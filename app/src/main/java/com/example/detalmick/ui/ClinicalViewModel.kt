package com.example.detalmick.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.detalmick.data.*
import com.google.firebase.database.*

data class ClinicalState(
    val authorized: Boolean = false, val checking: Boolean = true, val staff: Boolean = false,
    val loadingPatients: Boolean = false,
    val patients: List<PatientSummary> = emptyList(), val patientId: String = "", val patientName: String = "",
    val treatments: List<Treatment> = emptyList(), val payments: List<Payment> = emptyList(),
    val loadingTreatments: Boolean = false, val loadingPayments: Boolean = false,
    val treatmentError: String = "", val paymentError: String = "", val error: String = "",
    val reception: Boolean = false, val tab: String = "tratamientos", val filter: Int = 0
)

class ClinicalViewModel : ViewModel() {
    private val repository = ClinicalRepository()
    private val mutable = MutableLiveData(ClinicalState())
    val state: LiveData<ClinicalState> = mutable
    private val current get() = mutable.value!!
    private val subscriptions = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var directory: Pair<DatabaseReference, ValueEventListener>? = null
    private var running = false
    private var epoch = 0
    private fun update(state: ClinicalState) { mutable.value = state }

    fun start() {
        if (running) return
        running = true
        val token = ++epoch
        val user = FirebaseProvider.auth.currentUser
        if (user == null) { update(ClinicalState(checking = false, error = "Tu sesión terminó. Vuelve a iniciar sesión.")); return }
        update(current.copy(checking = true, error = ""))
        repository.role().addOnCompleteListener {
            if (!running || token != epoch) return@addOnCompleteListener
            if (!it.isSuccessful) {
                update(current.copy(authorized = false, checking = false, error = "No se pudieron verificar los permisos. Revisa tu conexión y vuelve a intentar."))
            } else {
                if (it.result.child("activo").value == false) {
                    update(ClinicalState(checking = false, error = "Cuenta suspendida. Comunícate con la clínica."))
                    return@addOnCompleteListener
                }
                val staff = AccessControl.staff(it.result)
                val reception = AccessControl.reception(it.result) && !AccessControl.clinical(it.result)
                update(current.copy(authorized = true, staff = staff, reception = reception, tab = if(reception) "pagos" else current.tab, checking = false, loadingPatients = staff))
                if (staff) observePatients() else selectPatient(user.uid, "Mi información")
            }
        }
    }

    fun stop() {
        running = false; epoch++
        clearPatient()
        directory?.let { it.first.removeEventListener(it.second) }; directory = null
    }
    fun refresh() { stop(); start() }
    fun tab(tab: String) { update(current.copy(tab = if(current.reception) "pagos" else tab, filter = 0)) }
    fun filter(filter: Int) { if (current.filter != filter) update(current.copy(filter = filter)) }

    private fun observePatients() {
        val ref = repository.patients()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!running) return
                val patients = snapshot.children.mapNotNull { child ->
                    runCatching { PatientProfile.fromValue(child.value)?.let { PatientSummary(child.key!!, it.nombre) } }.getOrNull()
                }.sortedBy { it.name.lowercase() }
                update(current.copy(patients = patients, loadingPatients = false, error = ""))
                val chosen = patients.find { it.uid == current.patientId } ?: patients.firstOrNull()
                if (chosen == null) {
                    clearPatient()
                    update(current.copy(patientId = "", patientName = "", treatments = emptyList(), payments = emptyList(), loadingTreatments = false, loadingPayments = false))
                } else selectPatient(chosen.uid, chosen.name)
            }
            override fun onCancelled(error: DatabaseError) {
                clearPatient()
                update(current.copy(patients = emptyList(), loadingPatients = false, patientId = "", treatments = emptyList(), payments = emptyList(),
                    error = "No se pudo cargar la lista de pacientes. Verifica la conexión y los permisos del dentista."))
            }
        }
        directory = ref to listener; ref.addValueEventListener(listener)
    }

    fun selectPatient(uid: String, name: String) {
        if (!current.authorized) return
        if (!current.staff && uid != FirebaseProvider.auth.currentUser?.uid) return
        if (uid == current.patientId && subscriptions.isNotEmpty()) return
        clearPatient()
        update(current.copy(patientId = uid, patientName = name, treatments = emptyList(), payments = emptyList(),
            loadingTreatments = true, loadingPayments = true, treatmentError = "", paymentError = ""))
        val treatmentRef = repository.treatments(uid)
        val treatmentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!running || current.patientId != uid) return
                runCatching { snapshot.children.map { Treatment.decode(it.key!!, it.value) } }
                    .onSuccess { update(current.copy(treatments = it.sortedByDescending(Treatment::fecha), loadingTreatments = false, treatmentError = "")) }
                    .onFailure { update(current.copy(treatments = emptyList(), loadingTreatments = false, treatmentError = "Hay un tratamiento con datos incompletos. Pide a la clínica que revise el registro.")) }
            }
            override fun onCancelled(error: DatabaseError) {
                update(current.copy(treatments = emptyList(), loadingTreatments = false, treatmentError = "No pudimos consultar tus tratamientos. Revisa tu conexión y vuelve a intentar."))
            }
        }
        val paymentRef = repository.payments(uid)
        val paymentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!running || current.patientId != uid) return
                runCatching { snapshot.children.map { Payment.decode(it.key!!, it.value) } }
                    .onSuccess { update(current.copy(payments = it.sortedBy(Payment::fechaLimite), loadingPayments = false, paymentError = "")) }
                    .onFailure { update(current.copy(payments = emptyList(), loadingPayments = false, paymentError = "Hay un pago con datos incompletos. Pide a la clínica que revise sus montos y abonos.")) }
            }
            override fun onCancelled(error: DatabaseError) {
                update(current.copy(payments = emptyList(), loadingPayments = false, paymentError = "No pudimos consultar tus pagos. Revisa tu conexión y vuelve a intentar."))
            }
        }
        subscriptions += treatmentRef to treatmentListener
        subscriptions += paymentRef to paymentListener
        if (!current.reception) treatmentRef.addValueEventListener(treatmentListener)
        else update(current.copy(loadingTreatments=false))
        paymentRef.addValueEventListener(paymentListener)
    }
    private fun clearPatient() { subscriptions.forEach { it.first.removeEventListener(it.second) }; subscriptions.clear() }
    override fun onCleared() { stop(); super.onCleared() }
}
