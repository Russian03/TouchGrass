package io.github.russian03.touchgrass.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {

    @Test
    fun `la duración va en horas y minutos`() {
        assertThat(duration(0)).isEqualTo("0 min")
        assertThat(duration(45)).isEqualTo("45 min")
        assertThat(duration(60)).isEqualTo("1 h")
        assertThat(duration(90)).isEqualTo("1 h 30 min")
        assertThat(duration(245)).isEqualTo("4 h 5 min")
    }
}
