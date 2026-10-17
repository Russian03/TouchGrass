package io.github.russian03.touchgrass.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Cómo se apuntan y se resumen las estadísticas, sin nada de Android para probarlo con JUnit.
 * Todas las funciones reciben el día lógico ([LocalDate]) que les toca: las calcula quien
 * llama con `BudgetPolicy.logicalDay`.
 */
object StatsPolicy {

    /** Meses que se guardan; lo anterior se borra al escribir. */
    const val KEEP_MONTHS = 24L

    /** Meses que se comparan en el resumen, contando el que se mira. */
    const val COMPARE_MONTHS = 6

    /** Máximo de caracteres de la nota de «Otro». */
    const val NOTE_MAX_LENGTH = 60

    /** Empieza a contar días a partir de [today], si aún no se había empezado. */
    fun start(state: StatsState, today: LocalDate): StatsState =
        if (state.since != null) state else state.copy(since = today.toString())

    fun recordUnlock(state: StatsState, day: LocalDate, record: UnlockRecord, limitMinutes: Int): StatsState {
        val clean = record.copy(note = record.note?.trim()?.take(NOTE_MAX_LENGTH)?.takeIf { it.isNotEmpty() })
        return updateDay(state, day) { it.copy(limitMinutes = limitMinutes, unlocks = it.unlocks + clean) }
    }

