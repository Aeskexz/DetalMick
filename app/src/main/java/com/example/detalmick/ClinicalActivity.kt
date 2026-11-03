package com.example.detalmick

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.detalmick.data.*
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.databinding.ContentClinicalBinding
import com.example.detalmick.domain.ClinicalValidation as Format
import com.example.detalmick.ui.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ClinicalActivity : AccessibleActivity() {
    private lateinit var shell: ActivityMainBinding
    private lateinit var binding: ContentClinicalBinding
    private lateinit var model: ClinicalViewModel
    private var filters = emptyList<String>()
    private var drawing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        shell = ActivityMainBinding.inflate(layoutInflater); setContentView(shell.root)
        ViewCompat.setOnApplyWindowInsetsListener(shell.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding = ContentClinicalBinding.inflate(layoutInflater, shell.content, true)
        ViewCompat.setAccessibilityHeading(binding.title, true)
        model = ViewModelProvider(this)[ClinicalViewModel::class.java]
        if (savedInstanceState == null) model.tab(intent.getStringExtra("tab") ?: "tratamientos")
        binding.tabs.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !drawing) model.tab(if (id == R.id.paymentsTab) "pagos" else "tratamientos")
        }
        binding.patientSelect.setOnClickListener { choosePatient() }
        binding.filterSelect.setOnClickListener { chooseFilter() }
        binding.create.setOnClickListener { edit(if (model.state.value?.tab == "pagos") "cargo" else "tratamiento") }
        binding.refresh.setOnClickListener { model.refresh() }
        binding.back.setOnClickListener { finish() }
        binding.patientRecord.setOnClickListener { model.state.value?.let { startActivity(Intent(this, PatientRecordActivity::class.java).putExtra("patient",it.patientId)) } }
        model.state.observe(this, ::render)
    }
    override fun onStart() { super.onStart(); model.start() }
    override fun onStop() { model.stop(); super.onStop() }

    private fun choosePatient() {
        val state = model.state.value ?: return
        if (!state.staff || state.loadingPatients || state.patients.isEmpty()) return
        val patients = state.patients
        MaterialAlertDialogBuilder(this).setTitle("Seleccionar paciente")
            .setItems(patients.map { "${it.name} · ${it.uid.takeLast(6)}" }.toTypedArray()) { _, index ->
                model.selectPatient(patients[index].uid, patients[index].name)
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun chooseFilter() {
        MaterialAlertDialogBuilder(this).setTitle("Mostrar registros")
            .setItems(filters.toTypedArray()) { _, index -> model.filter(index) }.setNegativeButton("Cancelar", null).show()
    }

    private fun render(state: ClinicalState) {
        drawing = true
        shell.connection.text = if (state.staff) "Acceso de dentista · Datos protegidos" else "Acceso de paciente · Solo tus registros"
        binding.title.text = if (state.staff) "Gestión de pacientes" else "Mis tratamientos y pagos"
        binding.subtitle.text = if (state.staff) "Selecciona un paciente para registrar tratamientos y pagos." else "Revisa tus tratamientos y lo que tienes pendiente de pago."
        binding.patientSelect.isVisible = state.authorized && state.staff
        binding.patientSelect.isEnabled = !state.loadingPatients && state.patients.isNotEmpty()
        val selected = state.patients.find { it.uid == state.patientId }
        binding.patientSelect.text = selected?.let { "Paciente: ${it.name} · ${it.uid.takeLast(6)}" } ?: "Seleccionar paciente"
        val treatments = state.tab != "pagos"
        binding.tabs.check(if (treatments) R.id.treatmentsTab else R.id.paymentsTab)
        val options = if (treatments) listOf("Todos", "Programados", "Realizados") else listOf("Todos", "Pendientes", "Pagados")
        filters = options
        binding.filterSelect.text = "Mostrar: ${filters.getOrElse(state.filter) { filters[0] }}"
        val loading = state.checking || state.loadingPatients || if (treatments) state.loadingTreatments else state.loadingPayments
        val error = state.error.ifBlank { if (treatments) state.treatmentError else state.paymentError }
        shell.loadingGroup.isVisible = loading
        shell.message.isVisible = error.isNotBlank()
        shell.message.text = error
        shell.message.setTextColor(getColor(R.color.error))
        binding.create.isVisible = state.authorized && state.staff && state.patientId.isNotEmpty() && !loading && error.isBlank()
        binding.create.text = if (treatments) "Registrar tratamiento" else "Registrar cargo"
        binding.tabs.isVisible = state.authorized && !state.reception
        binding.patientRecord.isVisible = state.authorized && !state.reception && state.patientId.isNotBlank()
        binding.filterSelect.isVisible = state.authorized && state.patientId.isNotEmpty()
        binding.records.removeAllViews()
        val recordsVisible = state.authorized && !loading && error.isBlank() && state.patientId.isNotBlank()
        binding.summary.isVisible = recordsVisible && !treatments
        if (!treatments) binding.summary.text = "Saldo pendiente: ${Format.amount(state.payments.sumOf { it.saldo })}"
        var count = 0
        if (recordsVisible && treatments) state.treatments.filter {
            state.filter == 0 || it.estado == if (state.filter == 1) "programado" else "realizado"
        }.forEach { record ->
            count++
            card(record.titulo, "${if (record.estado == "realizado") "Realizado" else "Programado"} · ${Format.displayDate(record.fecha)}") {
                treatmentDetail(record, state.staff)
            }
        }
        if (recordsVisible && !treatments) state.payments.filter {
            state.filter == 0 || it.estado == if (state.filter == 1) "pendiente" else "pagado"
        }.forEach { payment ->
            count++
            val overdue = payment.saldo > 0 && payment.fechaLimite < Format.today()
            card(payment.concepto, "${if (payment.saldo == 0L) "Pagado" else if (overdue) "Pendiente · Vencido" else "Pendiente"}\n" +
                "Monto: ${Format.amount(payment.totalCentavos)}\nAbonado: ${Format.amount(payment.abonadoCentavos)}\n" +
                "Saldo: ${Format.amount(payment.saldo)}\nFecha límite: ${Format.displayDate(payment.fechaLimite)}") { paymentDetail(payment, state.staff) }
        }
        binding.empty.isVisible = state.authorized && !loading && error.isBlank() && count == 0
        binding.empty.text = if (state.staff && state.patients.isEmpty()) "Aún no hay pacientes registrados. Los pacientes deben crear su cuenta y completar sus datos."
            else if (treatments) "No hay tratamientos para esta selección." else "No hay pagos para esta selección."
        drawing = false
    }

    private fun card(title: String, description: String, open: () -> Unit) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val card = MaterialCardView(this).apply {
            radius = 16 * resources.displayMetrics.density; cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = padding }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding) }
        content.addView(TextView(this).apply { text = title; textSize = 20f; setTextColor(getColor(R.color.ink)); ViewCompat.setAccessibilityHeading(this, true) })
        content.addView(TextView(this).apply { text = description; textSize = 16f; setTextColor(getColor(R.color.muted)); setPadding(0, padding / 2, 0, padding / 2) })
        content.addView(MaterialButton(this).apply { text = "Ver detalle"; setOnClickListener { open() } })
        card.addView(content); binding.records.addView(card)
    }
    private fun treatmentDetail(record: Treatment, staff: Boolean) {
        val dialog = MaterialAlertDialogBuilder(this).setTitle(record.titulo)
            .setMessage("${if (record.estado == "realizado") "Tratamiento realizado" else "Tratamiento programado"}\nFecha: ${Format.displayDate(record.fecha)}\nProfesional (ID): ${record.odontologo}\n\n${record.descripcion}")
            .setNegativeButton("Cerrar", null)
        if (staff) dialog.setPositiveButton("Actualizar tratamiento") { _, _ -> edit("tratamiento", record.id) }
        dialog.show()
    }
    private fun paymentDetail(payment: Payment, staff: Boolean) {
        val history = if (payment.abonos.isEmpty()) "Aún no se han registrado abonos." else payment.abonos.joinToString("\n") {
            "${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ROOT).format(java.util.Date(it.at))} · ${Format.amount(it.cents)}"
        }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(payment.concepto)
            .setMessage("Monto: ${Format.amount(payment.totalCentavos)}\nAbonado: ${Format.amount(payment.abonadoCentavos)}\nSaldo: ${Format.amount(payment.saldo)}\n" +
                "Estado: ${if (payment.saldo == 0L) "Pagado" else "Pendiente"}\nFecha límite: ${Format.displayDate(payment.fechaLimite)}\n\nHistorial de abonos\n$history")
            .setNegativeButton("Cerrar", null)
        if (staff && payment.saldo > 0) dialog.setPositiveButton("Registrar abono") { _, _ -> edit("abono", payment.id) }
        if (!staff && payment.saldo > 0) dialog.setPositiveButton("Consultar cómo pagar") { _, _ -> startActivity(Intent(this, ClinicActivity::class.java)) }
        dialog.setNeutralButton("Ver factura") { _, _ -> showInvoice(payment) }
        dialog.show()
    }
    private fun showInvoice(payment: Payment) {
        val issue = if(payment.creadoEn>0) java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT).format(java.util.Date(payment.creadoEn)) else "Sin fecha registrada"
        val invoice = "FACTURA BÁSICA - DetalMick\nNúmero: ${payment.id}\nPaciente: ${model.state.value?.patientName}\nCita: ${payment.citaId.ifBlank { "Registro anterior sin cita vinculada" }}\nEmisión: $issue\nConcepto: ${payment.concepto}\nTotal: ${Format.amount(payment.totalCentavos)}\nAbonado: ${Format.amount(payment.abonadoCentavos)}\nSaldo: ${Format.amount(payment.saldo)}\nEstado: ${payment.estado}\nFecha límite: ${Format.displayDate(payment.fechaLimite)}\n\nComprobante académico; no es un documento tributario electrónico."
        MaterialAlertDialogBuilder(this).setTitle("Factura de la cita").setMessage(invoice).setNegativeButton("Cerrar",null)
            .setPositiveButton("Compartir") { _, _ -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,invoice),"Compartir factura")) }.show()
    }
    private fun edit(mode: String, id: String = "") {
        val state = model.state.value ?: return
        if (!state.staff || !state.authorized || state.patientId.isBlank()) return
        startActivity(Intent(this, ClinicalEditorActivity::class.java).putExtra("patient", state.patientId)
            .putExtra("patientName", state.patientName).putExtra("mode", mode).putExtra("record", id)
            .putExtra("operation", ClinicalRepository().newId()))
    }
}
