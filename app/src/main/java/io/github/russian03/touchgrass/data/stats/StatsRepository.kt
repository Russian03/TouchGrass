package io.github.russian03.touchgrass.data.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.russian03.touchgrass.core.budget.BudgetPolicy
import io.github.russian03.touchgrass.core.budget.TimeSource
import io.github.russian03.touchgrass.core.budget.UnlockSession
import io.github.russian03.touchgrass.core.stats.StatsPolicy
import io.github.russian03.touchgrass.core.stats.StatsState
import io.github.russian03.touchgrass.core.stats.UnlockReason
import io.github.russian03.touchgrass.core.stats.UnlockRecord
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Estadísticas guardadas en el dispositivo. Como el saldo, se usa un DataStore multiproceso:
 * el servicio apunta los bloqueos y la app, los desbloqueos. Si no se puede guardar algo, se
 * pierde ese dato pero nada deja de funcionar.
 */
@Singleton
class StatsRepository internal constructor(private val store: DataStore<StatsState>, private val time: TimeSource) {

    @Inject
    constructor(@ApplicationContext context: Context, time: TimeSource) : this(createStore(context), time)

    val state: Flow<StatsState> =
        store.data.catch { e ->
            if (e is IOException) emit(StatsSerializer.defaultValue) else throw e
        }

    /** Día lógico de hoy (de 8:00 a 8:00). */
    fun today(): LocalDate = BudgetPolicy.logicalDay(time.now())

    /** Empieza a contar días, si aún no se había empezado. */
    suspend fun start() = update { StatsPolicy.start(it, today()) }

    suspend fun recordUnlock(session: UnlockSession, limitMinutes: Int, reason: UnlockReason, note: String?) {
        val day = session.day?.let(LocalDate::parse) ?: today()
        val record = UnlockRecord(session.packageName, session.minutes, session.startWallMs, reason, note)
        update { StatsPolicy.recordUnlock(it, day, record, limitMinutes) }
    }

    suspend fun recordRefund(session: UnlockSession, refundedMinutes: Int) {
        val day = session.day?.let(LocalDate::parse) ?: return
        update { StatsPolicy.recordRefund(it, day, session.startWallMs, refundedMinutes) }
    }

    suspend fun recordBlock(packageName: String) = update { StatsPolicy.recordBlock(it, today(), packageName) }

    suspend fun recordLeave(packageName: String) = update { StatsPolicy.recordLeave(it, today(), packageName) }

    suspend fun markSeen(month: YearMonth) = update { StatsPolicy.markSeen(it, month) }

    suspend fun markNotified(month: YearMonth) = update { StatsPolicy.markNotified(it, month) }

    private suspend fun update(change: (StatsState) -> StatsState) {
        try {
            store.updateData(change)
        } catch (_: IOException) {
            // Las estadísticas no son críticas: si no se pueden guardar, se pierde este dato.
        }
    }

    private companion object {
        fun createStore(context: Context): DataStore<StatsState> = MultiProcessDataStoreFactory.create(
            serializer = StatsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { StatsSerializer.defaultValue },
            produceFile = { File(context.filesDir, "datastore/stats.json") },
        )
    }
}
