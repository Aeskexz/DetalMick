package com.example.detalmick.data

import android.content.Context
import com.example.detalmick.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class AdminRepository {
    private val database get() = FirebaseProvider.database

    private var observed: Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>? = null
    fun stop() { observed?.let { it.first.removeEventListener(it.second) }; observed = null }

    fun observeStaff(onUpdate: (List<AdminDentist>) -> Unit, onError: (String) -> Unit) {
        stop()
        val dentistasRef = database.getReference("dentistas")
        val rolesRef = database.getReference("roles")

        val listener = object : ValueEventListener {
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
        }
        observed = dentistasRef to listener
        dentistasRef.addValueEventListener(listener)
    }

    fun deleteDentist(uid: String, onComplete: (Boolean, String?) -> Unit) {
        setStaffStatus(uid, false, onComplete)
    }

    fun setStaffStatus(uid: String, activo: Boolean, onComplete: (Boolean, String?) -> Unit) {
        database.getReference("dentistas/$uid").get().addOnSuccessListener { profile ->
            val name = (profile.child("nombre").value as? String).orEmpty()
            val specialty = (profile.child("especialidad").value as? String).orEmpty()
            val publicProfile = if(activo && name.isNotBlank() && specialty.isNotBlank()) mapOf("nombre" to name,"especialidad" to specialty) else null
            database.reference.updateChildren(mapOf("roles/$uid/activo" to activo, "dentistas/$uid/activo" to activo,"clinica/dentistas/$uid" to publicProfile))
                .addOnSuccessListener { onComplete(true, null) }.addOnFailureListener { onComplete(false, "No se confirmó el cambio.") }
        }.addOnFailureListener { onComplete(false,"No se pudo consultar el perfil.") }
    }

    fun inviteDentist(
        context: Context,
        email: String,
        tempPass: String,
        reception: Boolean = false,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val tempName = "AdminInvite_${System.currentTimeMillis()}"
        val tempApp: FirebaseApp
        try {
            tempApp = FirebaseApp.initializeApp(context, FirebaseProvider.auth.app.options, tempName)
        } catch (e: Exception) {
            onComplete(false, "Error al inicializar sesión auxiliar: ${e.message}")
            return
        }

        val tempAuth = FirebaseAuth.getInstance(tempApp)
        if(BuildConfig.USE_FIREBASE_EMULATORS) tempAuth.useEmulator("10.0.2.2",9099)
        tempAuth.createUserWithEmailAndPassword(email.trim(), tempPass)
            .addOnSuccessListener { authResult ->
                val newUid = authResult.user?.uid
                if (newUid == null) {
                    tempApp.delete()
                    onComplete(false, "No se pudo obtener el ID del nuevo usuario.")
                    return@addOnSuccessListener
                }

                val rolesData = mapOf(
                    "personal" to !reception,
                    "recepcionista" to reception,
                    "activo" to true,
                    "admin" to false,
                    "primerIngreso" to true
                )
                val dentistData = mapOf(
                    "nombre" to if(reception) "Recepción" else "Dr/Dra. Invitado",
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
                        authResult.user?.delete()?.addOnCompleteListener { deleted ->
                            tempAuth.signOut(); tempApp.delete()
                            onComplete(false, if(deleted.isSuccessful) "No se guardó el perfil; se revirtió la cuenta. Intenta de nuevo." else "No se guardó el perfil. Revisa la cuenta creada en Authentication antes de reintentar.")
                        }
                    }
            }
            .addOnFailureListener { authErr ->
                tempApp.delete()
                onComplete(false, authErr.message)
            }
    }
}
