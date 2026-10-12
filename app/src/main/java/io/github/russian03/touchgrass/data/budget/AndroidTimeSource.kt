package io.github.russian03.touchgrass.data.budget

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.russian03.touchgrass.core.budget.TimeSnapshot
import io.github.russian03.touchgrass.core.budget.TimeSource
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTimeSource @Inject constructor(@param:ApplicationContext private val context: Context) : TimeSource {

    override fun now(): TimeSnapshot {
        val resolver = context.contentResolver
        return TimeSnapshot(
            wallMs = System.currentTimeMillis(),
            elapsedMs = SystemClock.elapsedRealtime(),
            bootCount = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1),
            zone = ZoneId.systemDefault(),
            // Las dos: con la zona manual también se podría adelantar el reloj unas horas.
            autoTime =
            Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 0) == 1 &&
                Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1,
        )
    }
}
