package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SilentBackGuardTest {

    private val guard = SilentBackGuard()

    @Test
    fun `el primer atras se permite`() {
        assertThat(guard.tryAcquire(nowMs = 0)).isTrue()
    }

    @Test
    fun `un segundo atras antes del enfriamiento se niega y dice cuanto falta`() {
        guard.tryAcquire(nowMs = 0)
        assertThat(guard.tryAcquire(nowMs = 400)).isFalse()
        assertThat(guard.remainingMs(nowMs = 400)).isEqualTo(SilentBackGuard.COOLDOWN_MS - 400)
    }

    @Test
    fun `pasado el enfriamiento se vuelve a permitir`() {
        guard.tryAcquire(nowMs = 0)
        assertThat(guard.tryAcquire(nowMs = SilentBackGuard.COOLDOWN_MS)).isTrue()
    }

    @Test
    fun `tras el maximo de atras seguidos no hay mas hasta que se libere`() {
        repeat(SilentBackGuard.MAX_IN_BURST) {
            assertThat(guard.tryAcquire(nowMs = it * SilentBackGuard.COOLDOWN_MS)).isTrue()
        }
        val later = SilentBackGuard.MAX_IN_BURST * SilentBackGuard.COOLDOWN_MS * 2
        assertThat(guard.tryAcquire(nowMs = later)).isFalse()
        assertThat(guard.isExhausted).isTrue()
    }

    @Test
    fun `al quedar libre la pantalla la racha empieza de cero`() {
        repeat(SilentBackGuard.MAX_IN_BURST) { guard.tryAcquire(nowMs = it * SilentBackGuard.COOLDOWN_MS) }
        guard.reset()
        assertThat(guard.isExhausted).isFalse()
        assertThat(guard.tryAcquire(nowMs = 10 * SilentBackGuard.COOLDOWN_MS)).isTrue()
    }

    @Test
    fun `sin ningun atras previo no hay que esperar`() {
        assertThat(guard.remainingMs(nowMs = 5)).isEqualTo(0)
    }
}
