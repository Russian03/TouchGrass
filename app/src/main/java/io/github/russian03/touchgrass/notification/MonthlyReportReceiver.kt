package io.github.russian03.touchgrass.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.data.stats.StatsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Salta con la alarma del día 1 (y cada vez que arranca el servicio): si el resumen del mes
 * anterior está listo y aún no se ha avisado, envía la notificación y programa la siguiente.
 */
@AndroidEntryPoint
class MonthlyReportReceiver : BroadcastReceiver() {

    @Inject lateinit var stats: StatsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val month = StatsPolicy.pendingNotification(stats.state.first(), stats.today())
                if (month != null) {
                    MonthlyReport.notify(context, month)
                    // Aunque no se pueda notificar, no se insiste: el resumen también sale en Inicio.
                    stats.markNotified(month)
                }
                MonthlyReport.scheduleAlarm(context)
            } finally {
                pending.finish()
            }
        }
    }
}
