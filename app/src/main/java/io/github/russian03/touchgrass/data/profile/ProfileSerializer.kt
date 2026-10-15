package io.github.russian03.touchgrass.data.profile

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Guarda [Profile] como JSON. Un fichero ilegible se trata como corrupto. */
object ProfileSerializer : Serializer<Profile> {

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue: Profile = Profile()

    override suspend fun readFrom(input: InputStream): Profile = try {
        json.decodeFromString(Profile.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Perfil ilegible", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Perfil con valores no válidos", e)
    }

    override suspend fun writeTo(t: Profile, output: OutputStream) {
        output.write(json.encodeToString(Profile.serializer(), t).encodeToByteArray())
    }
}
