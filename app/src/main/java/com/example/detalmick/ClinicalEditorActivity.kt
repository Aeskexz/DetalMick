package com.example.detalmick

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.databinding.ContentClinicalEditorBinding
import com.example.detalmick.ui.ClinicalEditorViewModel
import com.example.detalmick.ui.EditorState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import java.util.Locale

class ClinicalEditorActivity : AccessibleActivity() {
    private lateinit var shell: ActivityMainBinding
    private lateinit var binding: ContentClinicalEditorBinding
    private lateinit var model: ClinicalEditorViewModel
    private var initialized = false
    private var initialValues = emptyList<String>()
    private var treatmentStatus = "programado"
    private var selectedAppointment = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        shell = ActivityMainBinding.inflate(layoutInflater); setContentView(shell.root)
        ViewCompat.setOnApplyWindowInsetsListener(shell.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding = ContentClinicalEditorBinding.inflate(layoutInflater, shell.content, true)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ClinicalEditorViewModel(
                intent.getStringExtra("patient").orEmpty(), intent.getStringExtra("mode").orEmpty(),
                intent.getStringExtra("record").orEmpty(), intent.getStringExtra("operation").orEmpty()) as T
        })[ClinicalEditorViewModel::class.java]
        selectedAppointment = savedInstanceState?.getString("selectedAppointment").orEmpty()
        binding.status.setOnClickListener { chooseStatus() }
        binding.appointment.setOnClickListener {
            val citas=model.state.value?.citas.orEmpty()
            MaterialAlertDialogBuilder(this).setTitle("Cita de la factura").setItems(citas.map { "${it.fecha} ${it.horario.take(5)} · ${it.servicioNombre}" }.toTypedArray()) { _,index ->
                selectedAppointment=citas[index].id; binding.appointment.text="Cita: ${citas[index].fecha} ${citas[index].horario.take(5)}"
            }.show()
        }
        binding.dateInputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        binding.dateInputLayout.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar)
        binding.dateInputLayout.endIconContentDescription = "Elegir fecha"
        binding.dateInputLayout.setEndIconOnClickListener {
            val now = Calendar.getInstance()
            val parts = binding.dateInput.text.toString().split('-').mapNotNull(String::toIntOrNull)
            DatePickerDialog(this, { _, y, m, d -> binding.dateInput.setText(String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)) },
                parts.getOrNull(0) ?: now.get(Calendar.YEAR), (parts.getOrNull(1)?.minus(1) ?: now.get(Calendar.MONTH)).coerceIn(0, 11),
                (parts.getOrNull(2) ?: now.get(Calendar.DAY_OF_MONTH)).coerceIn(1, 31)).show()
        }
        binding.save.setOnClickListener { model.submit(binding.titleInput.text.toString(), binding.descriptionInput.text.toString(),
            if (binding.status.text.toString() == "Realizado") "realizado" else "programado", binding.dateInput.text.toString(), binding.amountInput.text.toString(), selectedAppointment) }
        binding.cancel.setOnClickListener { cancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { cancel() } })
        model.state.observe(this, ::render)
    }

    private fun render(state: EditorState) {
        if (state.saved) { Toast.makeText(this, "Registro guardado correctamente.", Toast.LENGTH_LONG).show(); finish(); return }
        shell.connection.text = "Solo dentistas autorizados pueden guardar cambios"
        shell.loadingGroup.isVisible = state.busy
        shell.message.isVisible = state.error.isNotBlank()
        shell.message.text = state.error
        shell.message.setTextColor(getColor(R.color.error))
        binding.title.text = when (model.mode) { "cargo" -> "Registrar cargo"; "abono" -> "Registrar abono"; else -> if (model.record.isEmpty()) "Registrar tratamiento" else "Actualizar tratamiento" }
        binding.patient.text = "Paciente: ${state.patientName}"
        binding.appointment.isVisible = model.mode == "cargo"
        binding.descriptionInputLayout.isVisible = model.mode == "tratamiento"
        binding.status.isVisible = model.mode == "tratamiento"
        binding.titleInputLayout.isVisible = model.mode != "abono"
        binding.dateInputLayout.isVisible = model.mode != "abono"
        binding.amountInputLayout.isVisible = model.mode != "tratamiento"
        binding.titleInputLayout.hint = if (model.mode == "cargo") "Concepto del cargo" else "Nombre del tratamiento"
        binding.dateInputLayout.hint = if (model.mode == "cargo") "Fecha límite (AAAA-MM-DD)" else "Fecha del tratamiento (AAAA-MM-DD)"
        binding.amountInputLayout.hint = if (model.mode == "abono") "Ingresa el monto recibido" else "Ingresa el monto total del cargo"
        binding.help.text = when (model.mode) {
            "abono" -> "${state.title}\nSaldo actual: ${com.example.detalmick.domain.ClinicalValidation.amount(state.balance)}\nRegistra solo el importe recibido. El saldo se actualiza al confirmar."
            "cargo" -> "Registra el monto total y la fecha límite. Después podrás registrar uno o varios abonos."
            else -> "Explica al paciente qué se realizó o qué está programado, con palabras sencillas."
        }
        if (state.ready && !initialized) {
            binding.titleInput.setText(state.title); binding.descriptionInput.setText(state.description)
            binding.dateInput.setText(state.date); treatmentStatus = state.status
            binding.status.text = if (state.status == "realizado") "Realizado" else "Programado"
            initialValues = fields(); initialized = true
        }
        enable(binding.root, !state.busy)
        binding.save.isEnabled = state.ready && !state.busy
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("selectedAppointment",selectedAppointment) }
    private fun enable(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (index in 0 until view.childCount) enable(view.getChildAt(index), enabled)
    }
    private fun fields() = listOf(binding.titleInput.text.toString(), binding.descriptionInput.text.toString(),
        binding.dateInput.text.toString(), binding.amountInput.text.toString(), binding.status.text.toString())
    private fun chooseStatus() {
        MaterialAlertDialogBuilder(this).setTitle("Estado del tratamiento")
            .setItems(arrayOf("Programado", "Realizado")) { _, index ->
                treatmentStatus = if (index == 0) "programado" else "realizado"
                binding.status.text = if (index == 0) "Programado" else "Realizado"
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun cancel() {
        if (model.state.value?.busy == true) return
        if (!initialized || fields() == initialValues) { finish(); return }
        MaterialAlertDialogBuilder(this).setTitle(R.string.unsaved_title).setMessage("Si sales, se descartarán los datos que no has guardado.")
            .setNegativeButton(R.string.keep_editing, null).setPositiveButton(R.string.discard) { _, _ -> finish() }.show()
    }
}
