package io.github.russian03.touchgrass.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileTest {

    @Test
    fun `el nombre se limpia antes de guardarlo`() {
        assertThat(Profile.cleanName("  Jan  ")).isEqualTo("Jan")
        assertThat(Profile.cleanName("Ana   María")).isEqualTo("Ana María")
        assertThat(Profile.cleanName("   ")).isNull()
        assertThat(Profile.cleanName("*Jan*")).isEqualTo("Jan")
        assertThat(Profile.cleanName("x".repeat(40))).hasLength(Profile.NAME_MAX_LENGTH)
    }
}
