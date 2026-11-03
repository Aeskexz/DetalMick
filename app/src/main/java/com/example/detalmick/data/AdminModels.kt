package com.example.detalmick.data

data class AdminDentist(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val especialidad: String = "",
    val phone: String = "",
    val activo: Boolean = true,
    val isAdmin: Boolean = false
)
