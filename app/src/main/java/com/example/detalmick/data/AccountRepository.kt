package com.example.detalmick.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.database.DataSnapshot
import java.util.concurrent.TimeUnit

class AccountRepository {
    val auth get() = FirebaseProvider.auth
    private val database get() = FirebaseProvider.database
    val connection get() = database.getReference(".info/connected")

    fun register(email: String, password: String): Task<AuthResult> =
        auth.createUserWithEmailAndPassword(email, password)

    fun login(email: String, password: String): Task<AuthResult> =
        auth.signInWithEmailAndPassword(email, password)

    fun reset(email: String): Task<Void> = auth.sendPasswordResetEmail(email)

    fun profile(uid: String, staff: Boolean = false): Task<DataSnapshot> = Tasks.withTimeout(
        database.getReference(if (staff) "dentistas" else "usuarios").child(uid).get(), 15, TimeUnit.SECONDS)

    fun save(uid: String, profile: PatientProfile, staff: Boolean = false): Task<Void> = Tasks.withTimeout(
        database.getReference(if (staff) "dentistas" else "usuarios").child(uid).updateChildren(
            mapOf("nombre" to profile.nombre, "telefono" to profile.telefono,
                "direccion" to profile.direccion, "especialidad" to profile.especialidad)
        ), 15, TimeUnit.SECONDS)

    fun role(uid: String): Task<DataSnapshot> = Tasks.withTimeout(
        database.getReference("roles").child(uid).get(), 15, TimeUnit.SECONDS)

    fun completeFirstLogin(uid: String): Task<Void> = Tasks.withTimeout(
        database.getReference("roles").child(uid).child("primerIngreso").setValue(false), 15, TimeUnit.SECONDS)

    fun logout() {
        // Drop unacknowledged local writes before switching accounts.
        database.purgeOutstandingWrites()
        auth.signOut()
    }
}
