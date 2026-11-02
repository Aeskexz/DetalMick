package com.example.detalmick.domain

enum class AccessPortal { PATIENT, DENTIST }

object DentistIdentity {
    // Reserved domain: this is a technical login identifier, never an email delivery address.
    private const val DOMAIN = "dentistas.detalmick.invalid"
    fun email(identifier: String): String? {
        val value = identifier.trim().lowercase(java.util.Locale.ROOT)
        if (Validation.email(value)) return value
        return if (Regex("^[a-z0-9_]{3,40}$").matches(value)) "$value@$DOMAIN" else null
    }
    fun isUsernameAccount(email: String): Boolean = email.endsWith("@$DOMAIN", ignoreCase = true)
}
