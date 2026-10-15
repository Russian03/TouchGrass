package io.github.russian03.touchgrass.ui.settings

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.testing.FakeTime
import io.github.russian03.touchgrass.testing.testProfile
import io.github.russian03.touchgrass.testing.testRepository
import io.github.russian03.touchgrass.ui.onboarding.OnboardingViewModel
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val folder = TemporaryFolder()

    // DataStore escribe en hilos de verdad, así que la prueba usa tiempo real, no virtual.
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        io.cancel()
        Dispatchers.resetMain()
        main.close()
    }

    private val time = FakeTime()

    private fun runSettings(test: suspend (SettingsViewModel) -> Unit) = runBlocking {
        val repo = testRepository(File(folder.root, "budget.json"), io, time)
        // Un solo DataStore por fichero: lo comparten los primeros pasos y Ajustes.
        val profile = testProfile(File(folder.root, "profile.json"), io)
        // Primeros pasos con 30 min al día.
        val onboarding = OnboardingViewModel(repo, profile)
        onboarding.onLimitChange(30)
        // Como en la interfaz: se termina cuando el límite ya está guardado.
        onboarding.saveLimit { onboarding.finish {} }
        withTimeout(5_000) { repo.state.first { it.onboardingDone } }

        val vm = SettingsViewModel(repo, profile)
        io.launch { vm.state.collect {} }
        withTimeout(5_000) { vm.state.first { it.budget != null } }
        test(vm)
    }

    private suspend fun SettingsViewModel.settled() = withTimeout(5_000) {
        state.first { !it.saving && it.result != null }
    }

    @Test
    fun `el nombre se guarda limpio y vacío se quita`() = runSettings { vm ->
        vm.onNameChange("  Jan ")
        assertThat(withTimeout(5_000) { vm.state.first { it.nameDraft != null } }.canSaveName).isTrue()
        vm.saveName()
        val saved = withTimeout(5_000) { vm.state.first { it.nameSaved == true && it.savedName == "Jan" } }
        assertThat(saved.nameDraft).isNull()
        assertThat(saved.shownName).isEqualTo("Jan")

        vm.onNameChange("   ")
        vm.saveName()
        assertThat(
            withTimeout(5_000) {
                vm.state.first { it.nameSaved == true && it.savedName == null }
            }.shownName,
        ).isEmpty()
    }

    @Test
    fun `bajar el límite se aplica al momento`() = runSettings { vm ->
        vm.onDraftChange(15)
        assertThat(withTimeout(5_000) { vm.state.first { it.draft == 15 } }.lowers).isTrue()
        vm.save()
        val state = vm.settled()
        assertThat(state.result).isEqualTo(LimitChange.Applied)
        assertThat(withTimeout(5_000) { vm.state.first { it.budget?.limitMinutes == 15 } }.draft).isNull()
    }

    @Test
    fun `subir el límite queda para el mes siguiente`() = runSettings { vm ->
        vm.onDraftChange(60)
        vm.save()
        assertThat(vm.settled().result).isEqualTo(LimitChange.Scheduled)
        val budget = withTimeout(5_000) { vm.state.first { it.budget?.pendingLimitMinutes == 60 } }.budget!!
        assertThat(budget.limitMinutes).isEqualTo(30)
        assertThat(budget.pendingFrom.toString()).isEqualTo("2026-11")
    }

    @Test
    fun `subir el límite con la hora manual avisa`() = runSettings { vm ->
        time.autoTime = false
        vm.onDraftChange(60)
        vm.save()
        assertThat(vm.settled().result).isEqualTo(LimitChange.Failed(Denial.ManualTime))
        assertThat(withTimeout(5_000) { vm.state.first { it.result != null } }.draft).isEqualTo(60)
    }

    @Test
    fun `sin cambios no se puede guardar`() = runSettings { vm ->
        vm.onDraftChange(30)
        assertThat(withTimeout(5_000) { vm.state.first { it.draft == 30 } }.canSave).isFalse()
    }
}
