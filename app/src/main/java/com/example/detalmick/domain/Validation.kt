package com.example.detalmick.domain

object Validation {
    fun email(value: String): Boolean =
        value.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)

    fun name(value: String): Boolean = value.trim().length in 1..120

    fun phone(value: String): Boolean = value.isBlank() ||
        (value.length <= 25 && Regex("^[+0-9() -]+$").matches(value) &&
            value.count(Char::isDigit) in 8..15)

    fun address(value: String): Boolean = value.length <= 300

    fun password(value: String): Boolean = value.length in 8..128
}
