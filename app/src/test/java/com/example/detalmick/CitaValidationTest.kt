package com.example.detalmick

import com.example.detalmick.domain.CitaValidation
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class CitaValidationTest {
    @Test fun rejectsImpossibleDatesAndTrailingText() {
        for(date in listOf("2030-02-30","2030-13-01","2030-01-01 extra","2030-2-01","2030-00-01"))
            assertNull(CitaValidation.inicioMillis(date,"09:00 - 10:00"))
    }
    @Test fun acceptsLeapDayAndUsesClinicTimeZone() {
        val expected=LocalDate.of(2028,2,29).atTime(9,0).toInstant(ZoneOffset.ofHours(-6)).toEpochMilli()
        assertEquals(expected,CitaValidation.inicioMillis("2028-02-29","09:00 - 10:00"))
    }
    @Test fun rejectsSundayAndUnsupportedHours() {
        assertFalse(CitaValidation.validarFechaHorario("2099-11-01","08:00 - 09:00"))
        val sunday=LocalDate.of(2099,1,1).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY))
        assertFalse(CitaValidation.validarFechaHorario(sunday.toString(),"09:00 - 10:00"))
    }
}
