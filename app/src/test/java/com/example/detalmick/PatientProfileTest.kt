package com.example.detalmick

import com.example.detalmick.data.PatientProfile
import org.junit.Assert.*
import org.junit.Test

class PatientProfileTest {
    @Test fun absentProfileAllowsCompletingRegistration() {
        assertNull(PatientProfile.fromValue(null))
    }

    @Test fun acceptsOptionalFieldsAndNormalizesName() {
        assertEquals(PatientProfile("Ana"), PatientProfile.fromValue(mapOf("nombre" to " Ana ")))
        assertEquals(PatientProfile("Ana", "+503 7123-4567", "San Salvador"), PatientProfile.fromValue(
            mapOf("nombre" to "Ana", "telefono" to "+503 7123-4567", "direccion" to "San Salvador")))
    }

    @Test fun malformedRecordsAreRejectedInsteadOfCrashingTheScreen() {
        val invalid = listOf("texto", true, mapOf("nombre" to 123), mapOf("nombre" to " "),
            mapOf("telefono" to "71234567"), mapOf("nombre" to "a".repeat(121)),
            mapOf("nombre" to "Ana", "telefono" to 71234567),
            mapOf("nombre" to "Ana", "direccion" to "a".repeat(301)))
        invalid.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PatientProfile.fromValue(value) }
        }
    }
}
