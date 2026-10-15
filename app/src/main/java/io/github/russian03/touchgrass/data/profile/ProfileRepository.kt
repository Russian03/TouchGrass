package io.github.russian03.touchgrass.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Perfil guardado en el dispositivo. Solo lo usa la app (el servicio no lo lee), así que basta un
 * DataStore normal. Si no se puede guardar, Inicio saluda sin nombre.
 */
@Singleton
class ProfileRepository internal constructor(private val store: DataStore<Profile>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(createStore(context))

    val profile: Flow<Profile> =
        store.data.catch { e ->
            if (e is IOException) emit(ProfileSerializer.defaultValue) else throw e
        }

    /** Guarda el nombre (limpio); vacío lo borra. Devuelve `false` si no se ha podido guardar. */
    suspend fun setName(raw: String): Boolean = try {
        store.updateData { it.copy(name = Profile.cleanName(raw)) }
        true
    } catch (_: IOException) {
        false
    }

    private companion object {
        fun createStore(context: Context): DataStore<Profile> = DataStoreFactory.create(
            serializer = ProfileSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { ProfileSerializer.defaultValue },
            produceFile = { File(context.filesDir, "datastore/profile.json") },
        )
    }
}
