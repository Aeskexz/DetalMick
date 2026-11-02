package com.example.detalmick.models

import com.google.firebase.firestore.DocumentId

data class Tratamiento(
    @DocumentId
    val id: String = "",
    val pacienteId: String = "",
    val descripcion: String = "",
    val detalles: String = "",
    val fecha: Long = 0L,
    val estado: String = "programado", // programado, realizado
    val costo: Double = 0.0,
    val fechaRegistro: Long = System.currentTimeMillis()
) {
    fun getEstadoTexto(): String = when(estado) {
        "realizado" -> "Realizado"
        "programado" -> "Programado"
        else -> "Desconocido"
    }

    fun getFechaFormato(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "ES"))
        return sdf.format(java.util.Date(fecha))
    }
}
