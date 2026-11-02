package com.example.detalmick.domain

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

object ClinicalValidation {
    fun cents(text: String): Long? = try {
        val clean = text.trim().replace(',', '.')
        if (!Regex("^[0-9]{1,7}(\\.[0-9]{1,2})?$").matches(clean)) null
        else BigDecimal(clean).movePointRight(2).longValueExact().takeIf { it in 1..999999999L }
    } catch (_: ArithmeticException) { null }

    fun date(text: String): Boolean = try {
        Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(text) &&
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(text) != null
    } catch (_: java.text.ParseException) { false }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date())
    fun amount(cents: Long): String = java.text.NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2))
    fun displayDate(date: String): String = if (date(date)) "${date.substring(8)}/${date.substring(5, 7)}/${date.substring(0, 4)}" else date
    fun status(total: Long, paid: Long): String {
        require(total in 1..999999999L && paid in 0..total)
        return if (paid == total) "pagado" else "pendiente"
    }
}
