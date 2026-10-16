package io.github.russian03.touchgrass.core.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Todo lo que se guarda para las estadísticas. Solo vive en el móvil, como el saldo.
 *
 * Los días son días lógicos (de 8:00 a 8:00) en ISO (`2026-10-13`) y los meses, `YearMonth`
 * (`2026-10`), igual que en el saldo.
 */
@Serializable
data class StatsState(
    /** Primer día con estadísticas: los días anteriores no cuentan. `null` si aún no hay. */
    val since: String? = null,
    /** Estadísticas de cada día con algo que contar. Un día que no está es un día sin nada. */
    val days: Map<String, DayStats> = emptyMap(),
    /** Último mes cuyo resumen se ha abierto: hasta entonces se avisa en Inicio. */
    val lastMonthSeen: String? = null,
    /** Último mes del que se ha enviado la notificación del resumen. */
    val lastMonthNotified: String? = null,
    /** Último día cuya cara ya se ha enseñado al abrir la app («reclamado»). */
    val lastDayClaimed: String? = null,
    /**
     * Día en que la protección dejó de funcionar, mientras siga sin funcionar. Al volver se
     * marcan todos esos días con [DayStats.protectionOff] y se borra.
     */
    val protectionOffSince: String? = null,
)

@Serializable
data class DayStats(
    /** Límite diario que había al pedir tiempo ese día; `null` si no se pidió. */
    val limitMinutes: Int? = null,
    val unlocks: List<UnlockRecord> = emptyList(),
    /** Veces que ha saltado la pantalla de bloqueo, por app. */
    val blocks: Map<String, Int> = emptyMap(),
    /** Veces que el usuario se ha ido con «Volver atrás», por app. */
    val leaves: Map<String, Int> = emptyMap(),
    /** La protección estuvo desactivada en algún momento del día. */
    val protectionOff: Boolean = false,
)

/** Un desbloqueo pedido: [minutes] restados al pedirlo, de los que se devolvieron [refundedMinutes]. */
@Serializable
data class UnlockRecord(
    val packageName: String,
    val minutes: Int,
    /** Inicio del desbloqueo (reloj de pared); también sirve para encontrarlo al terminarlo. */
    val startWallMs: Long,
    val reason: UnlockReason,
    /** Lo que escribió el usuario con [UnlockReason.Other]. */
    val note: String? = null,
    val refundedMinutes: Int = 0,
) {
    /** Minutos que de verdad se gastaron. */
    val netMinutes: Int get() = (minutes - refundedMinutes).coerceAtLeast(0)
}

/** Para qué se pide tiempo. */
@Serializable
enum class UnlockReason {
    @SerialName("bath")
    Bath,

    @SerialName("eat")
    Eat,

    @SerialName("bus")
    Bus,

    @SerialName("hobby")
    Hobby,

    @SerialName("sleep")
    Sleep,

    @SerialName("other")
    Other,
}