    /** Apunta los minutos devueltos al terminar antes el desbloqueo que empezó en [startWallMs]. */
    fun recordRefund(state: StatsState, day: LocalDate, startWallMs: Long, refundedMinutes: Int): StatsState {
        val stats = state.days[day.toString()] ?: return state
        if (stats.unlocks.none { it.startWallMs == startWallMs }) return state
        return updateDay(state, day) { current ->
            current.copy(
                unlocks =
                current.unlocks.map {
                    if (it.startWallMs ==
                        startWallMs
                    ) {
                        it.copy(refundedMinutes = refundedMinutes.coerceIn(0, it.minutes))
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun recordBlock(state: StatsState, day: LocalDate, packageName: String): StatsState =
        updateDay(state, day) { it.copy(blocks = it.blocks.increment(packageName)) }

    fun recordLeave(state: StatsState, day: LocalDate, packageName: String): StatsState =
        updateDay(state, day) { it.copy(leaves = it.leaves.increment(packageName)) }

    /** Meses con estadísticas, del primero a [today]. */
    fun months(state: StatsState, today: LocalDate): List<YearMonth> {
        val since = state.since?.let(LocalDate::parse) ?: return listOf(YearMonth.from(today))
        val first = YearMonth.from(minOf(since, today))
        val last = YearMonth.from(today)
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
    }

    /** Días de [month] que cuentan: desde [StatsState.since] hasta [today], ambos incluidos. */
    fun trackedDays(state: StatsState, month: YearMonth, today: LocalDate): Int {
        val since = state.since?.let(LocalDate::parse) ?: return 0
        val from = maxOf(month.atDay(1), since)
        val to = minOf(month.atEndOfMonth(), today)
        return if (from > to) 0 else (to.toEpochDay() - from.toEpochDay() + 1).toInt()
    }

    /** Resumen de [month] tal como está a [today] (si es el mes en curso, hasta hoy). */
    fun month(state: StatsState, month: YearMonth, today: LocalDate): MonthStats {
        val tracked = trackedDays(state, month, today)
        val since = state.since?.let(LocalDate::parse)
        val days =
            if (since == null || tracked == 0) {
                emptyList()
            } else {
                val from = maxOf(month.atDay(1), since)
                val to = minOf(month.atEndOfMonth(), today)
                generateSequence(to) { it.minusDays(1) }
                    .takeWhile { it >= from }
                    .map { day -> DayRow(day, state.days[day.toString()] ?: DayStats()) }
                    .toList()
            }
        // Cara de cada día del mes: hasta hoy, la que tenga (o sin contar); los que vienen, nada.
        val calendar =
            generateSequence(month.atDay(1)) { it.plusDays(1) }
                .takeWhile { it <= month.atEndOfMonth() }
                .associateWith { day -> if (day > today) null else faceAt(state, day) }
        val all = days.map { it.stats }
        val unlocks = all.flatMap { it.unlocks }
        val apps = (unlocks.map { it.packageName } + all.flatMap { it.blocks.keys + it.leaves.keys }).toSortedSet()
        return MonthStats(
            month = month,
            trackedDays = tracked,
            unlockedMinutes = unlocks.sumOf { it.netMinutes },
            unlockCount = unlocks.size,
            blockCount = all.sumOf { it.blocks.values.sum() },
            leaveCount = all.sumOf { it.leaves.values.sum() },
            daysWithoutUnlock = days.count { it.stats.unlocks.isEmpty() },
            daysExhausted = days.count { it.exhausted },
            apps =
            apps.map { pkg ->
                AppMonth(
                    packageName = pkg,
                    unlockedMinutes = unlocks.filter { it.packageName == pkg }.sumOf { it.netMinutes },
                    unlockCount = unlocks.count { it.packageName == pkg },
                    blockCount = all.sumOf { it.blocks[pkg] ?: 0 },
                )
            }.sortedByDescending { it.unlockedMinutes },
            reasons = unlocks.groupingBy { it.reason }.eachCount(),
            days = days,
            calendar = calendar,
            faces = calendar.filterValues { it != null && it != DayFace.Untracked }
                .filterKeys { it < today }
                .values
                .filterNotNull()
                .groupingBy { it }
                .eachCount(),
            blocksByDay =
            generateSequence(month.atDay(1)) { it.plusDays(1) }
                .takeWhile { it <= minOf(month.atEndOfMonth(), today) }
                .map { day -> state.days[day.toString()]?.blocks?.values?.sum() ?: 0 }
                .toList(),
        )
    }

    /**
     * Racha actual: días seguidos de poco uso que acaban hoy. Hoy, con lo que lleva, cuenta
     * mientras siga siendo de poco uso; si ya no lo es, la racha se ha roto y es 0.
     */
    fun currentStreak(state: StatsState, today: LocalDate): Int = streakAt(state, today)

    /** La racha más larga desde [StatsState.since] hasta [today] (incluido). */
    fun bestStreak(state: StatsState, today: LocalDate): Int {
        val since = state.since?.let(LocalDate::parse) ?: return 0
        var best = 0
        var run = 0
        generateSequence(since) { it.plusDays(1) }.takeWhile { it <= today }.forEach { day ->
            run = if (DayFaces.isLow(rowAt(state, day))) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    /**
     * [month] y hasta [COMPARE_MONTHS] - 1 meses anteriores con datos, del más antiguo al más
     * reciente. Se comparan por la media diaria, porque el primer mes y el actual están a medias.
     */
    fun comparison(state: StatsState, month: YearMonth, today: LocalDate): List<MonthStats> =
        generateSequence(month) { it.minusMonths(1) }
            .take(COMPARE_MONTHS)
            .map { month(state, it, today) }
            .takeWhile { it.trackedDays > 0 }
            .toList()
            .reversed()

    /** Días de una semana. */
    const val WEEK_DAYS = 7

    /**
     * Fila de [day], vacía si ese día no tiene nada apuntado. Si la protección sigue sin funcionar
     * desde [StatsState.protectionOffSince], esos días ya cuentan como sin protección.
     */
    private fun rowAt(state: StatsState, day: LocalDate): DayRow {
        val stats = state.days[day.toString()] ?: DayStats()
        val offSince = state.protectionOffSince?.let(LocalDate::parse)
        val off = stats.protectionOff || (offSince != null && day >= offSince)
        return DayRow(day, if (off == stats.protectionOff) stats else stats.copy(protectionOff = true))
    }

    /**
     * Días seguidos de poco uso que terminan en [day] (incluido), sin contar los anteriores a
     * [StatsState.since]. Es 0 si [day] no fue de poco uso.
     */
    fun streakAt(state: StatsState, day: LocalDate): Int {
        val since = state.since?.let(LocalDate::parse) ?: return 0
        var streak = 0
        var current = day
        while (current >= since && DayFaces.isLow(rowAt(state, current))) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    /** Cara de [day], o [DayFace.Untracked] si aún no había estadísticas. */
    fun faceAt(state: StatsState, day: LocalDate): DayFace {
        val since = state.since?.let(LocalDate::parse)
        if (since == null || day < since) return DayFace.Untracked
        return DayFaces.face(rowAt(state, day), streakAt(state, day))
    }

    /** Semanas que se ven en «Mi semana»: la anterior y la actual. */
    const val WEEKS_SHOWN = 2

    /**
     * Las [WEEKS_SHOWN] últimas semanas hasta la de [today], de lunes a domingo, con la cara y la
     * racha de cada día. Hoy cuenta con lo que lleva el día; los días que aún no han llegado salen
     * sin contar.
     */
    fun weeks(state: StatsState, today: LocalDate): List<WeekDay> {
        val monday =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS_SHOWN - 1L)
        return (0 until WEEK_DAYS * WEEKS_SHOWN).map { offset ->
            val day = monday.plusDays(offset.toLong())
            if (day > today) {
                WeekDay(day, DayFace.Untracked, 0, 0, null)
            } else {
                val row = rowAt(state, day)
                WeekDay(day, faceAt(state, day), streakAt(state, day), row.unlockedMinutes, row.stats.limitMinutes)
            }
        }
    }

    /**
     * Días que aún no se han reclamado al abrir la app: desde el siguiente al último reclamado
     * hasta ayer, sin salir de «Mi semana» ni de los días con estadísticas. Si nunca se ha
     * reclamado nada, solo ayer.
     */
    fun unclaimedDays(state: StatsState, today: LocalDate): List<LocalDate> {
        val since = state.since?.let(LocalDate::parse) ?: return emptyList()
        val yesterday = today.minusDays(1)
        val firstShown =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS_SHOWN - 1L)
        val afterClaimed = state.lastDayClaimed?.let(LocalDate::parse)?.plusDays(1) ?: yesterday
        val from = maxOf(afterClaimed, since, firstShown)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= yesterday }.toList()
    }

    /** Guarda que ya se han enseñado las caras hasta [day]. */
    fun markClaimed(state: StatsState, day: LocalDate): StatsState =
        if (state.lastDayClaimed?.let(LocalDate::parse)?.let { it >= day } == true) {
            state
        } else {
            state.copy(lastDayClaimed = day.toString())
        }

    /**
     * La protección ha dejado de funcionar [today]: lo marca y apunta desde cuándo. Si aún no se
     * había empezado a contar (nunca ha funcionado), no hay nada que marcar.
     */
    fun markProtectionOff(state: StatsState, today: LocalDate): StatsState {
        if (state.since == null) return state
        val marked = updateDay(state, today) { it.copy(protectionOff = true) }
        return if (state.protectionOffSince != null) marked else marked.copy(protectionOffSince = today.toString())
    }

    /** La protección vuelve a funcionar [today]: marca todos los días que estuvo quitada. */
    fun markProtectionOn(state: StatsState, today: LocalDate): StatsState {
        val offSince = state.protectionOffSince?.let(LocalDate::parse) ?: return state
        val marked =
            generateSequence(minOf(offSince, today)) { it.plusDays(1) }
                .takeWhile { it <= today }
                .fold(state) { acc, day -> updateDay(acc, day) { it.copy(protectionOff = true) } }
        return marked.copy(protectionOffSince = null)
    }

    private fun updateDay(state: StatsState, day: LocalDate, change: (DayStats) -> DayStats): StatsState {
        val key = day.toString()
        val oldest = YearMonth.from(day).minusMonths(KEEP_MONTHS - 1).atDay(1)
        val days = (state.days + (key to change(state.days[key] ?: DayStats()))).filterKeys {
            LocalDate.parse(it) >=
                oldest
        }
        val since = state.since?.let(LocalDate::parse)?.let { maxOf(it, oldest) } ?: day
        return state.copy(since = since.toString(), days = days)
    }

    private fun Map<String, Int>.increment(key: String) = this + (key to (this[key] ?: 0) + 1)
}

data class DayRow(val day: LocalDate, val stats: DayStats) {
    val unlockedMinutes: Int get() = stats.unlocks.sumOf { it.netMinutes }
    val blockCount: Int get() = stats.blocks.values.sum()

    /** Se gastó todo el saldo del día (con un límite mayor que 0). */
    val exhausted: Boolean get() = stats.limitMinutes?.let { it > 0 && unlockedMinutes >= it } == true
}

/** Un día de «Mi semana»: su cara, su racha y los minutos desbloqueados de su límite. */
data class WeekDay(
    val day: LocalDate,
    val face: DayFace,
    val streak: Int,
    val unlockedMinutes: Int,
    val limitMinutes: Int?,
)

data class AppMonth(val packageName: String, val unlockedMinutes: Int, val unlockCount: Int, val blockCount: Int)

data class MonthStats(
    val month: YearMonth,
    /** Días del mes que cuentan (desde que hay estadísticas y hasta hoy). */
    val trackedDays: Int,
    val unlockedMinutes: Int,
    val unlockCount: Int,
    val blockCount: Int,
    val leaveCount: Int,
    val daysWithoutUnlock: Int,
    /** Días en que se gastó todo el saldo. */
    val daysExhausted: Int,
    val apps: List<AppMonth>,
    val reasons: Map<UnlockReason, Int>,
    /** Días del mes, del más reciente al más antiguo. */
    val days: List<DayRow>,
    /** Cara de cada día del mes, del 1 al último; `null` en los que aún no han llegado. */
    val calendar: Map<LocalDate, DayFace?> = emptyMap(),
    /** Cuántos días pasados del mes (sin hoy) tuvieron cada cara. */
    val faces: Map<DayFace, Int> = emptyMap(),
    /** Veces que saltó el bloqueo cada día, del 1 a hoy (o al último del mes). */
    val blocksByDay: List<Int> = emptyList(),
) {
    /** Días sin agotar el saldo. */
    val daysWithinLimit: Int get() = trackedDays - daysExhausted

    /** Minutos desbloqueados de media al día, o 0 sin días. */
    val averageMinutes: Double get() = if (trackedDays == 0) 0.0 else unlockedMinutes.toDouble() / trackedDays
}
