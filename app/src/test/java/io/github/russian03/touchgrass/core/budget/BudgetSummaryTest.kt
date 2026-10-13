package io.github.russian03.touchgrass.core.budget

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.testing.FakeTime
import org.junit.Test

class BudgetSummaryTest {

    @Test
    fun `el resumen ya tiene en cuenta el cambio de día`() {
        val state = BudgetState(limitMinutes = 30, day = "2026-10-12", usedMinutes = 30, onboardingDone = true)
        val summary = BudgetSummary.of(state, FakeTime("2026-10-13T08:30").now())
        assertThat(summary.remainingMinutes).isEqualTo(30)
        assertThat(summary.usedMinutes).isEqualTo(0)
    }

    @Test
    fun `lo gastado no pasa del límite si se ha bajado`() {
        val state = BudgetState(limitMinutes = 10, day = "2026-10-13", usedMinutes = 25, onboardingDone = true)
        val summary = BudgetSummary.of(state, FakeTime("2026-10-13T12:00").now())
        assertThat(summary.usedMinutes).isEqualTo(10)
        assertThat(summary.remainingMinutes).isEqualTo(0)
    }

    @Test
    fun `incluye el desbloqueo en marcha y lo que le queda`() {
        val time = FakeTime("2026-10-13T12:00")
        val state = BudgetState(limitMinutes = 30, onboardingDone = true)
        val unlocked = (BudgetPolicy.requestUnlock(state, "app", 20, setOf("app"), time.now()) as BudgetResult.Ok).state
        val summary = BudgetSummary.of(unlocked, time.now())
        assertThat(summary.unlock?.packageName).isEqualTo("app")
        assertThat(summary.unlockRemainingMs).isEqualTo(20 * 60_000L)
    }
}
