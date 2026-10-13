package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Comprueba que todos los JSON de reglas que se empaquetan en la app son válidos. */
class RuleFilesTest {

    private val rulesDir = File("src/main/assets/rules")

    @Test
    fun `todos los ficheros de reglas se parsean`() {
        val files = rulesDir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertThat(files).isNotEmpty()
        files.forEach { file ->
            val set = RuleRepository.parse(file.readText())
            assertThat(set.rules).isNotEmpty()
            // El nombre se muestra al pedir tiempo: no puede quedarse en el paquete.
            assertThat(set.name).isNotEqualTo(set.packageName)
            assertThat(set.blocks).isNotEmpty()
            assertThat(set.rules.map { it.id }).containsNoDuplicates()
            assertThat(set.places.map { it.id }).containsNoDuplicates()
            val placeIds = set.places.map { it.id }
            set.rules.flatMap { it.allowFrom }.forEach { assertThat(placeIds).contains(it.place) }
        }
    }

    @Test
    fun `instagram bloquea la pestaña y el visor de Reels`() {
        val set = RuleRepository.parse(File(rulesDir, "instagram.json").readText())
        assertThat(set.packageName).isEqualTo("com.instagram.android")
        assertThat(set.rules.map { it.id }).containsAtLeast("reels_tab", "reels_viewer")
    }
}
