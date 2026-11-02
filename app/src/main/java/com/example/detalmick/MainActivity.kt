package com.example.detalmick

import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.databinding.ContentAuthBinding
import com.example.detalmick.databinding.ContentHomeBinding
import com.example.detalmick.databinding.ContentProfileBinding
import com.example.detalmick.models.Pago
import com.example.detalmick.models.Tratamiento
import com.example.detalmick.ui.AccountState
import com.example.detalmick.ui.AccountViewModel
import com.example.detalmick.ui.HistorialViewModel
import com.example.detalmick.ui.Screen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var model: AccountViewModel
    private lateinit var historialModel: HistorialViewModel
    private var renderedScreen: Screen? = null
    private var lastMessage = ""
    private var emailDraft = ""
    private var authForm: ContentAuthBinding? = null
    private var home: ContentHomeBinding? = null
    private var profileForm: ContentProfileBinding? = null
    private var currentDashboardView: View? = null
    private var tratamientoSeleccionado: Tratamiento? = null
    private var fechaTratamientoSeleccionada: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        model = ViewModelProvider(this)[AccountViewModel::class.java]
        historialModel = ViewModelProvider(this)[HistorialViewModel::class.java]
        model.state.observe(this, ::render)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> showDashboardContent(R.layout.content_dashboard_inicio)
                R.id.nav_agendar -> showDashboardContent(R.layout.content_dashboard_agendar)
                R.id.nav_mis_citas -> showDashboardContent(R.layout.content_dashboard_mis_citas)
                R.id.nav_servicios -> showDashboardContent(R.layout.content_dashboard_servicios)
                R.id.nav_historial -> showHistorial()
                else -> false
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = model.state.value ?: return
                if (state.busy) return
                when (state.screen) {
                    Screen.REGISTER, Screen.RESET -> leaveAuth(Screen.LOGIN)
                    Screen.PROFILE -> if (!state.profileSaved) confirmLogout() else leaveProfile()
                    else -> finish()
                }
            }
        })
    }

    private fun showDashboardContent(layoutId: Int): Boolean {
        if (currentDashboardView?.id == layoutId) return false
        binding.content.removeAllViews()
        currentDashboardView = View.inflate(this, layoutId, binding.content)
        return true
    }

    private fun showHistorial() {
        binding.content.removeAllViews()
        currentDashboardView = View.inflate(this, R.layout.content_dashboard_historial, binding.content)

        val contentHistorial = currentDashboardView?.findViewById<FrameLayout>(R.id.content_historial) ?: return
        val personalActions = currentDashboardView?.findViewById<LinearLayout>(R.id.personal_actions)
        val btnTabTratamientos = currentDashboardView?.findViewById<Button>(R.id.btn_tab_tratamientos)
        val btnTabPagos = currentDashboardView?.findViewById<Button>(R.id.btn_tab_pagos)
        val btnRegistrarTratamiento = currentDashboardView?.findViewById<Button>(R.id.btn_registrar_tratamiento)
        val btnActualizarPago = currentDashboardView?.findViewById<Button>(R.id.btn_actualizar_pago)

        historialModel.esPersonal.observe(this) { esPersonal ->
            personalActions?.isVisible = esPersonal
        }

        btnTabTratamientos?.setOnClickListener {
            mostrarTratamientos(contentHistorial)
        }

        btnTabPagos?.setOnClickListener {
            mostrarPagos(contentHistorial)
        }

        btnRegistrarTratamiento?.setOnClickListener {
            mostrarRegistroTratamiento(contentHistorial)
        }

        btnActualizarPago?.setOnClickListener {
            mostrarActualizarPago(contentHistorial)
        }

        mostrarTratamientos(contentHistorial)
    }

    private fun mostrarTratamientos(container: FrameLayout) {
        container.removeAllViews()
        val view = View.inflate(this, R.layout.content_lista_tratamientos, container)
        val listaTratamientos = view.findViewById<LinearLayout>(R.id.tratamientos_list)
        val emptyMessage = view.findViewById<TextView>(R.id.empty_message)

        historialModel.tratamientos.observe(this) { tratamientos ->
            listaTratamientos.removeAllViews()
            if (tratamientos.isEmpty()) {
                emptyMessage.isVisible = true
            } else {
                emptyMessage.isVisible = false
                for (tratamiento in tratamientos) {
                    val itemView = View.inflate(this, R.layout.item_tratamiento, null)
                    actualizarVistaTratamiento(itemView, tratamiento)
                    itemView.findViewById<Button>(R.id.btn_detalle_tratamiento).setOnClickListener {
                        tratamientoSeleccionado = tratamiento
                        mostrarDetalleTratamiento(container)
                    }
                    listaTratamientos.addView(itemView)
                }
            }
        }
    }

    private fun actualizarVistaTratamiento(view: View, tratamiento: Tratamiento) {
        view.findViewById<TextView>(R.id.tratamiento_descripcion).text = tratamiento.descripcion
        view.findViewById<TextView>(R.id.tratamiento_detalles).text = tratamiento.detalles
        view.findViewById<TextView>(R.id.tratamiento_fecha).text = "Fecha: ${tratamiento.getFechaFormato()}"
        view.findViewById<TextView>(R.id.tratamiento_costo).text = "$${String.format("%.2f", tratamiento.costo)}"
        view.findViewById<TextView>(R.id.tratamiento_estado).text = tratamiento.getEstadoTexto()
    }

    private fun mostrarDetalleTratamiento(container: FrameLayout) {
        val tratamiento = tratamientoSeleccionado ?: return
        container.removeAllViews()
        val view = View.inflate(this, R.layout.content_detalle_tratamiento, container)

        view.findViewById<TextView>(R.id.detalle_descripcion).text = tratamiento.descripcion
        view.findViewById<TextView>(R.id.detalle_detalles).text = tratamiento.detalles
        view.findViewById<TextView>(R.id.detalle_fecha).text = tratamiento.getFechaFormato()
        view.findViewById<TextView>(R.id.detalle_costo).text = "$${String.format("%.2f", tratamiento.costo)}"
        view.findViewById<TextView>(R.id.detalle_estado).text = tratamiento.getEstadoTexto()

        view.findViewById<Button>(R.id.btn_volver_historial).setOnClickListener {
            mostrarTratamientos(container)
        }
    }

    private fun mostrarPagos(container: FrameLayout) {
        container.removeAllViews()
        val view = View.inflate(this, R.layout.content_lista_pagos, container)
        val listaPagos = view.findViewById<LinearLayout>(R.id.pagos_list)
        val emptyMessage = view.findViewById<TextView>(R.id.empty_message)

        historialModel.pagos.observe(this) { pagos ->
            listaPagos.removeAllViews()
            if (pagos.isEmpty()) {
                emptyMessage.isVisible = true
            } else {
                emptyMessage.isVisible = false
                for (pago in pagos) {
                    val itemView = View.inflate(this, R.layout.item_pago, null)
                    actualizarVistaPago(itemView, pago)
                    val btnActualizar = itemView.findViewById<Button>(R.id.btn_actualizar_pago_item)

                    historialModel.esPersonal.observe(this) { esPersonal ->
                        btnActualizar.isVisible = esPersonal && pago.estado == "pendiente"
                    }

                    listaPagos.addView(itemView)
                }
            }
        }
    }

    private fun actualizarVistaPago(view: View, pago: Pago) {
        view.findViewById<TextView>(R.id.pago_monto).text = "$${String.format("%.2f", pago.monto)}"
        view.findViewById<TextView>(R.id.pago_estado).text = pago.getEstadoTexto()
        view.findViewById<TextView>(R.id.pago_saldo).text = "Saldo: $${String.format("%.2f", pago.saldoPendiente)}"
        view.findViewById<TextView>(R.id.pago_fecha_limite).text = "Límite: ${pago.getFechaLimiteFormato()}"
    }

    private fun mostrarRegistroTratamiento(container: FrameLayout) {
        container.removeAllViews()
        val view = View.inflate(this, R.layout.content_registro_tratamiento, container)

        val inputDescripcion = view.findViewById<EditText>(R.id.input_descripcion)
        val inputDetalles = view.findViewById<EditText>(R.id.input_detalles)
        val inputCosto = view.findViewById<EditText>(R.id.input_costo)
        val inputPacienteId = view.findViewById<EditText>(R.id.input_paciente_id)
        val btnFecha = view.findViewById<Button>(R.id.input_fecha)
        val btnGuardar = view.findViewById<Button>(R.id.btn_guardar_tratamiento)
        val btnVolver = view.findViewById<Button>(R.id.btn_volver_registro_tratamiento)

        btnFecha.setOnClickListener {
            mostrarDatePicker { millis ->
                fechaTratamientoSeleccionada = millis
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "ES"))
                btnFecha.text = "Fecha: ${sdf.format(java.util.Date(millis))}"
            }
        }

        btnGuardar.setOnClickListener {
            val descripcion = inputDescripcion.text.toString().trim()
            val detalles = inputDetalles.text.toString().trim()
            val costoStr = inputCosto.text.toString().trim()
            val pacienteId = inputPacienteId.text.toString().trim()

            if (descripcion.isEmpty() || costoStr.isEmpty() || pacienteId.isEmpty() || fechaTratamientoSeleccionada == 0L) {
                mostrarMensaje("Por favor completa todos los campos")
                return@setOnClickListener
            }

            val costo = costoStr.toDoubleOrNull() ?: 0.0
            historialModel.registrarTratamiento(descripcion, detalles, fechaTratamientoSeleccionada, costo, pacienteId)
            mostrarTratamientos(container.parent as FrameLayout)
        }

        btnVolver.setOnClickListener {
            mostrarTratamientos(container.parent as FrameLayout)
        }
    }

    private fun mostrarActualizarPago(container: FrameLayout) {
        container.removeAllViews()
        val view = View.inflate(this, R.layout.content_actualizar_pago, container)

        val inputPagoId = view.findViewById<EditText>(R.id.input_pago_id)
        val spinnerEstado = view.findViewById<Spinner>(R.id.spinner_estado_pago)
        val inputSaldoPendiente = view.findViewById<EditText>(R.id.input_saldo_pendiente)
        val btnGuardar = view.findViewById<Button>(R.id.btn_guardar_pago)
        val btnVolver = view.findViewById<Button>(R.id.btn_volver_actualizar_pago)

        val opciones = arrayOf("Pendiente", "Pagado")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opciones)
        spinnerEstado.adapter = adapter

        btnGuardar.setOnClickListener {
            val pagoId = inputPagoId.text.toString().trim()
            val estadoSeleccionado = spinnerEstado.selectedItem.toString().lowercase()
            val saldo = inputSaldoPendiente.text.toString().trim()

            if (pagoId.isEmpty()) {
                mostrarMensaje("Por favor ingresa el ID del pago")
                return@setOnClickListener
            }

            val saldoValue = if (saldo.isEmpty()) 0.0 else saldo.toDoubleOrNull() ?: 0.0
            historialModel.actualizarEstadoPago(pagoId, estadoSeleccionado, 0L)
            mostrarPagos(container.parent as FrameLayout)
        }

        btnVolver.setOnClickListener {
            mostrarPagos(container.parent as FrameLayout)
        }
    }

    private fun mostrarDatePicker(onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val año = calendar.get(Calendar.YEAR)
        val mes = calendar.get(Calendar.MONTH)
        val dia = calendar.get(Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(this, { _, y, m, d ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(y, m, d, 0, 0, 0)
            onDateSelected(selectedCalendar.timeInMillis)
        }, año, mes, dia).show()
    }

    private fun mostrarMensaje(mensaje: String) {
        MaterialAlertDialogBuilder(this).setTitle("Información")
            .setMessage(mensaje)
            .setPositiveButton("OK", null).show()
    }

    private fun render(state: AccountState) {
        binding.connection.setText(if (state.connected) R.string.connection_online else R.string.connection_offline)
        binding.loadingGroup.isVisible = state.busy
        binding.message.isVisible = state.message.isNotBlank()
        binding.message.text = state.message
        binding.message.setTextColor(ContextCompat.getColor(this, if (state.error) R.color.error else R.color.primary))
        binding.message.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (state.error) R.color.error_surface else R.color.soft))
        if (state.message.isNotBlank() && state.message != lastMessage) {
            binding.message.post {
                binding.message.requestRectangleOnScreen(Rect(0, 0, binding.message.width, binding.message.height), true)
            }
        }
        lastMessage = state.message
        if (renderedScreen != state.screen) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(binding.root.windowToken, 0)
            authForm?.password?.text?.clear()
            authForm?.confirmation?.text?.clear()
            binding.content.removeAllViews()
            authForm = null; home = null; profileForm = null; currentDashboardView = null
            binding.bottomNavigation.isVisible = state.screen == Screen.HOME
            when (state.screen) {
                Screen.LOGIN, Screen.REGISTER, Screen.RESET -> showAuth(state.screen)
                Screen.HOME -> {
                    showHome()
                    binding.bottomNavigation.selectedItemId = R.id.nav_inicio
                    showDashboardContent(R.layout.content_dashboard_inicio)
                }
                Screen.PROFILE -> showProfile(state)
            }
            renderedScreen = state.screen
        }
        setEnabled(binding.content, !state.busy)
        home?.let {
            val name = state.profile?.nombre.orEmpty()
            it.greeting.text = if (name.isBlank()) getString(R.string.home_title)
                else getString(R.string.greeting, name.substringBefore(' '))
            it.fullName.text = name.ifBlank { "Tu perfil" }
            it.email.text = state.email
            it.role.setText(if (state.staff) R.string.staff_account else R.string.patient_account)
            it.edit.isEnabled = !state.busy && state.profile != null
            it.retry.isVisible = state.error || state.profile == null
            it.logout.isEnabled = true
        }
        profileForm?.let { it.logout.isEnabled = true; it.back.isVisible = state.profileSaved }
    }

    private fun showAuth(screen: Screen) {
        val form = ContentAuthBinding.inflate(layoutInflater, binding.content, true)
        authForm = form
        val register = screen == Screen.REGISTER
        val reset = screen == Screen.RESET
        form.title.setText(when (screen) { Screen.REGISTER -> R.string.register_title; Screen.RESET -> R.string.reset_title; else -> R.string.login_title })
        ViewCompat.setAccessibilityHeading(form.title, true)
        form.description.setText(when (screen) { Screen.REGISTER -> R.string.register_description; Screen.RESET -> R.string.reset_description; else -> R.string.login_description })
        form.nameLayout.isVisible = register
        form.confirmationLayout.isVisible = register
        form.passwordHelp.isVisible = register
        form.passwordLayout.isVisible = !reset
        form.forgot.isVisible = screen == Screen.LOGIN
        form.email.setText(emailDraft)
        form.submit.setText(when (screen) { Screen.REGISTER -> R.string.register; Screen.RESET -> R.string.reset; else -> R.string.login })
        form.secondary.setText(if (screen == Screen.LOGIN) R.string.register else R.string.back_login)
        form.submit.setOnClickListener {
            when (screen) {
                Screen.REGISTER -> model.register(form.name.text.toString(), form.email.text.toString(), form.password.text.toString(), form.confirmation.text.toString())
                Screen.RESET -> model.reset(form.email.text.toString())
                else -> model.login(form.email.text.toString(), form.password.text.toString())
            }
        }
        form.secondary.setOnClickListener { leaveAuth(if (screen == Screen.LOGIN) Screen.REGISTER else Screen.LOGIN) }
        form.forgot.setOnClickListener { leaveAuth(Screen.RESET) }
    }

    private fun showHome() {
        home = ContentHomeBinding.inflate(layoutInflater, binding.content, true).also {
            ViewCompat.setAccessibilityHeading(it.greeting, true)
            it.edit.setOnClickListener { model.navigate(Screen.PROFILE) }
            it.retry.setOnClickListener { model.loadProfile() }
            it.logout.setOnClickListener { confirmLogout() }
        }
    }

    private fun showProfile(state: AccountState) {
        profileForm = ContentProfileBinding.inflate(layoutInflater, binding.content, true).also {
            it.name.setText(state.profile?.nombre.orEmpty())
            it.phone.setText(state.profile?.telefono.orEmpty())
            it.address.setText(state.profile?.direccion.orEmpty())
            it.email.text = getString(R.string.account_email, state.email)
            it.save.setOnClickListener { _ -> model.save(it.name.text.toString(), it.phone.text.toString(), it.address.text.toString()) }
            it.back.setOnClickListener { leaveProfile() }
            it.logout.setOnClickListener { confirmLogout() }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.logout)
            .setMessage(if (profileHasChanges()) R.string.logout_unsaved_question else R.string.logout_question)
            .setNegativeButton(R.string.stay, null)
            .setPositiveButton(R.string.logout) { _, _ -> emailDraft = ""; model.logout() }.show()
    }

    private fun leaveAuth(screen: Screen) {
        emailDraft = authForm?.email?.text.toString().trim()
        model.navigate(screen)
    }

    private fun profileHasChanges(): Boolean {
        val form = profileForm ?: return false
        val saved = model.state.value?.profile ?: return false
        return form.name.text.toString().trim() != saved.nombre ||
            form.phone.text.toString().trim() != saved.telefono ||
            form.address.text.toString().trim() != saved.direccion
    }

    private fun leaveProfile() {
        if (!profileHasChanges()) { model.navigate(Screen.HOME); return }
        MaterialAlertDialogBuilder(this).setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_question)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.discard) { _, _ -> model.navigate(Screen.HOME) }.show()
    }

    private fun setEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (index in 0 until view.childCount) setEnabled(view.getChildAt(index), enabled)
    }
}

