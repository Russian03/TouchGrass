package io.github.russian03.touchgrass.core.blocking

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.snapshot.Bounds
import io.github.russian03.touchgrass.core.snapshot.ListRow
import org.junit.Test

/**
 * Caso R2 de la captura (Instagram 454.0.0.54.77, Android 14, pantalla 1220x2712): un post son varias
 * filas de la lista (cabecera, media, separador); solo la cabecera lleva `header`, y la de un sugerido
 * lleva además `marked` (`inline_follow_button`).
 */
class PostMaskTest {

    private val list = Bounds(0, 118, 1220, 2504)

    private fun row(top: Int, bottom: Int, header: Boolean = false, marked: Boolean = false) =
        ListRow(Bounds(0, top, 1220, bottom), header, marked)

    @Test
    fun `un post sugerido da una zona con sus limites`() {
        val rows = listOf(row(300, 800, header = true, marked = true), row(800, 1300))

        assertThat(PostMask.zones(rows, list)).containsExactly(Bounds(0, 300, 1220, 1300))
    }

    @Test
    fun `un post de seguido no da zona`() {
        val rows = listOf(row(300, 469, header = true), row(469, 1300))

        assertThat(PostMask.zones(rows, list)).isEmpty()
    }

    @Test
    fun `las filas de un mismo post sugerido se juntan en una zona`() {
        val rows =
            listOf(
                row(300, 600, header = true, marked = true),
                row(600, 1100),
                row(1100, 1139),
                row(1139, 1308, header = true),
            )

        assertThat(PostMask.zones(rows, list)).containsExactly(Bounds(0, 300, 1220, 1139))
    }

    @Test
    fun `la zona se recorta a la lista y no tapa las pestañas ni la barra de arriba`() {
        val rows = listOf(row(60, 900, header = true, marked = true), row(900, 2600))

        assertThat(PostMask.zones(rows, list)).containsExactly(Bounds(0, 118, 1220, 2504))
    }

    @Test
    fun `dos sugeridos separados por un seguido dan dos zonas`() {
        val rows =
            listOf(
                row(118, 500, header = true, marked = true),
                row(500, 900, header = true),
                row(900, 1300),
                row(1300, 1700, header = true, marked = true),
            )

        assertThat(PostMask.zones(rows, list))
            .containsExactly(Bounds(0, 118, 1220, 500), Bounds(0, 1300, 1220, 1700))
            .inOrder()
    }

    @Test
    fun `las filas sobre la primera cabecera se tapan si el post de arriba era sugerido`() {
        val rows = listOf(row(118, 480), row(480, 649, header = true), row(649, 2174))

        assertThat(PostMask.zones(rows, list, leadingMarked = true)).containsExactly(Bounds(0, 118, 1220, 480))
    }

    @Test
    fun `las filas sobre la primera cabecera no se tapan si el post de arriba no era sugerido`() {
        val rows = listOf(row(118, 480), row(480, 649, header = true), row(649, 2174))

        assertThat(PostMask.zones(rows, list, leadingMarked = false)).isEmpty()
    }

    @Test
    fun `sin filas o sin lista no hay zonas`() {
        val rows = listOf(row(300, 800, header = true, marked = true))

        assertThat(PostMask.zones(emptyList(), list)).isEmpty()
        assertThat(PostMask.zones(rows, null)).isEmpty()
    }

    @Test
    fun `una fila fuera de la lista no da zona`() {
        val rows = listOf(row(2600, 2700, header = true, marked = true))

        assertThat(PostMask.zones(rows, list)).isEmpty()
    }

    @Test
    fun `como mucho MAX_ZONES zonas`() {
        val rows = (0 until 5).flatMap { i ->
            listOf(
                row(200 * i, 200 * i + 100, true, true),
                row(
                    200 * i + 100,
                    200 * i + 200,
                    true,
                ),
            )
        }

        assertThat(PostMask.zones(rows, Bounds(0, 0, 1220, 2000))).hasSize(PostMask.MAX_ZONES)
        assertThat(PostMask.MAX_ZONES).isEqualTo(3)
    }
}
