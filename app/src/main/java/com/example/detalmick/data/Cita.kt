package com.example.detalmick.data

data class Opcion(val id: String, val nombre: String) : java.io.Serializable

data class Cita(
    val id: String = "",
    val uid: String = "",
    val pacienteNombre: String = "",
    val dentistaId: String = "",
    val dentistaNombre: String = "",
    val servicioId: String = "",
    val servicioNombre: String = "",
    val fecha: String = "",      // yyyy-MM-dd
    val horario: String = "",    // "09:00 - 10:00"
    val estado: String = "confirmada", // confirmada | reprogramada | cancelada
    val inicioEn: Long = 0,
    val asistenciaConfirmada: Boolean = false,
    val slotKey: String = horario.take(5).replace(":", ""),
    val anio: Int = fecha.take(4).toIntOrNull() ?: 0,
    val mes: Int = fecha.drop(5).take(2).toIntOrNull() ?: 0,
    val dia: Int = fecha.takeLast(2).toIntOrNull() ?: 0
) : java.io.Serializable
