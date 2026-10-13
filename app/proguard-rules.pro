# Reglas R8 del proyecto. Hilt, Compose y AndroidX traen sus propias reglas
# de consumidor, así que de momento no hace falta añadir nada.

# Quitar los logs de debug, verbose e info en release: así logcat no revela cuándo se usa
# Instagram ni qué reglas saltan. Los avisos y errores se mantienen.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
