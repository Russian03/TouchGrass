package io.github.russian03.touchgrass.data.budget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.BudgetResult
import io.github.russian03.touchgrass.core.budget.BudgetState
import io.github.russian03.touchgrass.core.budget.Denial
import io.github.russian03.touchgrass.core.budget.TimeSnapshot
import io.github.russian03.touchgrass.core.budget.TimeSource
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Saldo diario guardado en el dispositivo. La interfaz y el servicio de accesibilidad viven en
 * procesos distintos, así que se usa un DataStore multiproceso: los cambios hechos desde la app
 * llegan al momento al servicio. Todos los cambios pasan por [BudgetPolicy].
 */
@Singleton
class BudgetRepository internal constructor(private val store: DataStore<BudgetState>, private val time: TimeSource) {

    @Inject
    constructor(@ApplicationContext context: Context, time: TimeSource) : this(createStore(context), time)

    /** Estado guardado. Si no se puede leer, el estado por defecto, que no desbloquea nada. */
    val state: Flow<BudgetState> =
        store.data.catch { e ->
            if (e is IOException) emit(BudgetSerializer.defaultValue) else throw e
        }

    fun now(): TimeSnapshot = time.now()

    /** Devuelve `null` si se ha concedido, o el motivo por el que no. */
    suspend fun requestUnlock(packageName: String, minutes: Int, allowedPackages: Set<String>): Denial? =
        apply { state, now -> BudgetPolicy.requestUnlock(state, packageName, minutes, allowedPackages, now) }

    suspend fun setInitialLimit(minutes: Int): Denial? =
        apply { state, now -> BudgetPolicy.setInitialLimit(state, minutes, now) }

    suspend fun changeLimit(minutes: Int): Denial? =
        apply { state, now -> BudgetPolicy.changeLimit(state, minutes, now) }

    suspend fun completeOnboarding(): Denial? =
        apply { state, _ -> BudgetResult.Ok(BudgetPolicy.completeOnboarding(state)) }

    private suspend fun apply(operation: (BudgetState, TimeSnapshot) -> BudgetResult): Denial? {
        var denial: Denial? = null
        try {
            store.updateData { state ->
                // La hora se lee dentro de la transacción, con el estado más reciente.
                when (val result = operation(state, time.now())) {
                    is BudgetResult.Ok -> {
                        denial = null
                        result.state
                    }
                    is BudgetResult.Denied -> {
                        denial = result.reason
                        state
                    }
                }
            }
        } catch (e: IOException) {
            return Denial.Storage
        }
        return denial
    }

    private companion object {
        fun createStore(context: Context): DataStore<BudgetState> = MultiProcessDataStoreFactory.create(
            serializer = BudgetSerializer,
            // Un fichero corrupto vuelve al estado por defecto, que no desbloquea nada.
            corruptionHandler = ReplaceFileCorruptionHandler { BudgetSerializer.defaultValue },
            produceFile = { File(context.filesDir, "datastore/budget.json") },
        )
    }
}
