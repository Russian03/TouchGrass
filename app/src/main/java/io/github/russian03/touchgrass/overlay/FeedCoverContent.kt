package io.github.russian03.touchgrass.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.russian03.touchgrass.R
import io.github.russian03.touchgrass.ui.design.ChunkyPillButton
import io.github.russian03.touchgrass.ui.design.MixedTitle
import io.github.russian03.touchgrass.ui.design.Sprout
import io.github.russian03.touchgrass.ui.design.SproutMood
import io.github.russian03.touchgrass.ui.theme.ChunkyNeutral
import io.github.russian03.touchgrass.ui.theme.ChunkyNeutralEdge
import io.github.russian03.touchgrass.ui.theme.Ink
import io.github.russian03.touchgrass.ui.theme.InkEdge
import io.github.russian03.touchgrass.ui.theme.InkSoft
import io.github.russian03.touchgrass.ui.theme.Lime
import io.github.russian03.touchgrass.ui.theme.TouchGrassTheme

/**
 * Contenido de la capa de Inicio: lima opaca, el logo negando con la cabeza, un título y dos botones:
 * «Ir a Siguiendo» (el principal, sin esperas) y el enlace discreto «Pedir tiempo»
 * y, también grande, «Salir de la app». Se traga los toques.
 */
@Composable
internal fun FeedCoverContent(onFollowing: () -> Unit, onAskTime: () -> Unit, onExit: () -> Unit) {
    // La ventana del servicio no tiene tema: los botones y el título lo necesitan.
    TouchGrassTheme(darkTheme = false) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Lime)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(top = 24.dp, bottom = 40.dp),
            ) {
                Sprout(
                    mood = SproutMood.Calm,
                    faceColor = Ink,
                    outline = true,
                    shaking = true,
                    modifier = Modifier.width(96.dp),
                )
                MixedTitle(
                    text = stringResource(R.string.feed_cover_title),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = Ink,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.feed_cover_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextLink(text = stringResource(R.string.feed_cover_ask_time), onClick = onAskTime)
                ChunkyPillButton(
                    text = stringResource(R.string.feed_cover_go_following),
                    onClick = onFollowing,
                    containerColor = Ink,
                    edgeColor = InkEdge,
                    contentColor = Lime,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ChunkyPillButton(
                    text = stringResource(R.string.feed_cover_exit),
                    onClick = onExit,
                    containerColor = ChunkyNeutral,
                    edgeColor = ChunkyNeutralEdge,
                    contentColor = Ink,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Preview(widthDp = 411, heightDp = 640)
@Composable
private fun FeedCoverContentPreview() {
    FeedCoverContent(onFollowing = {}, onAskTime = {}, onExit = {})
}
