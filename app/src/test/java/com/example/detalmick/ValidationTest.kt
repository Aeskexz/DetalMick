package com.example.detalmick

import com.example.detalmick.domain.Validation
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun rejectsInvalidAccountInputs() {
        assertFalse(Validation.email("persona@"))
        assertFalse(Validation.email("a b@clinica.com"))
        assertTrue(Validation.email("paciente+uno@example.com"))
        assertFalse(Validation.name("   "))
        assertFalse(Validation.name("a".repeat(121)))
        assertTrue(Validation.name("María del Carmen"))
        assertFalse(Validation.password("1234567"))
        assertTrue(Validation.password("Clave123!"))
    }
    @Test fun validatesOptionalContactWithoutTruncatingIt() {
        assertTrue(Validation.phone(""))
        assertTrue(Validation.phone("+503 7123-4567"))
        assertFalse(Validation.phone("abcdefghi"))
        assertFalse(Validation.phone("123"))
        assertTrue(Validation.address(""))
        assertFalse(Validation.address("a".repeat(301)))
    }
}
