package io.github.russian03.touchgrass.core.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DayFaceTest {

    private fun row(minutes: Int, limit: Int = 40): DayRow {
        val unlocks = if (minutes == 0) emptyList() else listOf(UnlockRecord("pkg", minutes, 1L, UnlockReason.Bus))
        return DayRow(LocalDate.of(2026, 10, 1), DayStats(limitMinutes = limit, unlocks = unlocks))
    }

    @Test
    fun `cada tramo del límite tiene su cara`() {
        assertThat(DayFaces.face(row(0), streak = 1)).isEqualTo(DayFace.Free)
        assertThat(DayFaces.face(row(10), streak = 1)).isEqualTo(DayFace.Happy)
        assertThat(DayFaces.face(row(20), streak = 0)).isEqualTo(DayFace.Calm)
        assertThat(DayFaces.face(row(30), streak = 0)).isEqualTo(DayFace.Neutral)
        assertThat(DayFaces.face(row(39), streak = 0)).isEqualTo(DayFace.Tight)
        assertThat(DayFaces.face(row(40), streak = 0)).isEqualTo(DayFace.Sad)
    }

    @Test
    fun `la racha cambia la cara solo en días de poco uso`() {
        assertThat(DayFaces.face(row(0), streak = 3)).isEqualTo(DayFace.Cool)
        assertThat(DayFaces.face(row(10), streak = 7)).isEqualTo(DayFace.Legend)
        assertThat(DayFaces.face(row(0), streak = 14)).isEqualTo(DayFace.Mythic)
        assertThat(DayFaces.face(row(30), streak = 14)).isEqualTo(DayFace.Neutral)
    }

    @Test
    fun `poco uso es hasta el 25 por ciento sin agotar`() {
        assertThat(DayFaces.isLow(row(10))).isTrue()
        assertThat(DayFaces.isLow(row(11))).isFalse()
        assertThat(DayFaces.isLow(row(0, limit = 0))).isTrue()
        assertThat(DayFaces.isLow(row(5, limit = 5))).isFalse()
    }
}
