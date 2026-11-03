package com.example.detalmick.data

import com.example.detalmick.domain.ClinicalValidation

data class PatientSummary(val uid: String, val name: String)

data class Treatment(
    val id: String, val titulo: String, val descripcion: String,
    val estado: String, val fecha: String, val odontologo: String = ""
) {
    companion object {
        fun decode(id: String, value: Any?): Treatment {
            require(value is Map<*, *>)
            val title = value["titulo"] as? String ?: throw IllegalArgumentException("Tratamiento sin título")
            val description = value["descripcion"] as? String ?: throw IllegalArgumentException("Tratamiento sin descripción")
            val state = value["estado"] as? String ?: throw IllegalArgumentException("Tratamiento sin estado")
            val date = value["fecha"] as? String ?: throw IllegalArgumentException("Tratamiento sin fecha")
            require(title.isNotBlank() && title.length <= 120 && description.isNotBlank() && description.length <= 2000)
            require(state in setOf("programado", "realizado") && ClinicalValidation.date(date))
            return Treatment(id, title, description, state, date, value["registradoPor"] as? String ?: "")
        }
    }
}

data class PaymentEntry(val id: String, val cents: Long, val at: Long)
data class Payment(
    val id: String, val concepto: String, val totalCentavos: Long,
    val abonadoCentavos: Long, val fechaLimite: String, val abonos: List<PaymentEntry>,
    val citaId: String = "", val creadoEn: Long = 0
) {
    val saldo get() = totalCentavos - abonadoCentavos
    val estado get() = ClinicalValidation.status(totalCentavos, abonadoCentavos)
    companion object {
        fun decode(id: String, value: Any?): Payment {
            require(value is Map<*, *>)
            val concept = value["concepto"] as? String ?: throw IllegalArgumentException("Pago sin concepto")
            val total = value["totalCentavos"] as? Long ?: throw IllegalArgumentException("Monto inválido")
            val paid = value["abonadoCentavos"] as? Long ?: throw IllegalArgumentException("Abono inválido")
            val date = value["fechaLimite"] as? String ?: throw IllegalArgumentException("Fecha inválida")
            require(concept.isNotBlank() && concept.length <= 120 && ClinicalValidation.date(date))
            require(value["estado"] == ClinicalValidation.status(total, paid))
            val entries = (value["abonos"] as? Map<*, *>)?.map { (key, entry) ->
                require(entry is Map<*, *>)
                val cents = entry["montoCentavos"] as? Long ?: throw IllegalArgumentException("Abono inválido")
                val at = entry["creadoEn"] as? Long ?: throw IllegalArgumentException("Fecha inválida")
                require(cents > 0 && at > 0)
                PaymentEntry(key.toString(), cents, at)
            }.orEmpty().sortedByDescending { it.at }
            require(entries.sumOf { it.cents } == paid)
            return Payment(id, concept, total, paid, date, entries, value["citaId"] as? String ?: "", value["creadoEn"] as? Long ?: 0)
        }
    }
}
