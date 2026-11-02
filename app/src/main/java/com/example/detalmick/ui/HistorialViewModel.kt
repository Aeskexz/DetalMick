package com.example.detalmick.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.detalmick.models.Pago
import com.example.detalmick.models.Tratamiento
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistorialViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val tratamientos = MutableLiveData<List<Tratamiento>>(emptyList())
    val pagos = MutableLiveData<List<Pago>>(emptyList())
    val loading = MutableLiveData<Boolean>(false)
    val error = MutableLiveData<String>("")
    val esPersonal = MutableLiveData<Boolean>(false)

    init {
        verificarSiEsPersonal()
        cargarTratamientos()
        cargarPagos()
    }

    private fun verificarSiEsPersonal() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            esPersonal.value = doc.getBoolean("staff") == true
        }
    }

    fun cargarTratamientos() {
        loading.value = true
        val userId = auth.currentUser?.uid ?: return
        val query = if (esPersonal.value == true) {
            db.collection("tratamientos").orderBy("fecha", Query.Direction.DESCENDING)
        } else {
            db.collection("tratamientos").whereEqualTo("pacienteId", userId).orderBy("fecha", Query.Direction.DESCENDING)
        }

        query.get().addOnSuccessListener { snapshot ->
            val lista = snapshot.toObjects(Tratamiento::class.java)
            tratamientos.value = lista
            loading.value = false
        }.addOnFailureListener { e ->
            error.value = "Error al cargar tratamientos: ${e.message}"
            loading.value = false
        }
    }

    fun cargarPagos() {
        loading.value = true
        val userId = auth.currentUser?.uid ?: return
        val query = if (esPersonal.value == true) {
            db.collection("pagos").orderBy("fechaLimite", Query.Direction.DESCENDING)
        } else {
            db.collection("pagos").whereEqualTo("pacienteId", userId).orderBy("fechaLimite", Query.Direction.DESCENDING)
        }

        query.get().addOnSuccessListener { snapshot ->
            val lista = snapshot.toObjects(Pago::class.java)
            pagos.value = lista
            loading.value = false
        }.addOnFailureListener { e ->
            error.value = "Error al cargar pagos: ${e.message}"
            loading.value = false
        }
    }

    fun registrarTratamiento(descripcion: String, detalles: String, fecha: Long, costo: Double, pacienteId: String) {
        loading.value = true
        val tratamiento = Tratamiento(
            pacienteId = pacienteId,
            descripcion = descripcion,
            detalles = detalles,
            fecha = fecha,
            costo = costo,
            estado = "programado"
        )
        db.collection("tratamientos").add(tratamiento).addOnSuccessListener {
            cargarTratamientos()
            loading.value = false
        }.addOnFailureListener { e ->
            error.value = "Error al registrar tratamiento: ${e.message}"
            loading.value = false
        }
    }

    fun actualizarEstadoTratamiento(tratamientoId: String, nuevoEstado: String) {
        loading.value = true
        db.collection("tratamientos").document(tratamientoId).update("estado", nuevoEstado)
            .addOnSuccessListener {
                cargarTratamientos()
                loading.value = false
            }.addOnFailureListener { e ->
                error.value = "Error al actualizar tratamiento: ${e.message}"
                loading.value = false
            }
    }

    fun registrarPago(tratamientoId: String, monto: Double, saldoPendiente: Double, fechaLimite: Long, pacienteId: String) {
        loading.value = true
        val pago = Pago(
            tratamientoId = tratamientoId,
            pacienteId = pacienteId,
            monto = monto,
            saldoPendiente = saldoPendiente,
            fechaLimite = fechaLimite,
            estado = "pendiente"
        )
        db.collection("pagos").add(pago).addOnSuccessListener {
            cargarPagos()
            loading.value = false
        }.addOnFailureListener { e ->
            error.value = "Error al registrar pago: ${e.message}"
            loading.value = false
        }
    }

    fun actualizarEstadoPago(pagoId: String, nuevoEstado: String, fechaPago: Long = 0L) {
        loading.value = true
        val updates = mutableMapOf<String, Any>("estado" to nuevoEstado)
        if (nuevoEstado == "pagado") {
            updates["fechaPago"] = if (fechaPago > 0) fechaPago else System.currentTimeMillis()
            updates["saldoPendiente"] = 0.0
        }
        db.collection("pagos").document(pagoId).update(updates)
            .addOnSuccessListener {
                cargarPagos()
                loading.value = false
            }.addOnFailureListener { e ->
                error.value = "Error al actualizar pago: ${e.message}"
                loading.value = false
            }
    }

    fun limpiarError() {
        error.value = ""
    }
}
