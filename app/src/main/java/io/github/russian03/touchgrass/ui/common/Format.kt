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

/** "1 de noviembre". */
fun firstDayOf(month: YearMonth): String = month.atDay(1).format(DAY_MONTH)

/** "8:00". */
fun timeOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(TIME)

/** Si [epochMs] cae mañana (p. ej. la renovación de las 8:00 vista antes de medianoche). */
fun isTomorrow(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate() == LocalDate.now(zone).plusDays(1)
