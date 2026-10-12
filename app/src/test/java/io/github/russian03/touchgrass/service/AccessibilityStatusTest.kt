package io.github.russian03.touchgrass.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityStatusTest {

    @Test
    fun `sin activar en Accesibilidad la protección está apagada`() {
        assertThat(AccessibilityStatus.stateOf(enabledInSettings = false, running = false))
            .isEqualTo(ProtectionState.Off)
    }

    @Test
    fun `activada y en marcha la protección funciona`() {
        assertThat(AccessibilityStatus.stateOf(enabledInSettings = true, running = true))
            .isEqualTo(ProtectionState.Running)
    }

    @Test
    fun `activada pero sin servicio en marcha la protección está detenida`() {
        // Lo que pasa en HyperOS al limpiar Recientes: el ajuste sigue, el servicio no.
        assertThat(AccessibilityStatus.stateOf(enabledInSettings = true, running = false))
            .isEqualTo(ProtectionState.Stopped)
    }

    @Test
    fun `si el sistema dice que está en marcha se le cree aunque el ajuste no cuadre`() {
        assertThat(AccessibilityStatus.stateOf(enabledInSettings = false, running = true))
            .isEqualTo(ProtectionState.Running)
    }
}
