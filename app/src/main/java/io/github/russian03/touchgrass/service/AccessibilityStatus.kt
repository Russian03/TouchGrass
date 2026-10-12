package io.github.russian03.touchgrass.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Estado real de la protección, no solo lo que dice el ajuste de accesibilidad. */
enum class ProtectionState {
    /** El usuario no ha activado TouchGrass en Accesibilidad. */
    Off,

    /** Activada y el sistema tiene el servicio en marcha. */
    Running,

    /**
     * Activada en el ajuste, pero el sistema no tiene el servicio en marcha: p. ej. porque
     * HyperOS/MIUI mató su proceso al limpiar Recientes. Android no lo vuelve a arrancar
     * solo; hay que desactivarlo y activarlo de nuevo.
     */
    Stopped,
}

object AccessibilityStatus {

    fun state(context: Context): ProtectionState {
        val expected = ComponentName(context, TouchGrassAccessibilityService::class.java)
        return stateOf(
            enabledInSettings = isEnabledInSettings(context, expected),
            running = isRunning(context, expected),
        )
    }

    internal fun stateOf(enabledInSettings: Boolean, running: Boolean): ProtectionState = when {
        running -> ProtectionState.Running
        enabledInSettings -> ProtectionState.Stopped
        else -> ProtectionState.Off
    }

    private fun isEnabledInSettings(context: Context, expected: ComponentName): Boolean {
        val enabled =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /** El sistema solo devuelve aquí los servicios que tiene vinculados y funcionando. */
    private fun isRunning(context: Context, expected: ComponentName): Boolean =
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info.resolveInfo.serviceInfo.let { ComponentName(it.packageName, it.name) } == expected }
}
