package io.github.russian03.touchgrass.service

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.service.ShowConfirmation.Companion.CONFIRM_MS
import org.junit.Test

class ShowConfirmationTest {
    private val start = 1_000_000L
    private val confirmation = ShowConfirmation()

    @Test
    fun `la primera vez que se cumple no se confirma`() {
        assertThat(confirmation.onMatch("reels", start)).isFalse()
        assertThat(confirmation.remainingMs(start)).isEqualTo(CONFIRM_MS)
    }

    @Test
    fun `se confirma si sigue cumpliéndose pasado el tiempo`() {
        confirmation.onMatch("reels", start)
        assertThat(confirmation.onMatch("reels", start + CONFIRM_MS - 1)).isFalse()
        assertThat(confirmation.onMatch("reels", start + CONFIRM_MS)).isTrue()
        assertThat(confirmation.remainingMs(start + CONFIRM_MS + 50)).isEqualTo(0)
    }

    @Test
    fun `si cambia la regla vuelve a empezar`() {
        confirmation.onMatch("reels_tab", start)
        assertThat(confirmation.onMatch("reels_viewer", start + CONFIRM_MS)).isFalse()
        assertThat(confirmation.onMatch("reels_viewer", start + 2 * CONFIRM_MS)).isTrue()
    }

    @Test
    fun `si deja de cumplirse se olvida`() {
        confirmation.onMatch("reels", start)
        confirmation.reset()
        assertThat(confirmation.onMatch("reels", start + CONFIRM_MS)).isFalse()
    }
}
