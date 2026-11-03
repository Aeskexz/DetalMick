package com.example.detalmick.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

object AccessControl {
    fun active(role: DataSnapshot) = role.child("activo").value != false
    fun clinical(role: DataSnapshot) = active(role) && (role.child("personal").value == true || role.child("admin").value == true)
    fun reception(role: DataSnapshot) = active(role) && role.child("recepcionista").value == true
    fun staff(role: DataSnapshot) = clinical(role) || reception(role)
    fun denied(code: String) {
        val uid = FirebaseProvider.auth.currentUser?.uid ?: return
        FirebaseProvider.database.getReference("eventosSeguridad/$uid").push().setValue(
            mapOf("codigo" to code, "creadoEn" to ServerValue.TIMESTAMP))
    }
}
