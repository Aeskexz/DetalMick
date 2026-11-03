package com.example.detalmick.data

data class PatientProfile(
    val nombre: String = "",
    val telefono: String = "",
    val direccion: String = "",
    val especialidad: String = ""
) {
    companion object {
        /** Firebase data can also be edited by administrators: reject invalid records safely. */
        fun fromValue(value: Any?): PatientProfile? {
            if (value == null) return null
            require(value is Map<*, *>) { "El perfil debe ser un objeto." }
            val name = value["nombre"]
            val phone = value["telefono"] ?: ""
            val address = value["direccion"] ?: ""
            val esp = value["especialidad"] ?: ""
            require(name is String && name.trim().length in 1..120) { "Nombre inválido." }
            require(phone is String && phone.length <= 25) { "Teléfono inválido." }
            require(address is String && address.length <= 300) { "Dirección inválida." }
            return PatientProfile(name.trim(), phone.toString(), address.toString(), esp.toString())
        }
    }
}
