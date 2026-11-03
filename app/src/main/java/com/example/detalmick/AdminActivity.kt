package com.example.detalmick

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.detalmick.data.AdminDentist
import com.example.detalmick.data.AdminRepository
import com.example.detalmick.databinding.ActivityAdminBinding
import com.example.detalmick.databinding.DialogCreateDentistBinding
import com.example.detalmick.databinding.ItemAdminDentistBinding
import com.example.detalmick.domain.Validation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class AdminActivity : AccessibleActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val repository = AdminRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnNuevoDentista.setOnClickListener { showInviteDentistDialog() }
        val management = com.google.android.material.button.MaterialButton(this).apply {
            text="Ingresos, inventario y auditoría"
            setOnClickListener { startActivity(android.content.Intent(this@AdminActivity,ManagementActivity::class.java)) }
        }
        binding.root.addView(management,2)

        val uid=com.example.detalmick.data.FirebaseProvider.auth.currentUser?.uid ?: run { finish(); return }
        com.example.detalmick.data.FirebaseProvider.database.getReference("roles/$uid").get().addOnCompleteListener {
            if(it.isSuccessful && it.result.child("admin").value==true && com.example.detalmick.data.AccessControl.active(it.result)) loadDentists()
            else { com.example.detalmick.data.AccessControl.denied("administracion_denegada"); finish() }
        }
    }

    override fun onDestroy() { repository.stop(); super.onDestroy() }

    private fun loadDentists() {
        binding.progressBar.isVisible = true
        binding.tvEmpty.isVisible = false

        repository.observeStaff(
            onUpdate = { list ->
                binding.progressBar.isVisible = false
                binding.tvEmpty.isVisible = list.isEmpty()
                renderDentists(list)
            },
            onError = { err ->
                binding.progressBar.isVisible = false
                Snackbar.make(binding.root, "Error al cargar personal: $err", Snackbar.LENGTH_LONG).show()
            }
        )
    }

    private fun renderDentists(list: List<AdminDentist>) {
        binding.dentistsContainer.removeAllViews()
        for (dentist in list) {
            val itemBinding = ItemAdminDentistBinding.inflate(layoutInflater, binding.dentistsContainer, false)
            itemBinding.tvDentistName.text = dentist.name
            itemBinding.tvEspecialidad.text = dentist.especialidad
            itemBinding.tvPhone.text = when {
                dentist.phone.isNotBlank() -> dentist.phone
                dentist.email.isNotBlank() -> dentist.email
                else -> "Perfil pendiente de completar"
            }

            updateBadge(itemBinding, dentist.activo)

            // No permitir deshabilitar administradores principales
            if (dentist.isAdmin) {
                itemBinding.switchActivo.isEnabled = false
                itemBinding.tvSwitchLabel.text = "Administrador del sistema (Protegido)"
            } else {
                itemBinding.switchActivo.isEnabled = true
                itemBinding.tvSwitchLabel.text = "Acceso al sistema:"
            }

            itemBinding.switchActivo.setOnCheckedChangeListener(null)
            itemBinding.switchActivo.isChecked = dentist.activo

            itemBinding.btnDeleteDentist.isVisible = !dentist.isAdmin
            itemBinding.btnDeleteDentist.setOnClickListener { confirmDelete(dentist) }

            itemBinding.switchActivo.setOnClickListener {
                val newStatus = itemBinding.switchActivo.isChecked
                if (!newStatus) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("¿Suspender acceso?")
                        .setMessage("Al suspender a ${dentist.name}, no podrá iniciar sesión en la aplicación hasta que sea habilitado de nuevo.")
                        .setPositiveButton("Suspender") { _, _ ->
                            changeStatus(dentist.uid, false, itemBinding)
                        }
                        .setNegativeButton("Cancelar") { _, _ ->
                            itemBinding.switchActivo.isChecked = true
                        }
                        .setOnCancelListener {
                            itemBinding.switchActivo.isChecked = true
                        }
                        .show()
                } else {
                    changeStatus(dentist.uid, true, itemBinding)
                }
            }

            binding.dentistsContainer.addView(itemBinding.root)
        }
    }

    private fun updateBadge(item: ItemAdminDentistBinding, activo: Boolean) {
        if (activo) {
            item.tvStatusBadge.text = "Activo"
            item.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
            item.tvStatusBadge.setTextColor(getColor(R.color.primary))
        } else {
            item.tvStatusBadge.text = "Deshabilitado"
            item.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
            item.tvStatusBadge.setTextColor(getColor(R.color.error))
        }
    }

    private fun changeStatus(uid: String, activo: Boolean, item: ItemAdminDentistBinding) {
        binding.progressBar.isVisible = true
        repository.setStaffStatus(uid, activo) { success, error ->
            binding.progressBar.isVisible = false
            if (success) {
                updateBadge(item, activo)
                val msg = if (activo) "Cuenta habilitada correctamente" else "Cuenta deshabilitada correctamente"
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            } else {
                item.switchActivo.isChecked = !activo
                Snackbar.make(binding.root, "Error al cambiar estado: $error", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(dentist: AdminDentist) {
        if (dentist.isAdmin) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Retirar acceso del profesional")
            .setMessage("Se suspenderá el acceso de ${dentist.name} y se retirará del catálogo. Su cuenta y el historial clínico se conservan. Puedes habilitarlo nuevamente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Retirar acceso") { _, _ ->
                binding.progressBar.isVisible = true
                repository.deleteDentist(dentist.uid) { success, error ->
                    binding.progressBar.isVisible = false
                    if (success) Snackbar.make(binding.root, "Acceso retirado. Se conserva el historial.", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "No se pudo retirar el acceso: ${error ?: "Error desconocido"}", Snackbar.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun generateRandomPassword(): String {
        val digits = (1000..9999).random()
        val letters = listOf("A", "B", "C", "D", "E", "F", "X", "Z", "M", "K").shuffled().take(2).joinToString("")
        return "Denta#${digits}$letters"
    }

    private fun showInviteDentistDialog() {
        val dialogBinding = DialogCreateDentistBinding.inflate(LayoutInflater.from(this))
        val reception = com.google.android.material.checkbox.MaterialCheckBox(this).apply { text = "Cuenta de recepcionista (sin acceso clínico)" }
        dialogBinding.root.addView(reception)
        var currentPass = generateRandomPassword()
        dialogBinding.tvGeneratedPassword.text = currentPass

        dialogBinding.btnRegenerarPass.setOnClickListener {
            currentPass = generateRandomPassword()
            dialogBinding.tvGeneratedPassword.text = currentPass
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Generar e invitar", null)
            .setNegativeButton("Cancelar", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    val registerBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    registerBtn.setOnClickListener {
                        val email = dialogBinding.etEmail.text.toString().trim()

                        if (!Validation.email(email)) {
                            dialogBinding.etEmail.error = "Escribe un correo electrónico válido"
                            return@setOnClickListener
                        }

                        binding.progressBar.isVisible = true
                        dialog.dismiss()

                        repository.inviteDentist(this, email, currentPass, reception.isChecked) { success, error ->
                            binding.progressBar.isVisible = false
                            if (success) {
                                showCredentialsSuccessDialog(email, currentPass)
                            } else {
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("Error al registrar dentista")
                                    .setMessage(error ?: "No se pudo registrar la cuenta.")
                                    .setPositiveButton("Entendido", null)
                                    .show()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showCredentialsSuccessDialog(email: String, pass: String) {
        val msg = "Se ha creado la cuenta profesional para:\n\n" +
                "• Usuario/Correo: $email\n" +
                "• Contraseña provisional: $pass\n\n" +
                "Entrega estas credenciales al dentista. Al iniciar sesión por primera vez, la app le solicitará completar su nombre completo, especialidad y cambiar su contraseña."

        MaterialAlertDialogBuilder(this)
            .setTitle("¡Dentista Invitado!")
            .setMessage(msg)
            .setPositiveButton("Copiar credenciales") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Credenciales Dentista", "Usuario: $email\nContraseña temporal: $pass")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Credenciales copiadas al portapapeles", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
