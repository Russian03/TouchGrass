package io.github.russian03.touchgrass.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {

    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TouchGrassAccessibilityService::class.java)
        val enabled =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
