package io.github.russian03.touchgrass.data.budget

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.budget.TimeSnapshot
import io.github.russian03.touchgrass.core.budget.TimeSource
import io.github.russian03.touchgrass.core.budget.UnlockSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BudgetRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    private val instagram = "com.instagram.android"
    private val madrid = ZoneId.of("Europe/Madrid")
    private var autoTime = true

    private fun now() = TimeSnapshot(
        wallMs = LocalDateTime.parse("2026-10-13T10:00").atZone(madrid).toInstant().toEpochMilli(),
        elapsedMs = 60_000_000,
        bootCount = 1,
        zone = madrid,
        autoTime = autoTime,
    )

    private fun TestScope.repository(file: File = File(folder.root, "budget.json")) = BudgetRepository(
        DataStoreFactory.create(
            serializer = BudgetSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { BudgetSerializer.defaultValue },
            scope = backgroundScope,
            produceFile = { file },
        ),
        TimeSource { now() },
    )

    @Test
    fun `primeros pasos y desbloqueo se guardan`() = runTest {
        val repo = repository()
        assertThat(repo.setInitialLimit(30)).isNull()
        assertThat(repo.completeOnboarding()).isNull()
        assertThat(repo.requestUnlock(instagram, 20, setOf(instagram))).isNull()

        val state = repo.state.first()
        assertThat(state.limitMinutes).isEqualTo(30)
        assertThat(state.onboardingDone).isTrue()
        assertThat(state.usedMinutes).isEqualTo(20)
        assertThat(BudgetPolicy.isUnlocked(state, instagram, now())).isTrue()
    }

    @Test
    fun `una petición denegada no cambia nada`() = runTest {
        val repo = repository()
        repo.setInitialLimit(30)
        autoTime = false
        assertThat(repo.requestUnlock(instagram, 10, setOf(instagram))).isEqualTo(Denial.ManualTime)
        assertThat(repo.state.first().usedMinutes).isEqualTo(0)
        assertThat(repo.state.first().unlock).isNull()
    }

    @Test
    fun `un fichero corrupto vuelve al estado que no desbloquea nada`() = runTest {
        val file = folder.newFile("corrupt.json").apply { writeText("{ esto no es json") }
        val state = repository(file).state.first()
        assertThat(state).isEqualTo(BudgetState())
        assertThat(BudgetPolicy.maxUnlockMinutes(state, now())).isEqualTo(0)
    }

    @Test
    fun `el serializador conserva todo el estado`() = runTest {
        val state = BudgetState(
            limitMinutes = 30,
            pendingLimitMinutes = 45,
            pendingFrom = "2026-11",
            day = "2026-10-13",
            usedMinutes = 20,
            unlock = UnlockSession(instagram, 20, 1L, 2L, 3),
            onboardingDone = true,
        )
        val bytes = ByteArrayOutputStream().also { BudgetSerializer.writeTo(state, it) }.toByteArray()
        assertThat(BudgetSerializer.readFrom(ByteArrayInputStream(bytes))).isEqualTo(state)
    }

    @Test(expected = CorruptionException::class)
    fun `el serializador rechaza un fichero ilegible`() = runTest {
        BudgetSerializer.readFrom(ByteArrayInputStream("[]".encodeToByteArray()))
    }
}
