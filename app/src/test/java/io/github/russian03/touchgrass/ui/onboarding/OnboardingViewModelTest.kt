package io.github.russian03.touchgrass.ui.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.testing.FakeTime
import io.github.russian03.touchgrass.testing.testProfile
import io.github.russian03.touchgrass.testing.testRepository
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
class OnboardingViewModelTest {

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

    private val profile by lazy { testProfile(File(folder.root, "profile.json"), io) }

    private fun viewModel() =
        OnboardingViewModel(testRepository(File(folder.root, "budget.json"), io, FakeTime()), profile)

    @Test
    fun `aceptar el aviso guarda el consentimiento`() = runBlocking {
        val vm = viewModel()
        val saved = CompletableDeferred<Unit>()
        vm.acceptAccessibility { saved.complete(Unit) }
        withTimeout(5_000) { saved.await() }
        assertThat(profile.profile.first().accessibilityConsent).isTrue()
        assertThat(vm.state.value.declined).isFalse()
    }

    @Test
    fun `rechazar el aviso no guarda nada`() = runBlocking {
        val vm = viewModel()
        vm.declineAccessibility()
        assertThat(vm.state.value.declined).isTrue()
        assertThat(profile.profile.first().accessibilityConsent).isFalse()
    }

    @Test
    fun `aceptar después de rechazar quita el rechazo`() = runBlocking {
        val vm = viewModel()
        vm.declineAccessibility()
        val saved = CompletableDeferred<Unit>()
        vm.acceptAccessibility { saved.complete(Unit) }
        withTimeout(5_000) { saved.await() }
        assertThat(vm.state.value.declined).isFalse()
    }
}
