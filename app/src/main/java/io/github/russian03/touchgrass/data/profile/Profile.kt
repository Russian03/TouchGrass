package io.github.russian03.touchgrass.data.profile

import kotlinx.serialization.Serializable

/**
 * Datos personales que solo sirven para la interfaz (el saludo) y el consentimiento del servicio de
 * accesibilidad. No afectan al bloqueo.
 */
@Serializable
data class Profile(
    /** Nombre con el que se saluda en Inicio, o `null` si no se ha dicho. */
    val name: String? = null,
    /**
     * Ha aceptado el aviso de lo que mira el servicio de accesibilidad. Google Play exige pedirlo
     * antes de mandar a activarlo; sin él, «Activar» enseña antes el aviso.
     */
    val accessibilityConsent: Boolean = false,
) {
    companion object {
        /** Largo máximo del nombre: cabe en la cabecera en una línea. */
        const val NAME_MAX_LENGTH = 20

        /**
         * Nombre limpio para guardar: sin espacios de sobra ni asteriscos (marcan la negrita del
         * saludo), recortado; vacío es `null`.
         */
        fun cleanName(raw: String): String? = raw
            .replace("*", "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(NAME_MAX_LENGTH)
            .trim()
            .ifEmpty { null }
    }
}
