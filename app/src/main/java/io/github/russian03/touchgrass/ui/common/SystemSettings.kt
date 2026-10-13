package io.github.russian03.touchgrass.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Pantallas de Ajustes del sistema que la app abre. Solo se abren; no se cambia nada. */
object SystemSettings {

    /** En Xiaomi/Redmi/POCO el sistema cierra apps en segundo plano con mucha facilidad. */
    val isXiaomi: Boolean get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun openAccessibility(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppDetails(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    fun openDateTime(context: Context) = open(context, Intent(Settings.ACTION_DATE_SETTINGS))

    private fun open(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
