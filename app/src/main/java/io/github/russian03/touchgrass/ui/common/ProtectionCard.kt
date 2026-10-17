package io.github.russian03.touchgrass.ui.common

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.service.AccessibilityStatus
import io.github.russian03.touchgrass.service.ProtectionGrace
import io.github.russian03.touchgrass.service.ProtectionState
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.PulsingDot
import io.github.russian03.touchgrass.ui.design.StatusRow
import io.github.russian03.touchgrass.ui.theme.CardBeigeEdge
import io.github.russian03.touchgrass.ui.theme.LimeDeep
import kotlinx.coroutines.delay

/** Cada cuánto se vuelve a mirar el estado mientras la protección no funciona. */
private const val RECHECK_MS = 2_000L

/**
 * Estado real de la protección. Se vuelve a comprobar al volver a la app, al momento cuando cambia
 * el ajuste de Accesibilidad (y, en Android 13+, cuando el sistema vincula o desvincula el
 * servicio) y, mientras no funciona, cada [RECHECK_MS]: así, cuando el sistema vuelve a poner en
 * marcha el servicio tras un corte, todo se arregla solo sin salir de la pantalla.
 */
@Composable
fun rememberProtectionState(): ProtectionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AccessibilityStatus.state(context)) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val refresh = { state = AccessibilityStatus.state(context) }
        refresh()
        resumed = true
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        val removeListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listenToServices(context, refresh) else null
        onPauseOrDispose {
            resumed = false
            context.contentResolver.unregisterContentObserver(observer)
            removeListener?.invoke()
        }
    }
    LaunchedEffect(state, resumed) {
        if (!resumed || state == ProtectionState.Running) return@LaunchedEffect
        while (true) {
            delay(RECHECK_MS)
            state = AccessibilityStatus.state(context)
        }
    }
    return state
}

/** Avisa con [onChange] cuando el sistema vincula o desvincula servicios; devuelve cómo dejar de escuchar. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun listenToServices(context: Context, onChange: () -> Unit): () -> Unit {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    val listener = AccessibilityManager.AccessibilityServicesStateChangeListener { onChange() }
    manager.addAccessibilityServicesStateChangeListener(context.mainExecutor, listener)
    return { manager.removeAccessibilityServicesStateChangeListener(listener) }
}

/**
 * Desde cuándo está detenido el servicio, mientras lo esté. Vive con el proceso de la app: al
 * cambiar de pantalla no vuelve a empezar el margen.
 */
private var stoppedSinceMs: Long? = null

/**
 * Lo que se enseña de [state]: un corte ([ProtectionState.Stopped]) se enseña como si funcionara
 * durante el margen de [ProtectionGrace]; si sigue pasado ese tiempo, ya se enseña detenido.
 */
@Composable
fun rememberShownProtection(state: ProtectionState): ProtectionState {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (state == ProtectionState.Stopped) {
        if (stoppedSinceMs == null) stoppedSinceMs = now
    } else {
        stoppedSinceMs = null
    }
    LaunchedEffect(state) {
        now = System.currentTimeMillis()
        val since = stoppedSinceMs ?: return@LaunchedEffect
        val left = ProtectionGrace.STOPPED_GRACE_MS - (now - since)
        if (left > 0) {
            delay(left)
            now = System.currentTimeMillis()
        }
    }
    return ProtectionGrace.shown(state, stoppedSinceMs, now)
}

/** Título, detalle y acción de cada estado de la protección. */
private data class ProtectionTexts(@StringRes val title: Int, @StringRes val detail: Int, @StringRes val action: Int)

private fun ProtectionState.texts() = when (this) {
    ProtectionState.Running ->
        ProtectionTexts(R.string.home_service_on, R.string.home_service_on_detail, R.string.home_service_settings)
    ProtectionState.Stopped ->
        ProtectionTexts(
            R.string.home_service_stopped,
            R.string.home_service_stopped_detail,
            R.string.home_service_restart,
        )
    ProtectionState.Off ->
        ProtectionTexts(R.string.home_service_off, R.string.home_service_off_detail, R.string.home_service_enable)
}

