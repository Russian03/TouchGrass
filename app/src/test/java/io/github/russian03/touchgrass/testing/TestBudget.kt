package io.github.russian03.touchgrass.testing

import androidx.datastore.core.DataStoreFactory
import io.github.russian03.touchgrass.core.budget.TimeSnapshot
import io.github.russian03.touchgrass.core.budget.TimeSource
import io.github.russian03.touchgrass.data.budget.BudgetRepository
import io.github.russian03.touchgrass.data.budget.BudgetSerializer
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope

/** Reloj de prueba con hora de Madrid que se puede mover a mano. */
class FakeTime(var dateTime: String = "2026-10-13T10:00", var autoTime: Boolean = true) : TimeSource {
    private val zone = ZoneId.of("Europe/Madrid")

    override fun now() = TimeSnapshot(
        wallMs = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli(),
        elapsedMs = 60_000_000,
        bootCount = 1,
        zone = zone,
        autoTime = autoTime,
    )
}

fun testRepository(file: File, scope: CoroutineScope, time: TimeSource = FakeTime()) = BudgetRepository(
    DataStoreFactory.create(serializer = BudgetSerializer, scope = scope, produceFile = { file }),
    time,
)
