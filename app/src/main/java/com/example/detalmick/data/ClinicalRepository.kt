package com.example.detalmick.data

import com.example.detalmick.domain.ClinicalValidation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.*
import java.util.concurrent.TimeUnit

class ClinicalRepository {
    companion object {
        // Serialize local writes to the same ledger; server transactions still resolve other devices.
        private val paymentQueue = mutableMapOf<String, Task<Void>>()
    }
    private val database get() = FirebaseProvider.database
    private val uid get() = FirebaseProvider.auth.currentUser?.uid ?: error("Inicia sesión para continuar.")
    fun role() = timed(database.getReference("roles").child(uid).get())
    fun patients(): DatabaseReference = database.getReference("usuarios")
    fun treatments(patient: String): DatabaseReference = database.getReference("tratamientos").child(patient)
    fun payments(patient: String): DatabaseReference = database.getReference("pagos").child(patient)
    fun newId(): String = database.reference.push().key!!
    private fun <T> timed(task: Task<T>): Task<T> = Tasks.withTimeout(task, 15, TimeUnit.SECONDS)

    fun saveTreatment(patient: String, id: String, title: String, description: String, state: String, date: String, existing: Boolean): Task<Void> {
        val source = TaskCompletionSource<Void>()
        val author = uid
        treatments(patient).child(id).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (existing && data.value == null) return Transaction.success(data)
                if (data.value == null) {
                    data.child("creadoEn").value = ServerValue.TIMESTAMP
                    data.child("registradoPor").value = author
                }
                data.child("titulo").value = title; data.child("descripcion").value = description
                data.child("estado").value = state; data.child("fecha").value = date
                data.child("actualizadoEn").value = ServerValue.TIMESTAMP
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) source.trySetException(error.toException())
                else if (!committed || snapshot?.exists() != true) source.trySetException(IllegalStateException("No se confirmó el tratamiento."))
                else source.trySetResult(null)
            }
        }, false)
        return timed(source.task)
    }

    fun createCharge(patient: String, id: String, concept: String, total: Long, due: String, citaId: String): Task<Void> {
        val source = TaskCompletionSource<Void>()
        val author = uid
        val values = mapOf("concepto" to concept, "totalCentavos" to total, "abonadoCentavos" to 0L, "citaId" to citaId,
            "fechaLimite" to due, "estado" to "pendiente", "creadoEn" to ServerValue.TIMESTAMP,
            "actualizadoEn" to ServerValue.TIMESTAMP, "registradoPor" to author)
        payments(patient).child(id).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (data.value == null) data.value = values
                else if (data.child("concepto").value != concept || data.child("totalCentavos").value != total || data.child("fechaLimite").value != due || data.child("citaId").value != citaId) return Transaction.abort()
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) source.trySetException(error.toException())
                else if (!committed) source.trySetException(IllegalStateException("No se confirmó el cargo."))
                else source.trySetResult(null)
            }
        }, false)
        return timed(source.task)
    }

    fun recordPayment(patient: String, paymentId: String, operationId: String, amount: Long): Task<Void> {
        val author = uid
        val key = "$author:$patient:$paymentId"
        synchronized(paymentQueue) {
            val previous = paymentQueue[key] ?: Tasks.forResult<Void>(null)
            val next = previous.continueWithTask { recordPaymentTransaction(patient, paymentId, operationId, amount, author) }
            paymentQueue[key] = next
            next.addOnCompleteListener { synchronized(paymentQueue) { if (paymentQueue[key] === next) paymentQueue.remove(key) } }
            return next
        }
    }

    private fun recordPaymentTransaction(patient: String, paymentId: String, operationId: String, amount: Long, author: String): Task<Void> {
        val source = TaskCompletionSource<Void>()
        var reason = "No se pudo confirmar el abono."
        payments(patient).child(paymentId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                // An empty local cache must first be compared with the server.
                if (data.value == null) return Transaction.success(data)
                if (data.child("abonos").child(operationId).value != null) {
                    return if (data.child("abonos").child(operationId).child("montoCentavos").value == amount) Transaction.success(data) else Transaction.abort()
                }
                val total = data.child("totalCentavos").value as? Long
                val paid = data.child("abonadoCentavos").value as? Long
                if (total == null || paid == null || amount <= 0 || amount > total - paid) {
                    reason = "El abono supera el saldo actual o el registro no es válido. Actualiza la consulta."
                    return Transaction.abort()
                }
                data.child("abonadoCentavos").value = paid + amount
                data.child("estado").value = ClinicalValidation.status(total, paid + amount)
                data.child("actualizadoEn").value = ServerValue.TIMESTAMP
                data.child("abonos").child(operationId).value = mapOf("montoCentavos" to amount,
                    "creadoEn" to ServerValue.TIMESTAMP, "registradoPor" to author)
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) source.trySetException(error.toException())
                else if (!committed || snapshot?.exists() != true) source.trySetException(IllegalStateException(reason))
                else source.trySetResult(null)
            }
        }, false)
        return timed(source.task)
    }
}