/** Verde cuando protege; rojo cuando no. */
@Composable
private fun ProtectionState.color(): Color =
    if (this == ProtectionState.Running) LimeDeep else MaterialTheme.colorScheme.error

/**
 * Tarjeta beige de Ajustes con el estado de la protección y el botón para ir a Accesibilidad (lima
 * si hay que arreglarla). Debajo, si hacen falta, el aviso de la hora manual ([manualTime]) y el
 * consejo de Xiaomi ([showXiaomiTip], solo con el servicio activado). [color] permite resaltarla.
 * Con [reconnecting], el servicio está en un corte que aún no ha pasado del margen: se dice que se
 * está reconectando, sin alarmar.
 */
@Composable
fun ProtectionCard(
    state: ProtectionState,
    manualTime: Boolean,
    showXiaomiTip: Boolean,
    actions: ProtectionActions,
    modifier: Modifier = Modifier,
    color: Color = beigeCardColor(),
    reconnecting: Boolean = false,
) {
    val texts =
        if (reconnecting) {
            ProtectionTexts(
                R.string.home_service_reconnecting,
                R.string.home_service_reconnecting_detail,
                R.string.home_service_settings,
            )
        } else {
            state.texts()
        }
    val xiaomi = showXiaomiTip && state != ProtectionState.Off
    BeigeCard(modifier = modifier, color = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(if (reconnecting) MaterialTheme.colorScheme.outline else state.color())
            Text(
                text = stringResource(texts.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            text = stringResource(texts.detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (state == ProtectionState.Running || reconnecting) {
            ChunkyPillButton(
                text = stringResource(texts.action),
                onClick = actions.onOpenAccessibilitySettings,
                containerColor = MaterialTheme.colorScheme.background,
                edgeColor = CardBeigeEdge,
                modifier = Modifier.padding(top = 20.dp),
            )
        } else {
            ChunkyPillButton(
                text = stringResource(texts.action),
                onClick = actions.onOpenAccessibilitySettings,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        if (manualTime || xiaomi) {
            Column(Modifier.padding(top = 12.dp)) {
                if (manualTime) {
                    StatusRow(
                        title = stringResource(R.string.manual_time_title),
                        detail = stringResource(R.string.manual_time_body),
                        divider = xiaomi,
                        leading = { PulsingDot(MaterialTheme.colorScheme.error) },
                        trailing = {
                            TextButton(onClick = actions.onOpenDateSettings) {
                                Text(stringResource(R.string.home_service_enable_short))
                            }
                        },
                    )
                }
                if (xiaomi) XiaomiTipRow(onOpenAppSettings = actions.onOpenAppSettings, divider = false)
            }
        }
    }
}

/** A dónde llevan los botones de la tarjeta de la protección. */
class ProtectionActions(
    val onOpenAccessibilitySettings: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenDateSettings: () -> Unit,
)

/** La protección como fila de lista: punto que late, estado y, si no protege, la acción para arreglarlo. */
@Composable
fun ProtectionRow(
    state: ProtectionState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    val texts = state.texts()
    StatusRow(
        title = stringResource(texts.title),
        detail = stringResource(texts.detail),
        divider = divider,
        leading = { PulsingDot(state.color()) },
        modifier = modifier,
        trailing = {
            if (state != ProtectionState.Running) {
                TextButton(onClick = onOpenAccessibilitySettings) {
                    Text(stringResource(R.string.home_service_enable_short))
                }
            }
        },
    )
}

/** Consejo para que HyperOS/MIUI no cierre la app. Solo abre la ficha de la app en Ajustes. */
@Composable
fun XiaomiTipRow(onOpenAppSettings: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true) {
    StatusRow(
        title = stringResource(R.string.home_xiaomi_tip_title),
        detail = stringResource(R.string.home_xiaomi_tip),
        divider = divider,
        modifier = modifier,
        leading = { PulsingDot(MaterialTheme.colorScheme.outline, size = 8.dp) },
        trailing = { TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.home_open_short)) } },
    )
}
