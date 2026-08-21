package com.rodsan.pay.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val locale = Locale("es", "CO")

    fun date(millis: Long): String =
        SimpleDateFormat("EEEE dd/MM/yyyy", locale).format(Date(millis))
            .replaceFirstChar { it.titlecase(locale) }

    fun dateTime(millis: Long): String =
        SimpleDateFormat("EEEE dd/MM/yyyy HH:mm", locale).format(Date(millis))
            .replaceFirstChar { it.titlecase(locale) }
}
