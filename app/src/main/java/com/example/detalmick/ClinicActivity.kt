package com.example.detalmick

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.detalmick.data.FirebaseProvider
import com.example.detalmick.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ClinicActivity : AccessibleActivity() {
    private lateinit var shell: ActivityMainBinding
    private lateinit var content: LinearLayout
    private val db get() = FirebaseProvider.database.reference
    private var admin = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shell = ActivityMainBinding.inflate(layoutInflater); setContentView(shell.root)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; shell.content.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(shell.root) { v, i ->
            val b = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left,b.top,b.right,b.bottom); i
        }
        load()
    }
    private fun text(value: String) { content.addView(TextView(this).apply { text = value; textSize = 18f; setPadding(16,16,16,16) }) }
    private fun button(value: String, action: () -> Unit) { content.addView(MaterialButton(this).apply { text = value; minHeight = (56*resources.displayMetrics.density).toInt(); setOnClickListener { action() } }) }
    private fun open(uri: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this,"No hay una aplicación disponible para abrir este canal.",Toast.LENGTH_LONG).show() }
    }
    private fun load() {
        shell.loadingGroup.isVisible = true
        val uid = FirebaseProvider.auth.currentUser?.uid ?: run { finish(); return }
        db.child("roles/$uid").get().addOnSuccessListener { role ->
            admin = role.child("admin").value == true && role.child("activo").value != false
            db.child("clinica").get().addOnCompleteListener { result ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                shell.loadingGroup.isVisible = false; content.removeAllViews()
                text("Clínica y ayuda")
                val data = if (result.isSuccessful) result.result else null
                text(data?.child("nombre")?.value as? String ?: "DetalMick")
                val address = data?.child("direccion")?.value as? String ?: ""
                text(address.ifBlank { "La clínica aún no ha publicado su dirección." })
                val phone = (data?.child("telefono")?.value as? String).orEmpty().filter(Char::isDigit)
                val whatsapp = (data?.child("whatsapp")?.value as? String).orEmpty().filter(Char::isDigit)
                if (phone.length in 8..15) button("Llamar a la clínica") { open("tel:+$phone") }
                if (whatsapp.length in 8..15) button("Conversar por WhatsApp") { open("https://wa.me/$whatsapp") }
                if (phone.isBlank() && whatsapp.isBlank()) text("La clínica aún no ha publicado sus canales de contacto.")
                if (!result.isSuccessful) { text("No se pudo cargar la información. Revisa la conexión."); button("Reintentar") { load() } }
                text("Privacidad: tu expediente y tus saldos están protegidos por tu sesión. El personal autorizado puede consultarlos para tu atención. No compartas tu contraseña. WhatsApp y las llamadas abren aplicaciones externas; tú decides qué información enviar.")
                val prefs = getSharedPreferences("accessibility",0)
                button(if (prefs.getBoolean("largeText",false)) "Usar tamaño de texto del teléfono" else "Activar texto grande") {
                    prefs.edit().putBoolean("largeText",!prefs.getBoolean("largeText",false)).apply(); recreate()
                }
                if (admin) {
                    button("Configurar datos de la clínica") {
                        editClinic(listOf("nombre","direccion","telefono","whatsapp").associateWith { (data?.child(it)?.value as? String).orEmpty() })
                    }
                    button("Agregar servicio al catálogo") { editService() }
                }
                button("Volver al inicio") { finish() }
            }
        }.addOnFailureListener {
            shell.loadingGroup.isVisible = false; text("No se pudo verificar la sesión."); button("Reintentar") { content.removeAllViews(); load() }
        }
    }
    private fun editClinic(values: Map<String,String>) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        val inputs = values.mapValues { (key,value) -> input(container, when(key) { "nombre" -> "Nombre de la clínica"; "direccion" -> "Dirección"; "telefono" -> "Teléfono con código de país"; else -> "WhatsApp con código de país" }, value) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Datos de contacto").setView(container).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val changes = inputs.mapValues { it.value.text.toString().trim() }
            if (changes.getValue("nombre").length !in 1..120 || changes.getValue("direccion").length > 300 || listOf("telefono","whatsapp").any { changes.getValue(it).isNotBlank() && changes.getValue(it).filter(Char::isDigit).length !in 8..15 }) {
                Toast.makeText(this,"Revisa el nombre, la dirección y los teléfonos (8 a 15 dígitos).",Toast.LENGTH_LONG).show(); return@setOnClickListener
            }
            dialog.getButton(-1).isEnabled = false
            db.child("clinica").updateChildren(changes).addOnCompleteListener { task ->
                if (task.isSuccessful) { dialog.dismiss(); load() } else { dialog.getButton(-1).isEnabled = true; Toast.makeText(this,"No se guardaron los cambios.",Toast.LENGTH_LONG).show() }
            }
        } }; dialog.show()
    }
    private fun input(parent: LinearLayout, label: String, value: String = ""): TextInputEditText {
        val layout = TextInputLayout(this).apply { hint = label }
        val field = TextInputEditText(this).apply { setText(value); textSize = 18f; maxLines = 3 }
        layout.addView(field); parent.addView(layout); return field
    }
    private fun editService() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        val field = input(container,"Nombre del servicio")
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Agregar servicio").setView(container).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val name = field.text.toString().trim()
            if (name.length !in 1..120) { field.error = "Escribe de 1 a 120 caracteres"; return@setOnClickListener }
            dialog.getButton(-1).isEnabled = false
            db.child("servicios").push().setValue(mapOf("nombre" to name)).addOnCompleteListener {
                if (it.isSuccessful) { dialog.dismiss(); Toast.makeText(this,"Servicio agregado.",Toast.LENGTH_SHORT).show() }
                else { dialog.getButton(-1).isEnabled = true; field.error = "No se pudo guardar" }
            }
        } }; dialog.show()
    }
}
