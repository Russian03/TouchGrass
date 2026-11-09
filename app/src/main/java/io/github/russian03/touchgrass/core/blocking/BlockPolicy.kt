package io.github.russian03.touchgrass.core.blocking

import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.budget.TimeSource
import io.github.russian03.touchgrass.core.navigation.ViewerSession
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot

/** Lo que el servicio debe hacer con la pantalla de bloqueo tras mirar la de debajo. */
sealed interface BlockDecision {
    /**
     * Ninguna regla bloquea, o la anula un desbloqueo de esta app: quitar la pantalla y olvidar
     * la espera. Con [unlockRemainingMs], lo que le queda al desbloqueo que la anula (para volver
     * a mirar cuando caduque); `null` si no había regla.
     */
    data class Free(val unlockRemainingMs: Long? = null) : BlockDecision

    /** Una regla bloquea, pero la pantalla ya se ve o se acaba de salir: no cambiar nada. */
    data object Keep : BlockDecision

    /** [rule] bloquea: ponerla tras confirmarla en dos miradas con [confirmationKey]. */
    data class Block(val rule: Rule, val confirmationKey: String) : BlockDecision

    /**
     * [rule] (con `silentBack`) casa: tras confirmarla en dos miradas con [confirmationKey], hacer
     * un atrás sin mostrar ninguna pausa ni contar nada en las estadísticas.
     */
    data class SilentBack(val rule: Rule, val confirmationKey: String) : BlockDecision
}

/** Cuándo se pone la pantalla de bloqueo sobre lo que se ve de una app vigilada. */
object BlockPolicy {
    /** Tras salir a Inicio o pulsar «Volver atrás», la app aún parece estar delante un momento. */
    const val LEAVE_COOLDOWN_MS = 1_000L

    /**
     * Lo que falta para que acabe el enfriamiento tras salir ([leftAtMs], en elapsedRealtime).
     * Sirve para volver a mirar justo entonces: tras «Volver atrás» con los comentarios abiertos,
     * el atrás solo los cierra y el reel sigue delante sin que la app mande ningún evento.
     */
    fun cooldownRemainingMs(nowMs: Long, leftAtMs: Long): Long = (leftAtMs + LEAVE_COOLDOWN_MS - nowMs).coerceAtLeast(0)

    /** Apunta en [session] el lugar y el visor que se ven en [snapshot] (pasos 5-6 del flujo). Única función con efecto. */
    fun track(engine: RuleEngine, session: ViewerSession, snapshot: ScreenSnapshot, nowMs: Long) {
        engine.place(snapshot)?.let(session::onPlace)
        if (engine.pagers(snapshot.packageName).any { snapshot.has(it) }) session.onViewerShown(nowMs)
    }

    /**
     * Qué hacer con [snapshot] (pasos 7-9). [budget] = último saldo leído (`null` = nada desbloqueado);
     * [time] solo se consulta si una regla casa y hay saldo. [nowMs]/[leftAtMs] en elapsedRealtime.
     * [held] = regla del último `Block`: si ahora solo se ve la hoja que la tapa (los comentarios),
     * se sigue bloqueando con ella; sin regla anterior la hoja queda libre (comentarios de Inicio).
     */
    fun decide(
        engine: RuleEngine,
        snapshot: ScreenSnapshot,
        origin: String?,
        scrolled: Boolean,
        budget: BudgetState?,
        time: TimeSource,
        overlayShowing: Boolean,
        nowMs: Long,
        leftAtMs: Long,
        held: Rule? = null,
    ): BlockDecision {
        val rule =
            engine.evaluate(snapshot, origin, scrolled)
                ?: held?.takeIf { engine.covers(snapshot, it) }
                ?: return BlockDecision.Free()
        // Con un desbloqueo en marcha se sigue la navegación, pero no se bloquea nada.
        unlockRemainingMs(budget, snapshot.packageName, time)?.let { return BlockDecision.Free(it) }
        val key = "${snapshot.packageName}/${rule.id}"
        // El atrás silencioso no pone pausa: solo espera si se acaba de salir.
        if (rule.silentBack) {
            return if (nowMs - leftAtMs < LEAVE_COOLDOWN_MS) BlockDecision.Keep else BlockDecision.SilentBack(rule, key)
        }
        if (overlayShowing || nowMs - leftAtMs < LEAVE_COOLDOWN_MS) return BlockDecision.Keep
        return BlockDecision.Block(rule, key)
    }

    /**
     * Si lo que se ve es solo la hoja que tapa [held] (los comentarios sobre el reel): no casa
     * ninguna regla por sí sola y la hoja cubre la retenida. Se calcula en cada mirada para que
     * «Volver atrás» no tenga que leer la pantalla otra vez antes de quitar la pausa.
     */
    fun isOverSheet(
        engine: RuleEngine,
        snapshot: ScreenSnapshot,
        origin: String?,
        scrolled: Boolean,
        held: Rule?,
    ): Boolean = held != null && engine.evaluate(snapshot, origin, scrolled) == null && engine.covers(snapshot, held)

    /** Lo que le queda al desbloqueo de [packageName] en [budget], o `null` si no tiene uno en marcha. */
    private fun unlockRemainingMs(budget: BudgetState?, packageName: String, time: TimeSource): Long? {
        val state = budget ?: return null
        val now = time.now()
        val unlock = BudgetPolicy.activeUnlock(state, now)?.takeIf { it.packageName == packageName } ?: return null
        return BudgetPolicy.unlockRemainingMs(unlock, now)
    }
}
