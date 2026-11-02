package com.example.detalmick.domain

import com.example.detalmick.data.Cita
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CitaValidation {
    val HORARIOS = (9..16).map { "%02d:00 - %02d:00".format(Locale.ROOT, it, it + 1) }

    fun slotKey(horario: String) = horario.take(5).replace(":", "")
    fun validarServicio(id: String) = id.isNotBlank()

    private fun inicio(fecha: String, horario: String): Calendar? {
        val dia = (try {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(fecha)
        } catch (e: Exception) { null }) ?: return null
        val hora = horario.take(2).toIntOrNull() ?: return null
        return Calendar.getInstance().apply { time = dia; set(Calendar.HOUR_OF_DAY, hora); set(Calendar.MINUTE, 0) }
    }

    fun esActiva(c: Cita) =
        c.estado != "cancelada" && inicio(c.fecha, c.horario)?.after(Calendar.getInstance()) == true

    fun validarFechaHorario(fecha: String, horario: String): Boolean {
        if (horario !in HORARIOS) return false
        val i = inicio(fecha, horario) ?: return false
        return i.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && i.after(Calendar.getInstance())
    }
}