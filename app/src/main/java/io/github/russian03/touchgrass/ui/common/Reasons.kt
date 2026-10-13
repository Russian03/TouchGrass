package io.github.russian03.touchgrass.ui.common

import androidx.annotation.StringRes
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.stats.UnlockReason

/** Nombre de cada motivo tal como se muestra. */
@StringRes
fun reasonLabel(reason: UnlockReason): Int = when (reason) {
    UnlockReason.Bath -> R.string.reason_bath
    UnlockReason.Eat -> R.string.reason_eat
    UnlockReason.Bus -> R.string.reason_bus
    UnlockReason.Hobby -> R.string.reason_hobby
    UnlockReason.Sleep -> R.string.reason_sleep
    UnlockReason.Other -> R.string.reason_other
}
