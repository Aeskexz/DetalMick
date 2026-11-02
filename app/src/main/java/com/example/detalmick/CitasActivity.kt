package com.example.detalmick

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.detalmick.data.Cita
import com.example.detalmick.data.Opcion
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.databinding.ContentAgendarCitaBinding
import com.example.detalmick.databinding.ContentCitasListaBinding
import com.example.detalmick.databinding.ItemCitaBinding
import com.example.detalmick.ui.CitaViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.example.detalmick.ui.CitasState
import com.example.detalmick.domain.CitaValidation
class CitasActivity : AppCompatActivity() {
    private enum class Vista { LISTA, AGENDAR }

    private lateinit var shell: ActivityMainBinding
    private lateinit var model: CitaViewModel
    private var staff = false
    private var nombre = ""
    private var vista = Vista.LISTA
    private var lista: ContentCitasListaBinding? = null
    private var form: ContentAgendarCitaBinding? = null

    private var paso = 1
    private var servicio: Opcion? = null
    private var dentista: Opcion? = null
    private var fecha = ""
    private var horario = ""
    private var editando: Cita? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        shell = ActivityMainBinding.inflate(layoutInflater); setContentView(shell.root)
        ViewCompat.setOnApplyWindowInsetsListener(shell.root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        shell.connection.isVisible = false
        staff = intent.getBooleanExtra("staff", false)
        nombre = intent.getStringExtra("nombre").orEmpty()
        model = ViewModelProvider(this)[CitaViewModel::class.java]
        model.state.observe(this, ::render)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = atras()
        })
        mostrarLista()
        model.cargarCitas(staff)
    }

    private fun render(s: CitasState) {
        shell.loadingGroup.isVisible = s.loading
        s.aviso?.let { Snackbar.make(shell.root, it.texto, Snackbar.LENGTH_LONG).show(); model.limpiarAviso() }
        if (s.operacionOk && vista == Vista.AGENDAR) mostrarLista()
        lista?.let { pintarLista(it, s) }
        form?.let { pintarFormulario(it, s) }
    }

    private fun atras() {
        when {
            vista == Vista.LISTA -> finish()
            paso == 1 || (paso == 2 && editando != null) -> mostrarLista()
            else -> { paso--; mostrarPaso() }
        }
    }

    // ---------- LISTA ----------
    private fun mostrarLista() {
        vista = Vista.LISTA; form = null
        shell.content.removeAllViews()
        val b = ContentCitasListaBinding.inflate(layoutInflater, shell.content, true)
        lista = b
        b.tvTitulo.text = if (staff) "Agenda del dentista" else "Mis citas"
        b.btnAgendarNuevaCita.isVisible = !staff
        b.btnAgendarNuevaCita.setOnClickListener { iniciarAgendar() }
        b.btnReintentar.setOnClickListener { model.cargarCitas(staff) }
        b.btnVolver.setOnClickListener { finish() }
        model.state.value?.let { pintarLista(b, it) }
    }

    private fun pintarLista(b: ContentCitasListaBinding, s: CitasState) {
        val proxima = s.citas.firstOrNull { CitaValidation.esActiva(it) }
        b.tvProximaCita.text = proxima?.let { "${it.servicioNombre}\n${it.fecha} · ${it.horario}\n" + quien(it) }
            ?: if (staff) "No hay citas próximas en tu agenda." else "No tienes citas próximas."
        b.panelError.isVisible = s.errorCarga != null
        b.tvError.text = s.errorCarga
        b.tvEstadoVacio.isVisible = s.citasCargadas && s.citas.isEmpty() && s.errorCarga == null
        b.listaCitas.removeAllViews()
        s.citas.forEach { c ->
            val item = ItemCitaBinding.inflate(layoutInflater, b.listaCitas, false)
            item.tvTitulo.text = c.servicioNombre
            item.tvDetalle.text = "${c.fecha} · ${c.horario}\n${quien(c)}"
            item.tvEstado.text = c.estado.replaceFirstChar { it.uppercase() }
            item.filaAcciones.isVisible = CitaValidation.esActiva(c)
            item.btnReprogramar.setOnClickListener { iniciarReprogramar(c) }
            item.btnCancelar.setOnClickListener { confirmarCancelacion(c) }
            b.listaCitas.addView(item.root)
        }
    }

    private fun quien(c: Cita) = if (staff) "Paciente: ${c.pacienteNombre}" else "Dentista: ${c.dentistaNombre}"

    private fun confirmarCancelacion(c: Cita) {
        MaterialAlertDialogBuilder(this).setTitle("Cancelar cita")
            .setMessage("¿Cancelar ${c.servicioNombre} del ${c.fecha} a las ${c.horario.take(5)}?")
            .setNegativeButton("Conservar", null)
            .setPositiveButton("Cancelar cita") { _, _ -> model.cancelar(c) }.show()
    }

    // ---------- FORMULARIO (3 pasos) ----------
    private fun iniciarAgendar() {
        editando = null; servicio = null; dentista = null; fecha = ""; horario = ""; paso = 1
        mostrarFormulario()
    }

    private fun iniciarReprogramar(c: Cita) {
        editando = c; servicio = Opcion(c.servicioId, c.servicioNombre); dentista = Opcion(c.dentistaId, c.dentistaNombre)
        fecha = ""; horario = ""; paso = 2
        mostrarFormulario()
    }

    private fun mostrarFormulario() {
        vista = Vista.AGENDAR; lista = null
        shell.content.removeAllViews()
        val b = ContentAgendarCitaBinding.inflate(layoutInflater, shell.content, true)
        form = b
        b.btnReintentarCatalogo.setOnClickListener { model.cargarCatalogo() }
        b.btnSiguiente1.setOnClickListener {
            val st = model.state.value ?: return@setOnClickListener
            val sv = st.servicios.getOrNull(b.spinnerServicios.selectedItemPosition)
            val dn = st.dentistas.getOrNull(b.spinnerDentistas.selectedItemPosition)
            if (sv == null || dn == null) { Snackbar.make(shell.root, "Elige un servicio y un dentista.", Snackbar.LENGTH_SHORT).show(); return@setOnClickListener }
            servicio = sv; dentista = dn; paso = 2; mostrarPaso()
        }
        b.btnElegirFecha.setOnClickListener { elegirFecha() }
        b.btnSiguiente2.setOnClickListener {
            if (fecha.isBlank() || horario.isBlank()) Snackbar.make(shell.root, "Elige fecha y horario.", Snackbar.LENGTH_SHORT).show()
            else { paso = 3; mostrarPaso() }
        }
        b.btnAtras2.setOnClickListener { atras() }
        b.btnAtras3.setOnClickListener { atras() }
        b.btnConfirmarCita.setOnClickListener {
            val sv = servicio ?: return@setOnClickListener
            val dn = dentista ?: return@setOnClickListener
            val c = editando
            if (c == null) model.agendar(nombre, sv, dn, fecha, horario) else model.reprogramar(c, fecha, horario)
        }
        mostrarPaso()
        if (editando == null && model.state.value?.catalogoCargado != true) model.cargarCatalogo()
    }

    private fun mostrarPaso() {
        val b = form ?: return
        b.paso1.isVisible = paso == 1; b.paso2.isVisible = paso == 2; b.paso3.isVisible = paso == 3
        b.tvPaso.text = if (editando != null) "Reprogramar cita · paso ${paso - 1} de 2" else "Paso $paso de 3"
        b.btnConfirmarCita.text = if (editando != null) "Confirmar nueva fecha" else "Confirmar y guardar cita"
        if (paso == 2) b.btnElegirFecha.text = if (fecha.isBlank()) "Elegir fecha" else fecha
        if (paso == 3) b.tvResumen.text = "Servicio: ${servicio?.nombre}\nDentista: ${dentista?.nombre}\nFecha: $fecha\nHorario: $horario" +
                (editando?.let { "\n\nAntes: ${it.fecha} · ${it.horario}" } ?: "")
        model.state.value?.let { pintarFormulario(b, it) }
    }

    private fun pintarFormulario(b: ContentAgendarCitaBinding, s: CitasState) {
        if (paso == 1) {
            if (b.spinnerServicios.adapter?.count != s.servicios.size)
                b.spinnerServicios.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, s.servicios.map { it.nombre })
            if (b.spinnerDentistas.adapter?.count != s.dentistas.size)
                b.spinnerDentistas.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, s.dentistas.map { it.nombre })
            val vacio = s.catalogoCargado && (s.servicios.isEmpty() || s.dentistas.isEmpty())
            b.tvCatalogoVacio.isVisible = vacio
            b.btnReintentarCatalogo.isVisible = !s.catalogoCargado && !s.loading
            b.btnSiguiente1.isEnabled = s.catalogoCargado && !vacio && !s.loading
        }
        if (paso == 2) pintarHorarios(b, s)
        b.btnConfirmarCita.isEnabled = !s.loading
    }

    private fun elegirFecha() {
        val limites = CalendarConstraints.Builder()
            .setStart(MaterialDatePicker.todayInUtcMilliseconds())
            .setValidator(DateValidatorPointForward.now()).build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Fecha de la cita").setCalendarConstraints(limites).build()
        picker.addOnPositiveButtonClickListener { utc ->
            fecha = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utc))
            horario = ""
            form?.btnElegirFecha?.text = fecha
            dentista?.let { model.cargarOcupados(it.id, fecha) }
        }
        picker.show(supportFragmentManager, "fecha")
    }

    private fun pintarHorarios(b: ContentAgendarCitaBinding, s: CitasState) {
        b.chipsHorarios.removeAllViews()
        b.tvSinFecha.isVisible = fecha.isBlank()
        if (fecha.isBlank()) return
        CitaValidation.HORARIOS.forEach { h ->
            val ocupado = CitaValidation.slotKey(h) in s.ocupados
            val valido = CitaValidation.validarFechaHorario(fecha, h)
            b.chipsHorarios.addView(Chip(this).apply {
                text = h.take(5) + if (ocupado) " (ocupado)" else ""
                isCheckable = true
                isEnabled = !s.loading && !ocupado && valido
                isChecked = h == horario
                setOnClickListener { horario = h }
            })
        }
    }
}