package io.github.russian03.touchgrass.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `Main` en un hilo real para los tests de ViewModel con DataStore (que escribe en hilos de verdad).
 *
 * Al terminar cierra en orden: primero cancela y espera los `viewModelScope` registrados con [track] y el
 * scope [io]; solo entonces suelta `Main` y cierra su hilo. Si se soltara `Main` con corrutinas vivas, una
 * reanudación en `Main.immediate` chocaría con `resetMain()` ("Dispatchers.Main is used concurrently").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealMainRule : TestWatcher() {

    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val viewModels = mutableListOf<ViewModel>()

    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Registra un ViewModel para cancelar su scope al terminar el test. */
    fun <T : ViewModel> track(vm: T): T = vm.also { viewModels += it }

    override fun starting(description: Description) = Dispatchers.setMain(main)

    override fun finished(description: Description) {
        try {
            runBlocking {
                val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] } +
                    listOfNotNull(io.coroutineContext[Job])
                // Se cancela todo antes de esperar: un fallo o timeout en uno no deja vivos a los demás.
                jobs.forEach { it.cancel() }
                withTimeout(5_000) { jobs.joinAll() }
            }
        } finally {
            Dispatchers.resetMain()
            main.close()
        }
    }
}
