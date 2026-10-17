package io.github.russian03.touchgrass.service

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.service.ProtectionGrace.STOPPED_GRACE_MS
import org.junit.Test

class ProtectionGraceTest {
    private val since = 1_000_000L

    @Test
    fun `un corte recién empezado se enseña como si funcionara`() {
        assertThat(ProtectionGrace.shown(ProtectionState.Stopped, since, since)).isEqualTo(ProtectionState.Running)
        assertThat(ProtectionGrace.shown(ProtectionState.Stopped, since, since + STOPPED_GRACE_MS - 1_000))
            .isEqualTo(ProtectionState.Running)
    }

    @Test
    fun `un corte que pasa del margen se enseña detenido`() {
        assertThat(ProtectionGrace.shown(ProtectionState.Stopped, since, since + STOPPED_GRACE_MS + 1_000))
            .isEqualTo(ProtectionState.Stopped)
    }

    @Test
    fun `quitarlo en Accesibilidad se enseña al momento`() {
        assertThat(ProtectionGrace.shown(ProtectionState.Off, since, since)).isEqualTo(ProtectionState.Off)
        assertThat(ProtectionGrace.shown(ProtectionState.Off, null, since)).isEqualTo(ProtectionState.Off)
    }

    @Test
    fun `en marcha se enseña en marcha`() {
        assertThat(ProtectionGrace.shown(ProtectionState.Running, null, since)).isEqualTo(ProtectionState.Running)
    }
}
