# Reglas R8 del proyecto. Hilt, Compose y AndroidX traen sus propias reglas
# de consumidor, así que de momento no hace falta añadir nada.

# Quitar los logs de debug y verbose en release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
