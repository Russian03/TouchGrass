package io.github.russian03.touchgrass.ui.unlock

import com.google.common.truth.Truth.assertThat
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.rules.Action
import io.github.russian03.touchgrass.core.rules.Condition
import io.github.russian03.touchgrass.core.rules.Match
import io.github.russian03.touchgrass.core.rules.Rule
import io.github.russian03.touchgrass.core.rules.RuleSet
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.stats.StatsRepository
import io.github.russian03.touchgrass.testing.FakeTime
import io.github.russian03.touchgrass.testing.testProfile
import io.github.russian03.touchgrass.testing.testRepository
import io.github.russian03.touchgrass.testing.testStats
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
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    @get:JUnitRule val folder = TemporaryFolder()

    // DataStore escribe en hilos de verdad, así que la prueba usa tiempo real, no virtual.
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        io.cancel()
        Dispatchers.resetMain()
        main.close()
    }

    private lateinit var stats: StatsRepository

    private fun ruleSet(packageName: String, name: String) = RuleSet(
        version = 1,
        packageName = packageName,
        name = name,
        rules = listOf(Rule("feed", "Feed", Match(listOf(Condition("feed"))), Action.Back)),
    )

    private val instagram = ruleSet(IG, "Instagram")
    private val tiktok = ruleSet(TT, "TikTok")

    private fun runUnlock(
        vararg apps: RuleSet,
        before: (UnlockViewModel) -> Unit = {},
        test: suspend (UnlockViewModel, BudgetRepository) -> Unit,
    ) = runBlocking {
        val repo = testRepository(File(folder.root, "budget.json"), io, FakeTime())
        stats = testStats(File(folder.root, "stats.json"), io, FakeTime())
        // Primeros pasos con 30 min al día.
        val onboarding = OnboardingViewModel(repo, testProfile(File(folder.root, "profile.json"), io))
        onboarding.onLimitChange(30)
        onboarding.saveLimit { onboarding.finish {} }
        withTimeout(5_000) { repo.state.first { it.onboardingDone } }

        val vm = UnlockViewModel(repo, stats) { apps.toList() }
        before(vm)
        io.launch { vm.budget.collect {} }
        withTimeout(5_000) { vm.budget.first { it != null } }
        withTimeout(5_000) { vm.form.first { it.apps != null } }
        test(vm, repo)
    }

    @Test
    fun `con varias apps primero se elige y luego se desbloquea esa`() = runUnlock(instagram, tiktok) { vm, repo ->
        assertThat(vm.form.value.selected).isNull()
        vm.choose(TT)
        assertThat(vm.form.value.selected?.name).isEqualTo("TikTok")
        vm.onDraftChange(15)
        vm.onReasonChange(UnlockReason.Bus)
        vm.request()
        assertThat(withTimeout(5_000) { vm.form.first { it.granted } }.error).isNull()
        val state = repo.state.first()
        assertThat(state.unlock?.packageName).isEqualTo(TT)
        assertThat(state.usedMinutes).isEqualTo(15)
    }

    @Test
    fun `con una sola app no hay que elegir`() = runUnlock(instagram) { vm, _ ->
        assertThat(vm.form.value.selected?.packageName).isEqualTo(IG)
        assertThat(vm.backToApps()).isFalse()
    }

    @Test
    fun `la app pedida antes de cargar queda elegida`() = runUnlock(instagram, tiktok, before = {
        it.choose(TT)
    }) { vm, _ ->
        assertThat(vm.form.value.selected?.packageName).isEqualTo(TT)
        assertThat(vm.backToApps()).isTrue()
        assertThat(vm.form.value.selected).isNull()
    }

    @Test
    fun `una app sin reglas se ignora`() = runUnlock(instagram, tiktok) { vm, _ ->
        vm.choose("com.example.otra")
        assertThat(vm.form.value.selected).isNull()
    }

    @Test
    fun `no se pide más de lo que queda`() = runUnlock(instagram, tiktok) { vm, repo ->
        vm.choose(IG)
        vm.onDraftChange(60)
        vm.onReasonChange(UnlockReason.Eat)
        vm.request()
        assertThat(withTimeout(5_000) { vm.form.first { it.granted } }.granted).isTrue()
        // Se muestra como mucho lo que queda hoy: 30.
        assertThat(repo.state.first().usedMinutes).isEqualTo(30)
    }

    @Test
    fun `con un desbloqueo en marcha no se concede otro`() = runUnlock(instagram, tiktok) { vm, repo ->
        assertThat(repo.requestUnlock(IG, 5, setOf(IG, TT))).isNull()
        vm.choose(TT)
        vm.onDraftChange(5)
        vm.onReasonChange(UnlockReason.Sleep)
        vm.request()
        val form = withTimeout(5_000) { vm.form.first { it.error != null } }
        assertThat(form.error).isEqualTo(Denial.AlreadyUnlocked)
        assertThat(form.granted).isFalse()
    }

    @Test
    fun `sin motivo no se pide`() = runUnlock(instagram) { vm, repo ->
        vm.onDraftChange(10)
        vm.request()
        assertThat(vm.form.value.busy).isFalse()
        assertThat(repo.state.first().unlock).isNull()
        // «Otro» necesita texto.
        vm.onReasonChange(UnlockReason.Other)
        assertThat(vm.form.value.reasonReady).isFalse()
        vm.request()
        assertThat(repo.state.first().unlock).isNull()
        vm.onNoteChange("  ")
        assertThat(vm.form.value.reasonReady).isFalse()
    }

    @Test
    fun `el desbloqueo se apunta en las estadísticas con su motivo`() = runUnlock(instagram, tiktok) { vm, _ ->
        vm.choose(TT)
        vm.onDraftChange(15)
        vm.onReasonChange(UnlockReason.Other)
        vm.onNoteChange("Ver una receta")
        vm.request()
        withTimeout(5_000) { vm.form.first { it.granted } }
        val day = withTimeout(5_000) { stats.state.first { it.days.isNotEmpty() } }.days.getValue("2026-10-13")
        val record = day.unlocks.single()
        assertThat(record.packageName).isEqualTo(TT)
        assertThat(record.minutes).isEqualTo(15)
        assertThat(record.reason).isEqualTo(UnlockReason.Other)
        assertThat(record.note).isEqualTo("Ver una receta")
        assertThat(day.limitMinutes).isEqualTo(30)
    }

    @Test
    fun `la nota se guarda solo con Otro`() = runUnlock(instagram) { vm, _ ->
        vm.onReasonChange(UnlockReason.Other)
        vm.onNoteChange("algo")
        vm.onReasonChange(UnlockReason.Bath)
        vm.request()
        withTimeout(5_000) { vm.form.first { it.granted } }
        val record = withTimeout(5_000) {
            stats.state.first { it.days.isNotEmpty() }
        }.days.values.single().unlocks.single()
        assertThat(record.reason).isEqualTo(UnlockReason.Bath)
        assertThat(record.note).isNull()
    }

    private companion object {
        const val IG = "com.instagram.android"
        const val TT = "com.zhiliaoapp.musically"
    }
}
