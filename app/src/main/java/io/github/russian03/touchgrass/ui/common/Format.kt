package io.github.russian03.touchgrass.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** La app solo está en español: las fechas se escriben en español sea cual sea el idioma del móvil. */
private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH)
private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(SPANISH)

private val MONTH = DateTimeFormatter.ofPattern("LLLL", SPANISH)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL 'de' yyyy", SPANISH)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("LLL", SPANISH)
private val WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEEE d", SPANISH)
private val WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EEE", SPANISH)
private val FULL_DAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH)

/** "septiembre". */
fun monthName(month: YearMonth): String = month.format(MONTH)

/** "Octubre de 2026". */
fun monthTitle(month: YearMonth): String = month.format(MONTH_YEAR).replaceFirstChar { it.titlecase(SPANISH) }

/** "sep". */
fun monthShort(month: YearMonth): String = month.format(MONTH_SHORT).removeSuffix(".")

/** "Lunes 12". */
fun weekdayDay(day: LocalDate): String = day.format(WEEKDAY_DAY).replaceFirstChar { it.titlecase(SPANISH) }

/** "mié". */
fun weekdayShort(day: LocalDate): String = day.format(WEEKDAY_SHORT).removeSuffix(".")

/** "Miércoles, 15 de octubre". */
fun fullDay(day: LocalDate): String = day.format(FULL_DAY).replaceFirstChar { it.titlecase(SPANISH) }

/** "1 de noviembre". */
fun firstDayOf(month: YearMonth): String = month.atDay(1).format(DAY_MONTH)

/** "8:00". */
fun timeOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(TIME)

/** Si [epochMs] cae mañana (p. ej. la renovación de las 8:00 vista antes de medianoche). */
fun isTomorrow(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate() == LocalDate.now(zone).plusDays(1)

/** Cuenta atrás: "12:05", o "1:02:05" a partir de una hora. Redondea hacia arriba los segundos. */
fun countdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1_000
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}
