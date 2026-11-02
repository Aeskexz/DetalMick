package com.example.detalmick.models

import com.google.firebase.firestore.DocumentId

data class Pago(
    @DocumentId
    val id: String = "",
    val tratamientoId: String = "",
    val pacienteId: String = "",
    val monto: Double = 0.0,
    val saldoPendiente: Double = 0.0,
    val estado: String = "pendiente", // pendiente, pagado
    val fechaLimite: Long = 0L,
    val fechaPago: Long = 0L,
    val fechaRegistro: Long = System.currentTimeMillis()
) {
    fun getEstadoTexto(): String = when(estado) {
        "pagado" -> "Pagado"
        "pendiente" -> "Pendiente"
        else -> "Desconocido"
    }

    fun getFechaLimiteFormato(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "ES"))
        return sdf.format(java.util.Date(fechaLimite))
    }

    fun getFechaPagoFormato(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "ES"))
        return sdf.format(java.util.Date(fechaPago))
    }
}
