package io.github.russian03.touchgrass.data.budget

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.github.russian03.touchgrass.core.budget.BudgetState
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Guarda [BudgetState] como JSON. Un fichero ilegible se trata como corrupto. */
object BudgetSerializer : Serializer<BudgetState> {

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** Sin fichero aún: sin límite, así que no se desbloquea nada hasta los primeros pasos. */
    override val defaultValue: BudgetState = BudgetState()

    override suspend fun readFrom(input: InputStream): BudgetState = try {
        json.decodeFromString(BudgetState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Saldo ilegible", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Saldo con valores no válidos", e)
    }

    override suspend fun writeTo(t: BudgetState, output: OutputStream) {
        output.write(json.encodeToString(BudgetState.serializer(), t).encodeToByteArray())
    }
}
