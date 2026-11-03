package com.example.detalmick.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctionsException

class AdminRepository {
    private val database get() = FirebaseProvider.database

    fun observeStaff(onUpdate: (List<AdminDentist>) -> Unit, onError: (String) -> Unit) {
        val dentistasRef = database.getReference("dentistas")
        val rolesRef = database.getReference("roles")

        dentistasRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dentistasSnapshot: DataSnapshot) {
                rolesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(rolesSnapshot: DataSnapshot) {
                        val list = mutableListOf<AdminDentist>()
                        for (child in dentistasSnapshot.children) {
                            val uid = child.key ?: continue
                            val name = child.child("nombre").getValue(String::class.java).orEmpty()
                            val phone = child.child("telefono").getValue(String::class.java).orEmpty()
                            val esp = child.child("especialidad").getValue(String::class.java).orEmpty()
                            val email = child.child("email").getValue(String::class.java).orEmpty()

                            val roleChild = rolesSnapshot.child(uid)
                            val activo = roleChild.child("activo").getValue(Boolean::class.java)
                                ?: child.child("activo").getValue(Boolean::class.java)
                                ?: true
                            val isAdmin = roleChild.child("admin").getValue(Boolean::class.java) ?: false

                            list.add(
                                AdminDentist(
                                    uid = uid,
                                    name = name.ifBlank { "Dr/Dra. $uid" },
                                    email = email,
                                    phone = phone,
                                    especialidad = esp.ifBlank { "Pendiente de completar" },
                                    activo = activo,
                                    isAdmin = isAdmin
                                )
                            )
                        }
                        onUpdate(list)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        val list = mutableListOf<AdminDentist>()
                        for (child in dentistasSnapshot.children) {
                            val uid = child.key ?: continue
                            val name = child.child("nombre").getValue(String::class.java).orEmpty()
                            val phone = child.child("telefono").getValue(String::class.java).orEmpty()
                            val esp = child.child("especialidad").getValue(String::class.java).orEmpty()
                            val email = child.child("email").getValue(String::class.java).orEmpty()
                            val activo = child.child("activo").getValue(Boolean::class.java) ?: true

                            list.add(
                                AdminDentist(
                                    uid = uid,
                                    name = name.ifBlank { "Dr/Dra. $uid" },
                                    email = email,
                                    phone = phone,
                                    especialidad = esp.ifBlank { "Pendiente de completar" },
                                    activo = activo,
                                    isAdmin = false
                                )
                            )
                        }
                        onUpdate(list)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        })
    }

    fun deleteDentist(uid: String, onComplete: (Boolean, String?) -> Unit) {
        val user = FirebaseProvider.auth.currentUser
        if (user == null) {
            onComplete(false, "Tu sesión expiró. Cierra sesión e inicia de nuevo como administrador.")
            return
        }

        user.getIdToken(true)
            .addOnSuccessListener {
                FirebaseProvider.functions.getHttpsCallable("deleteDentist")
                    .call(mapOf("uid" to uid))
                    .addOnSuccessListener { onComplete(true, null) }
                    .addOnFailureListener { error ->
                        val message = if (error is FirebaseFunctionsException) {
                            when (error.code) {
                                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                                    "La función no recibió tu sesión. Verifica que esté desplegada en el mismo proyecto Firebase que usa la app y vuelve a iniciar sesión."
                                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                                    "Tu cuenta no tiene el permiso de administrador en Realtime Database."
                                FirebaseFunctionsException.Code.NOT_FOUND ->
                                    "No se encontró la función deleteDentist en este proyecto o región."
                                else -> error.localizedMessage
                            }
                        } else error.localizedMessage
                        onComplete(false, message)
                    }
            }
            .addOnFailureListener {
                onComplete(false, "No se pudo renovar tu sesión de administrador. Cierra sesión e inicia de nuevo.")
            }
    }

    fun setStaffStatus(uid: String, activo: Boolean, onComplete: (Boolean, String?) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "roles/$uid/activo" to activo,
            "dentistas/$uid/activo" to activo
        )
        database.reference.updateChildren(updates)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener {
                database.getReference("roles").child(uid).child("activo").setValue(activo)
                    .addOnSuccessListener { onComplete(true, null) }
                    .addOnFailureListener { err -> onComplete(false, err.message) }
            }
    }

    fun inviteDentist(
        context: Context,
        email: String,
        tempPass: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val tempName = "AdminInvite_${System.currentTimeMillis()}"
        val tempApp: FirebaseApp
        try {
            tempApp = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, tempName)
        } catch (e: Exception) {
            onComplete(false, "Error al inicializar sesión auxiliar: ${e.message}")
            return
        }

        val tempAuth = FirebaseAuth.getInstance(tempApp)
        tempAuth.createUserWithEmailAndPassword(email.trim(), tempPass)
            .addOnSuccessListener { authResult ->
                val newUid = authResult.user?.uid
                if (newUid == null) {
                    tempApp.delete()
                    onComplete(false, "No se pudo obtener el ID del nuevo usuario.")
                    return@addOnSuccessListener
                }

                val rolesData = mapOf(
                    "personal" to true,
                    "activo" to true,
                    "admin" to false,
                    "primerIngreso" to true
                )
                val dentistData = mapOf(
                    "nombre" to "Dr/Dra. Invitado",
                    "email" to email.trim(),
                    "especialidad" to "",
                    "telefono" to "",
                    "activo" to true
                )

                val updates = hashMapOf<String, Any>(
                    "roles/$newUid" to rolesData,
                    "dentistas/$newUid" to dentistData
                )

                database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        tempApp.delete()
                        onComplete(true, null)
                    }
                    .addOnFailureListener { dbErr ->
                        tempApp.delete()
                        onComplete(false, "Usuario creado en Auth pero falló en base de datos: ${dbErr.message}")
                    }
            }
            .addOnFailureListener { authErr ->
                tempApp.delete()
                onComplete(false, authErr.message)
            }
    }
}
