package io.github.russian03.touchgrass.data.stats

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.github.russian03.touchgrass.core.stats.StatsState
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Guarda [StatsState] como JSON. Un fichero ilegible se trata como corrupto. */
object StatsSerializer : Serializer<StatsState> {

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue: StatsState = StatsState()

    override suspend fun readFrom(input: InputStream): StatsState = try {
        json.decodeFromString(StatsState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Estadísticas ilegibles", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Estadísticas con valores no válidos", e)
    }

    override suspend fun writeTo(t: StatsState, output: OutputStream) {
        output.write(json.encodeToString(StatsState.serializer(), t).encodeToByteArray())
    }
}
