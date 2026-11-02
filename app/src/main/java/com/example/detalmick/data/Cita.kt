package com.example.detalmick.data

data class Opcion(val id: String, val nombre: String)

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
    val estado: String = "confirmada" // confirmada | reprogramada | cancelada
)