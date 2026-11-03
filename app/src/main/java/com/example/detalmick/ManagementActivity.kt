package com.example.detalmick

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.detalmick.data.*
import com.example.detalmick.databinding.ActivityMainBinding
import com.example.detalmick.domain.ClinicalValidation as Format
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ManagementActivity : AccessibleActivity() {
    private lateinit var shell:ActivityMainBinding
    private lateinit var content:LinearLayout
    private val db get()=FirebaseProvider.database.reference
    private var busy=false
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); shell=ActivityMainBinding.inflate(layoutInflater); setContentView(shell.root)
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; shell.content.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(shell.root) { v,i -> val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()); v.setPadding(b.left,b.top,b.right,b.bottom); i }
        val uid=FirebaseProvider.auth.currentUser?.uid ?: run { finish(); return }
        db.child("roles/$uid").get().addOnCompleteListener {
            if(it.isSuccessful && AccessControl.active(it.result) && it.result.child("admin").value==true) menu()
            else { AccessControl.denied("gestion_denegada"); finish() }
        }
    }
    private fun text(value:String) { content.addView(TextView(this).apply { text=value; textSize=18f; setPadding(0,16,0,16) }) }
    private fun button(value:String,action:()->Unit) { content.addView(MaterialButton(this).apply { text=value; minHeight=(56*resources.displayMetrics.density).toInt(); setOnClickListener { if(!busy) action() } }) }
    private fun input(parent:LinearLayout,label:String,value:String=""):TextInputEditText {
        val layout=TextInputLayout(this).apply { hint=label }; val field=TextInputEditText(this).apply { textSize=18f; setText(value) }
        layout.addView(field); parent.addView(layout); return field
    }
    private fun menu() { content.removeAllViews(); text("Administración de la clínica"); button("Reporte de ingresos") { report() }; button("Inventario e insumos") { inventory() }; button("Registro de accesos rechazados") { audit() }; button("Volver") { finish() } }
    private fun report() {
        content.removeAllViews(); text("Ingresos por período")
        val start=input(content,"Desde (AAAA-MM-DD)",Format.today().take(8)+"01")
        val end=input(content,"Hasta (AAAA-MM-DD)",Format.today())
        val result=TextView(this).apply { textSize=18f }
        button("Generar reporte") {
            val a=start.text.toString().trim(); val b=end.text.toString().trim()
            if(!Format.date(a) || !Format.date(b) || a>b) { result.text="Selecciona un rango de fechas válido."; return@button }
            val format=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("America/El_Salvador"); isLenient=false }
            val from=format.parse(a)!!.time; val until=format.parse(b)!!.time+86400000
            busy=true; shell.loadingGroup.isVisible=true
            db.child("pagos").get().addOnCompleteListener { response ->
                busy=false; shell.loadingGroup.isVisible=false
                if(!response.isSuccessful) { result.text="No se pudo consultar el reporte. Revisa tu conexión."; return@addOnCompleteListener }
                runCatching {
                    val payments=response.result.children.flatMap { p -> p.children.map { Payment.decode(it.key!!,it.value) } }
                    val receipts=payments.flatMap { it.abonos }.filter { it.at>=from && it.at<until }
                    "Período: $a a $b\nIngresos recibidos: ${Format.amount(receipts.sumOf { it.cents })}\nAbonos registrados: ${receipts.size}\nSaldo pendiente actual de toda la clínica: ${Format.amount(payments.sumOf { it.saldo })}\nLos ingresos corresponden a abonos recibidos, no a cargos emitidos."
                }.onSuccess { result.text=it }.onFailure { result.text="Hay registros incompletos. Revisa los pagos antes de generar el reporte." }
            }
        }; content.addView(result); button("Volver a administración") { menu() }
    }
    private fun inventory() {
        content.removeAllViews(); text("Inventario de insumos")
        button("Agregar insumo") { editStock(null) }
        busy=true; shell.loadingGroup.isVisible=true
        db.child("inventario").get().addOnCompleteListener { result ->
            busy=false; shell.loadingGroup.isVisible=false
            if(!result.isSuccessful) { text("No se pudo consultar el inventario."); button("Reintentar") { inventory() }; return@addOnCompleteListener }
            if(!result.result.exists()) text("Aún no hay insumos registrados.")
            result.result.children.forEach { item ->
                val stock=(item.child("stock").value as? Long) ?: 0L; val min=(item.child("minimo").value as? Long) ?: 0L
                text("${item.child("nombre").value}\nStock: $stock · Mínimo: $min${if(stock<min) "\nALERTA: stock por debajo del mínimo" else ""}")
                button("Actualizar ${item.child("nombre").value}") { editStock(item) }
            }; button("Volver a administración") { menu() }
        }
    }
    private fun editStock(item:DataSnapshot?) {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        val name=input(form,"Nombre del insumo",(item?.child("nombre")?.value as? String).orEmpty())
        val stock=input(form,"Existencias",item?.child("stock")?.value?.toString() ?: "0")
        val minimum=input(form,"Stock mínimo",item?.child("minimo")?.value?.toString() ?: "1")
        stock.inputType=android.text.InputType.TYPE_CLASS_NUMBER; minimum.inputType=android.text.InputType.TYPE_CLASS_NUMBER
        val dialog=MaterialAlertDialogBuilder(this).setTitle("Control de insumo").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val n=name.text.toString().trim(); val s=stock.text.toString().toLongOrNull(); val m=minimum.text.toString().toLongOrNull()
            if(n.length !in 1..120 || s==null || m==null || s !in 0..1000000 || m !in 0..1000000) { name.error="Nombre obligatorio; stock y mínimo entre 0 y 1000000."; return@setOnClickListener }
            dialog.getButton(-1).isEnabled=false
            val expected=(item?.child("revision")?.value as? Long) ?: 0L
            val ref=if(item==null) db.child("inventario").push() else item.ref
            ref.runTransaction(object:Transaction.Handler {
                override fun doTransaction(data:MutableData):Transaction.Result {
                    if(data.value==null && expected>0) return Transaction.success(data)
                    if(((data.child("revision").value as? Long) ?: 0L)!=expected) return Transaction.abort()
                    data.value=mapOf("nombre" to n,"stock" to s,"minimo" to m,"revision" to expected+1,"actualizadoEn" to ServerValue.TIMESTAMP)
                    return Transaction.success(data)
                }
                override fun onComplete(error:DatabaseError?,committed:Boolean,snapshot:DataSnapshot?) {
                    if(error==null && committed && snapshot?.exists()==true) { dialog.dismiss(); inventory() }
                    else { dialog.getButton(-1).isEnabled=true; name.error="No se guardó. Reabre el inventario para actualizar los datos." }
                }
            },false)
        } }; dialog.show()
    }
    private fun audit() {
        content.removeAllViews(); text("Accesos rechazados por la aplicación")
        db.child("eventosSeguridad").get().addOnCompleteListener { result ->
            if(!result.isSuccessful) text("No se pudo consultar el registro.")
            else {
                val entries=result.result.children.flatMap { user -> user.children.map { entry -> Triple(user.key.orEmpty(),entry.child("codigo").value,entry.child("creadoEn").value as? Long ?: 0L) } }.sortedByDescending { it.third }.take(100)
                if(entries.isEmpty()) text("Aún no hay rechazos registrados por la aplicación.")
                entries.forEach { text("${it.second}\nUsuario: ${it.first}\n${SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ROOT).format(java.util.Date(it.third))}") }
            }; button("Volver a administración") { menu() }
        }
    }
}
