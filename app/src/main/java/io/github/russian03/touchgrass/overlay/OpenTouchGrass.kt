package io.github.russian03.touchgrass.overlay

import android.content.Context
import android.content.Intent
import io.github.russian03.touchgrass.MainActivity

/**
 * Abre TouchGrass en Inicio: desde ahí se pide tiempo y, la primera vez del día, se ve la
 * celebración del día anterior.
 */
internal fun openTouchGrass(context: Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_HOME, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
}
