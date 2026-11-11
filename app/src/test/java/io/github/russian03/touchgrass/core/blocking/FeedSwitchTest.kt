package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.blocking.FeedSwitch.Step
import org.junit.Test

/** «Ir a Siguiendo» en dos pasos: pulsar el selector y luego «Siguiendo» en el menú (Instagram 454, caso S1). */
class FeedSwitchTest {

    private val feedSwitch = FeedSwitch()

    @Test
    fun `el primer paso pulsa el selector`() {
        feedSwitch.start(nowMs = 0)

        assertThat(feedSwitch.next(nowMs = 0, menuShown = false)).isEqualTo(Step.OpenMenu)
    }

    @Test
    fun `con el menu abierto el segundo paso pulsa Siguiendo`() {
        feedSwitch.start(nowMs = 0)
        feedSwitch.next(nowMs = 0, menuShown = false)

        assertThat(feedSwitch.next(nowMs = 300, menuShown = true)).isEqualTo(Step.PickFollowing)
    }

    @Test
    fun `mientras el menu no aparece espera sin repetir el primer paso`() {
        feedSwitch.start(nowMs = 0)
        feedSwitch.next(nowMs = 0, menuShown = false)

        assertThat(feedSwitch.next(nowMs = 200, menuShown = false)).isEqualTo(Step.None)
        assertThat(feedSwitch.next(nowMs = FeedSwitch.MENU_TIMEOUT_MS - 1, menuShown = false)).isEqualTo(Step.None)
    }

    @Test
    fun `si el menu no aparece a tiempo se rinde`() {
        feedSwitch.start(nowMs = 0)
        feedSwitch.next(nowMs = 0, menuShown = false)

        assertThat(feedSwitch.next(nowMs = FeedSwitch.MENU_TIMEOUT_MS, menuShown = false)).isEqualTo(Step.None)
        assertThat(feedSwitch.gaveUp).isTrue()
    }

    @Test
    fun `tras rendirse no reintenta hasta empezar de nuevo`() {
        feedSwitch.start(nowMs = 0)
        feedSwitch.next(nowMs = 0, menuShown = false)
        feedSwitch.next(nowMs = FeedSwitch.MENU_TIMEOUT_MS, menuShown = false)

        assertThat(feedSwitch.next(nowMs = 5_000, menuShown = false)).isEqualTo(Step.None)
        assertThat(feedSwitch.next(nowMs = 5_100, menuShown = true)).isEqualTo(Step.None)

        feedSwitch.start(nowMs = 6_000)
        assertThat(feedSwitch.gaveUp).isFalse()
        assertThat(feedSwitch.next(nowMs = 6_000, menuShown = false)).isEqualTo(Step.OpenMenu)
    }

    @Test
    fun `tras elegir Siguiendo no se repite`() {
        feedSwitch.start(nowMs = 0)
        feedSwitch.next(nowMs = 0, menuShown = false)
        feedSwitch.next(nowMs = 300, menuShown = true)

        assertThat(feedSwitch.next(nowMs = 400, menuShown = true)).isEqualTo(Step.None)
        assertThat(feedSwitch.next(nowMs = 500, menuShown = false)).isEqualTo(Step.None)
    }

    @Test
    fun `un menu que abrio el usuario no se toca`() {
        // Sin start() no hay una salida en marcha: el menú de Instagram es del usuario.
        assertThat(feedSwitch.next(nowMs = 0, menuShown = true)).isEqualTo(Step.None)
    }

    @Test
    fun `esta activa desde que empieza hasta que elige o se rinde`() {
        assertThat(feedSwitch.isActive).isFalse()
        feedSwitch.start(nowMs = 0)
        assertThat(feedSwitch.isActive).isTrue()
        feedSwitch.next(nowMs = 0, menuShown = false)
        assertThat(feedSwitch.isActive).isTrue()
        feedSwitch.next(nowMs = 300, menuShown = true)
        assertThat(feedSwitch.isActive).isFalse()
    }
}
