package com.example.detalmick

import android.os.Bundle
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.detalmick.data.*
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.domain.Validation
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*

class PatientRecordActivity : AccessibleActivity() {
    private lateinit var shell: ActivityMainBinding
    private lateinit var content: LinearLayout
    private lateinit var notes: TextInputEditText
    private lateinit var name: TextInputEditText
    private lateinit var phone: TextInputEditText
    private lateinit var address: TextInputEditText
    private val teeth = mutableMapOf<String,String>()
    private val buttons = mutableMapOf<String,MaterialButton>()
    private val states = arrayOf("sano","caries","restaurado","ausente")
    private val db get() = FirebaseProvider.database.reference
    private var patient = ""
    private var staff = false
    private var revision = 0L
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shell = ActivityMainBinding.inflate(layoutInflater); setContentView(shell.root)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; shell.content.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(shell.root) { v,i -> val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()); v.setPadding(b.left,b.top,b.right,b.bottom); i }
        patient = intent.getStringExtra("patient").orEmpty()
        val uid = FirebaseProvider.auth.currentUser?.uid ?: run { finish(); return }
        if (patient.isBlank()) patient = uid
        shell.loadingGroup.isVisible = true
        db.child("roles/$uid").get().addOnCompleteListener { role ->
            staff = role.isSuccessful && AccessControl.clinical(role.result)
            if (!role.isSuccessful || !AccessControl.active(role.result) || (!staff && patient != uid) || (AccessControl.reception(role.result) && !staff)) {
                AccessControl.denied("expediente_denegado"); finish(); return@addOnCompleteListener
            }
            load(savedInstanceState)
        }
    }
    private fun text(value:String) { content.addView(TextView(this).apply { text=value; textSize=18f; setPadding(0,16,0,16) }) }
    private fun input(label:String,value:String,multiline:Boolean=false):TextInputEditText {
        val layout=TextInputLayout(this).apply { hint=label }
        val field=TextInputEditText(this).apply { setText(value); textSize=18f; isEnabled=staff; if(multiline) minLines=3 }
        layout.addView(field); content.addView(layout); return field
    }
    private fun button(label:String,action:()->Unit) { content.addView(MaterialButton(this).apply { text=label; minHeight=(56*resources.displayMetrics.density).toInt(); setOnClickListener { if(!busy) action() } }) }
    private fun load(saved:Bundle?) {
        db.child("usuarios/$patient").get().addOnSuccessListener { profile ->
            db.child("odontogramas/$patient").get().addOnCompleteListener { result ->
                shell.loadingGroup.isVisible=false
                if (!result.isSuccessful || !profile.exists()) { text("No se pudo cargar el expediente."); button("Reintentar") { content.removeAllViews(); load(null) }; button("Volver") { finish() }; return@addOnCompleteListener }
                val value=result.result; revision=(value.child("revision").value as? Long) ?: 0L
                text("Expediente del paciente")
                name=input("Nombre",saved?.getString("name") ?: profile.child("nombre").value as? String ?: "")
                phone=input("Teléfono",saved?.getString("phone") ?: profile.child("telefono").value as? String ?: "")
                address=input("Dirección",saved?.getString("address") ?: profile.child("direccion").value as? String ?: "")
                if(staff) button("Guardar datos del paciente") { saveProfile() }
                text("Odontograma permanente · Numeración FDI\nSano, caries, restaurado o ausente. ${if(staff) "Toca un diente para registrar su estado." else "Consulta de tu registro odontológico."}")
                val grid=GridLayout(this).apply { columnCount=2; layoutParams=LinearLayout.LayoutParams(-1,-2) }
                listOf(1,2,3,4).flatMap { q -> (1..8).map { "$q$it" } }.forEach { tooth ->
                    teeth[tooth]=saved?.getString("tooth$tooth") ?: value.child("dientes/$tooth").value as? String ?: "sano"
                    val b=MaterialButton(this).apply {
                        text="$tooth: ${teeth[tooth]}"; isEnabled=staff; minHeight=(56*resources.displayMetrics.density).toInt()
                        layoutParams=GridLayout.LayoutParams().apply { width=0; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) }
                        setOnClickListener { if(!busy) MaterialAlertDialogBuilder(this@PatientRecordActivity).setTitle("Diente $tooth").setItems(states) { _,index -> teeth[tooth]=states[index]; text="$tooth: ${teeth[tooth]}" }.show() }
                    }; buttons[tooth]=b; grid.addView(b)
                }; content.addView(grid)
                notes=input("Observaciones clínicas",saved?.getString("notes") ?: value.child("observaciones").value as? String ?: "",true)
                if(staff) button("Guardar odontograma") { saveChart() }
                button("Volver") { finish() }
            }
        }.addOnFailureListener { shell.loadingGroup.isVisible=false; text("No se pudo cargar el expediente."); button("Reintentar") { content.removeAllViews(); load(saved) } }
    }
    private fun saveProfile() {
        val n=name.text.toString().trim(); val p=phone.text.toString().trim(); val a=address.text.toString().trim()
        if(!Validation.name(n) || !Validation.phone(p) || !Validation.address(a)) { message("Revisa el nombre, teléfono y dirección."); return }
        busy=true
        db.child("usuarios/$patient").updateChildren(mapOf("nombre" to n,"telefono" to p,"direccion" to a)).addOnCompleteListener { busy=false; message(if(it.isSuccessful) "Datos del paciente guardados." else "No se guardaron los datos. Revisa la conexión y los permisos.") }
    }
    private fun saveChart() {
        val note=notes.text.toString().trim()
        if(note.length>2000) { message("Las observaciones admiten hasta 2000 caracteres."); return }
        busy=true
        val expected=revision; val values=teeth.toMap(); val uid=FirebaseProvider.auth.currentUser?.uid ?: return
        db.child("odontogramas/$patient").runTransaction(object:Transaction.Handler {
            override fun doTransaction(data:MutableData):Transaction.Result {
                if(data.value==null && expected>0) return Transaction.success(data)
                val current=(data.child("revision").value as? Long) ?: 0L
                if(current!=expected) return Transaction.abort()
                data.value=mapOf("dientes" to values,"observaciones" to note,"revision" to expected+1,"actualizadoEn" to ServerValue.TIMESTAMP,"registradoPor" to uid)
                return Transaction.success(data)
            }
            override fun onComplete(error:DatabaseError?,committed:Boolean,snapshot:DataSnapshot?) {
                busy=false
                if(error==null && committed && snapshot?.exists()==true) { revision=expected+1; message("Odontograma guardado.") }
                else { message("No se guardó. Otro profesional pudo modificar el expediente. Reabre la pantalla para consultar la última versión.") }
            }
        },false)
    }
    private fun message(value:String) { Toast.makeText(this,value,Toast.LENGTH_LONG).show() }
    override fun onSaveInstanceState(out:Bundle) {
        super.onSaveInstanceState(out)
        if(::notes.isInitialized) { out.putString("notes",notes.text.toString()); out.putString("name",name.text.toString()); out.putString("phone",phone.text.toString()); out.putString("address",address.text.toString()); teeth.forEach { (k,v) -> out.putString("tooth$k",v) } }
    }
}
