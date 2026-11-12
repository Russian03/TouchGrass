package io.github.russian03.touchgrass.data.rules

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.rules.PillSpec
import io.github.russian03.touchgrass.core.rules.RuleEngine
import io.github.russian03.touchgrass.core.snapshot.ScreenSnapshot
import io.github.russian03.touchgrass.core.snapshot.ViewNode
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
    fun `la hoja que tapa una zona no bloquea por si sola`() {
        // Capturas de zonas libres (Instagram 454, TikTok 47.7.3): ninguna hoja `coveredBy` casa sin la regla.
        val engines = listOf("instagram", "tiktok").map { RuleRepository.parse(File(rulesDir, "$it.json").readText()) }
        engines.forEach { set ->
            val engine = RuleEngine(listOf(set))
            val covering = set.rules.mapNotNull { it.coveredBy }
            assertThat(covering).isNotEmpty()
            covering.forEach { match ->
                val sheet = ScreenSnapshot(set.packageName, match.allOf.map { ViewNode(it.viewId) })
                assertThat(engine.evaluate(sheet)).isNull()
            }
        }
    }

    @Test
    fun `instagram bloquea la pestaña y el visor de Reels`() {
        val set = RuleRepository.parse(File(rulesDir, "instagram.json").readText())
        assertThat(set.packageName).isEqualTo("com.instagram.android")
        assertThat(
            set.rules.map {
                it.id
            },
        ).containsAtLeast("reels_tab", "reels_viewer", "explore_peek", "feed_cover")
    }

    @Test
    fun `tiktok bloquea Para ti y Comunidad`() {
        val set = RuleRepository.parse(File(rulesDir, "tiktok.json").readText())
        assertThat(set.packageName).isEqualTo("com.zhiliaoapp.musically")
        assertThat(set.rules.map { it.id }).containsAtLeast("for_you_tab", "community_tab")
    }

    @Test
    fun `solo la vista previa y el menu de Buscar hacen atras silencioso`() {
        val instagram = RuleRepository.parse(File(rulesDir, "instagram.json").readText())
        val tiktok = RuleRepository.parse(File(rulesDir, "tiktok.json").readText())
        val silent = instagram.rules.filter { it.silentBack }.map { it.id }
        assertThat(silent).containsExactly("explore_peek", "explore_menu")
        assertThat(tiktok.rules.filter { it.silentBack }).isEmpty()
    }

    @Test
    fun `una regla sin silentBack no hace atras silencioso`() {
        val json =
            """{"version":1,"packageName":"p","rules":[{"id":"r","description":"d",""" +
                """"match":{"allOf":[{"viewId":"v"}]},"action":{"type":"back"}}]}"""
        assertThat(RuleRepository.parse(json).rules.single().silentBack).isFalse()
    }

    @Test
    fun `una regla sin cover no tapa nada`() {
        val json =
            """{"version":1,"packageName":"p","rules":[{"id":"r","description":"d",""" +
                """"match":{"allOf":[{"viewId":"v"}]},"action":{"type":"back"}}]}"""
        assertThat(RuleRepository.parse(json).rules.single().cover).isNull()
    }

    @Test
    fun `solo feed_cover tapa la pantalla`() {
        val instagram = RuleRepository.parse(File(rulesDir, "instagram.json").readText())
        val tiktok = RuleRepository.parse(File(rulesDir, "tiktok.json").readText())
        assertThat(instagram.rules.filter { it.cover != null }.map { it.id }).containsExactly("feed_cover")
        assertThat(tiktok.rules.filter { it.cover != null }).isEmpty()
    }

    @Test
    fun `feed_cover mide la barra de navegacion como una pastilla de 8 por 4 dp`() {
        val rule = RuleRepository.parse(File(rulesDir, "instagram.json").readText()).rules.single {
            it.id ==
                "feed_cover"
        }
        assertThat(rule.cover?.pill).isEqualTo(PillSpec(horizontalDp = 8, verticalDp = 4))
    }

    @Test
    fun `feed_cover no usa su salida`() {
        val rule = RuleRepository.parse(File(rulesDir, "instagram.json").readText()).rules.single {
            it.id ==
                "feed_cover"
        }
        assertThat(rule.cover).isNotNull()
        assertThat(rule.silentBack).isFalse()
        assertThat(rule.coveredBy).isNull()
    }
}
