package io.github.russian03.touchgrass.core.stats

import java.time.LocalDate
import java.time.YearMonth

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

    fun markSeen(state: StatsState, month: YearMonth): StatsState =
        if (state.lastMonthSeen?.let(YearMonth::parse)?.let { it >= month } == true) {
            state
        } else {
            state.copy(lastMonthSeen = month.toString())
        }

    fun markNotified(state: StatsState, month: YearMonth): StatsState = state.copy(lastMonthNotified = month.toString())

    /**
     * Mes cuyo resumen ya está listo y aún no se ha abierto: el anterior a [today], si hay datos
     * de él. El día 1 a las 8:00 (cuando empieza el día lógico) el mes anterior ya está completo.
     */
    fun pendingSummary(state: StatsState, today: LocalDate): YearMonth? {
        val previous = YearMonth.from(today).minusMonths(1)
        val seen = state.lastMonthSeen?.let(YearMonth::parse)
        if (seen != null && seen >= previous) return null
        return previous.takeIf { trackedDays(state, it, today) > 0 }
    }

    /** Mes del que toca enviar la notificación del resumen, o `null` si no toca. */
    fun pendingNotification(state: StatsState, today: LocalDate): YearMonth? {
        val previous = YearMonth.from(today).minusMonths(1)
        val notified = state.lastMonthNotified?.let(YearMonth::parse)
        if (notified != null && notified >= previous) return null
        return previous.takeIf { trackedDays(state, it, today) > 0 }
    }

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
        )
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

    /** Días que se ven en «Mi semana» de Inicio. */
    const val WEEK_DAYS = 7

    /**
     * Los últimos [WEEK_DAYS] días hasta [today], del más antiguo al más reciente, con lo que se
     * hizo en cada uno. Los días anteriores a [StatsState.since] no cuentan ([DayMark.Untracked]).
     */
    fun week(state: StatsState, today: LocalDate): List<WeekDay> {
        val since = state.since?.let(LocalDate::parse)
        return (WEEK_DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val row = DayRow(day, state.days[day.toString()] ?: DayStats())
            val mark =
                when {
                    since == null || day < since -> DayMark.Untracked
                    row.exhausted -> DayMark.Exhausted
                    row.stats.unlocks.isNotEmpty() -> DayMark.Unlocked
                    else -> DayMark.Free
                }
            WeekDay(day, mark, row.unlockedMinutes)
        }
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

/** Un día de «Mi semana»: qué pasó y cuántos minutos se desbloquearon. */
data class WeekDay(val day: LocalDate, val mark: DayMark, val unlockedMinutes: Int)

enum class DayMark {
    /** Aún no había estadísticas. */
    Untracked,

    /** Sin pedir tiempo: el día que se celebra. */
    Free,

    /** Se pidió tiempo sin agotar el saldo. */
    Unlocked,

    /** Se gastó todo el saldo. */
    Exhausted,
}

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
) {
    /** Días sin agotar el saldo. */
    val daysWithinLimit: Int get() = trackedDays - daysExhausted

    /** Minutos desbloqueados de media al día, o 0 sin días. */
    val averageMinutes: Double get() = if (trackedDays == 0) 0.0 else unlockedMinutes.toDouble() / trackedDays
}
