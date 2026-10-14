package io.github.russian03.touchgrass.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.russian03.touchgrass.MainActivity
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.ui.common.monthName
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Aviso del resumen mensual: el día 1 a las 8:00, cuando empieza el primer día lógico del mes,
 * una notificación dice que ya están las estadísticas del mes anterior.
 *
 * La alarma no es exacta (no hace falta ningún permiso especial) y se pierde al reiniciar, así
 * que [schedule] se llama cada vez que arranca el servicio, que vuelve a arrancar tras reiniciar.
 * Además comprueba al momento si hay un aviso pendiente, por si la alarma no llegó a sonar.
 */
object MonthlyReport {

    private const val CHANNEL_ID = "monthly_report"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_ALARM = 1
    private const val REQUEST_OPEN = 2

    /** Programa la alarma del próximo día 1 y comprueba ya si hay un aviso pendiente. */
    fun schedule(context: Context) {
        scheduleAlarm(context)
        context.sendBroadcast(checkIntent(context))
    }

    fun scheduleAlarm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMs(), alarmIntent(context))
    }

    /** Próximo día 1 a las 8:00 (hora local). */
    fun nextTriggerMs(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val logicalDay = if (local.toLocalTime() <
            BudgetPolicy.DAY_START
        ) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
        return YearMonth.from(logicalDay).plusMonths(1).atDay(1).atTime(BudgetPolicy.DAY_START)
            .atZone(zone).toInstant().toEpochMilli()
    }

    /** Si se pueden enviar notificaciones (en Android 13 o superior hace falta el permiso). */
    fun canNotify(context: Context): Boolean {
        val permission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Envía el aviso de que ya está el resumen de [month]. Devuelve si se ha podido. */
    fun notify(context: Context, month: YearMonth): Boolean {
        if (!canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_monthly),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val open =
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_STATS, month.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stats)
                .setContentTitle(context.getString(R.string.notification_monthly_title, monthName(month)))
                .setContentText(context.getString(R.string.notification_monthly_body))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        REQUEST_OPEN,
                        open,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setAutoCancel(true)
                .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun checkIntent(context: Context) = Intent(context, MonthlyReportReceiver::class.java)

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        checkIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
