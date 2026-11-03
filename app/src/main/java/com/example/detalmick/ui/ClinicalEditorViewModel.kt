package com.example.detalmick.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.detalmick.data.*
import com.example.detalmick.domain.ClinicalValidation as Validate
import java.util.concurrent.TimeoutException

data class EditorState(
    val busy: Boolean = true, val ready: Boolean = false, val saved: Boolean = false,
    val error: String = "", val patientName: String = "", val title: String = "",
    val description: String = "", val status: String = "programado",
    val date: String = Validate.today(), val balance: Long = 0, val citas: List<Cita> = emptyList()
)

class ClinicalEditorViewModel(val patient: String, val mode: String, val record: String, val operation: String) : ViewModel() {
    private val repository = ClinicalRepository()
    private val mutable = MutableLiveData(EditorState())
    val state: LiveData<EditorState> = mutable
    private val current get() = mutable.value!!
    private var alive = true
    init { load() }
    private fun load() {
        if (patient.isBlank() || mode !in listOf("tratamiento", "cargo", "abono") || (mode == "abono" && record.isBlank())) {
            fail("No se pudo abrir el formulario."); return
        }
        repository.role().addOnCompleteListener roleComplete@ {
            if (!alive) return@roleComplete
            if (!it.isSuccessful || !AccessControl.staff(it.result) || (mode == "tratamiento" && !AccessControl.clinical(it.result))) {
                AccessControl.denied("edicion_clinica_denegada")
                fail("Solo personal autorizado puede modificar estos registros."); return@roleComplete
            }
            repository.patients().child(patient).get().addOnCompleteListener patientComplete@ { result ->
                if (!alive) return@patientComplete
                val name = if (result.isSuccessful) result.result.child("nombre").value as? String else null
                if (name.isNullOrBlank()) { fail("No se pudo consultar al paciente. Revisa la conexión y vuelve a intentar."); return@patientComplete }
                mutable.value = current.copy(patientName = name)
                when {
                    mode == "abono" -> repository.payments(patient).child(record).get().addOnCompleteListener { payment ->
                        if (alive) runCatching {
                            check(payment.isSuccessful)
                            Payment.decode(record, payment.result.value)
                        }.onSuccess { mutable.value = current.copy(busy = false, ready = true, title = it.concepto, balance = it.saldo) }
                            .onFailure { fail("No se pudo cargar el pago. Actualiza la consulta e intenta de nuevo.") }
                    }
                    mode == "tratamiento" && record.isNotEmpty() -> repository.treatments(patient).child(record).get().addOnCompleteListener { treatment ->
                        if (alive) runCatching {
                            check(treatment.isSuccessful)
                            Treatment.decode(record, treatment.result.value)
                        }.onSuccess { mutable.value = current.copy(busy = false, ready = true, title = it.titulo, description = it.descripcion, status = it.estado, date = it.fecha) }
                            .onFailure { fail("No se pudo cargar el tratamiento. Actualiza la consulta e intenta de nuevo.") }
                    }
                    mode == "cargo" -> FirebaseProvider.database.getReference("citas/$patient").get().addOnCompleteListener { appointments ->
                        if(alive) {
                            val citas = if(appointments.isSuccessful) appointments.result.children.mapNotNull { runCatching { it.getValue(Cita::class.java)?.copy(id=it.key.orEmpty()) }.getOrNull() }.filter { it.estado != "cancelada" } else emptyList()
                            mutable.value=current.copy(busy=false,ready=appointments.isSuccessful,citas=citas,
                                error=if(!appointments.isSuccessful) "No se pudieron consultar las citas." else if(citas.isEmpty()) "El paciente necesita una cita para generar su factura." else "")
                        }
                    }
                    else -> mutable.value = current.copy(busy = false, ready = true)
                }
            }
        }
    }

    fun submit(title: String, description: String, status: String, date: String, amount: String, citaId: String = "") {
        if (current.busy || !current.ready) return
        val cleanTitle = title.trim(); val cleanDescription = description.trim(); val cleanDate = date.trim()
        if (mode != "abono" && cleanTitle.length !in 1..120) { fail("Escribe un nombre o concepto de hasta 120 caracteres."); return }
        if (mode == "tratamiento" && (cleanDescription.length !in 1..2000 || status !in setOf("programado", "realizado"))) {
            fail("Explica el tratamiento en lenguaje sencillo y selecciona un estado válido."); return
        }
        if (mode != "abono" && !Validate.date(cleanDate)) { fail("Selecciona una fecha válida con el formato AAAA-MM-DD."); return }
        val cents = if (mode == "tratamiento") null else Validate.cents(amount)
        if (mode != "tratamiento" && cents == null) { fail("Escribe un monto mayor que cero, con un máximo de dos decimales."); return }
        if (mode == "abono" && cents!! > current.balance) { fail("El abono no puede superar el saldo de ${Validate.amount(current.balance)}."); return }
        if(mode == "cargo" && current.citas.none { it.id == citaId }) { fail("Selecciona la cita asociada a esta factura."); return }
        mutable.value = current.copy(busy = true, error = "")
        val task = when (mode) {
            "tratamiento" -> repository.saveTreatment(patient, record.ifEmpty { operation }, cleanTitle, cleanDescription, status, cleanDate, record.isNotEmpty())
            "cargo" -> repository.createCharge(patient, operation, cleanTitle, cents!!, cleanDate, citaId)
            else -> repository.recordPayment(patient, record, operation, cents!!)
        }
        task.addOnCompleteListener {
            if (!alive) return@addOnCompleteListener
            if (it.isSuccessful) mutable.value = current.copy(busy = false, saved = true)
            else fail(if (it.exception is TimeoutException)
                "Aún no se confirmó el guardado. Puede completarse al volver la conexión. Reintentar este mismo formulario no duplica el registro."
                else "No se guardó el cambio. Revisa tu conexión, el saldo actual y los permisos del dentista, y vuelve a intentar.")
        }
    }
    private fun fail(message: String) { mutable.value = current.copy(busy = false, error = message) }
    override fun onCleared() { alive = false; super.onCleared() }
}
