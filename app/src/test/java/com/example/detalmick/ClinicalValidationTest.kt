package com.example.detalmick

import com.example.detalmick.domain.ClinicalValidation
import com.example.detalmick.domain.DentistIdentity
import com.example.detalmick.data.Payment
import com.example.detalmick.data.Treatment
import org.junit.Assert.*
import org.junit.Test

class ClinicalValidationTest {
    @Test fun moneyUsesExactIntegerCents() {
        assertEquals(1001L, ClinicalValidation.cents("10.01"))
        assertEquals(2550L, ClinicalValidation.cents("25,50"))
        listOf("0", "-1", "2.001", "NaN", "1e3", "99999999", "abc").forEach { assertNull(ClinicalValidation.cents(it)) }
        assertEquals("pendiente", ClinicalValidation.status(10000, 2500))
        assertEquals("pagado", ClinicalValidation.status(10000, 10000))
        assertThrows(IllegalArgumentException::class.java) { ClinicalValidation.status(100, 101) }
    }
    @Test fun datesMustActuallyExist() {
        assertTrue(ClinicalValidation.date("2028-02-29"))
        listOf("2026-02-29", "2026-04-31", "2026-13-01", "2026-1-2", "texto").forEach { assertFalse(ClinicalValidation.date(it)) }
        assertEquals("01/12/2026", ClinicalValidation.displayDate("2026-12-01"))
    }
    @Test fun dentistUsernamesHaveADeterministicNonDeliveryAddress() {
        assertEquals("dentista_test@dentistas.detalmick.invalid", DentistIdentity.email("Dentista_Test"))
        assertEquals("dentista@example.com", DentistIdentity.email("Dentista@Example.com"))
        assertNull(DentistIdentity.email("usuario con espacios"))
    }
    @Test fun malformedClinicalDataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Treatment.decode("t1", mapOf("titulo" to "Limpieza")) }
        val payment = mapOf("concepto" to "Limpieza", "totalCentavos" to 10000L, "abonadoCentavos" to 2500L,
            "estado" to "pendiente", "fechaLimite" to "2026-12-15", "abonos" to mapOf("a1" to mapOf("montoCentavos" to 2500L, "creadoEn" to 1L)))
        assertEquals(7500L, Payment.decode("p1", payment).saldo)
        assertThrows(IllegalArgumentException::class.java) { Payment.decode("p1", payment + ("estado" to "pagado")) }
        assertThrows(IllegalArgumentException::class.java) { Payment.decode("p1", payment + ("abonadoCentavos" to 3000L)) }
    }
}
