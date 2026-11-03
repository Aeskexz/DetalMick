package com.example.detalmick

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.detalmick.data.*
import com.example.detalmick.domain.CitaValidation
import com.google.android.gms.tasks.Tasks
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/** Reproducible screenshots from a running app, using local synthetic records. */
@RunWith(AndroidJUnit4::class)
class EvidenceCaptureTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun capture(name:String) {
        instrumentation.waitForIdleSync()
        val image=instrumentation.uiAutomation.takeScreenshot()
        val folder=context.getExternalFilesDir("evidence")!!.apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        val command="mkdir -p /sdcard/Download/DetalMick-evidence; cp '${File(folder,"$name.png").absolutePath}' /sdcard/Download/DetalMick-evidence/$name.png"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun waitFor(check:()->Unit) {
        val end=System.currentTimeMillis()+20000; var failure:Throwable?=null
        while(System.currentTimeMillis()<end) { try { check(); return } catch(e:Throwable) { failure=e; Thread.sleep(120) } }
        throw AssertionError("Expected screen was not reached",failure)
    }
    private fun visible(text:String)=waitFor { onView(withText(text)).check(matches(isDisplayed())) }
    private fun click(text:String)=onView(withText(text)).perform(scrollTo(),click())
    private fun signIn(email:String) { Tasks.await(FirebaseProvider.auth.signInWithEmailAndPassword(email,LocalClinic.password),20,TimeUnit.SECONDS) }
    private inline fun <reified T:android.app.Activity> screen(extras:Map<String,String> = emptyMap(), action:(ActivityScenario<T>)->Unit) {
        val intent=Intent(context,T::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        extras.forEach { (k,v)->intent.putExtra(k,v) }
        ActivityScenario.launch<T>(intent).use(action)
    }
    @Test fun capturePatientDentistReceptionAndAdministration() = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val tag=System.currentTimeMillis(); val email="evidence-$tag@example.test"; val dentistEmail="doctor-$tag@example.test"; val adminEmail="admin-$tag@example.test"; val receptionEmail="reception-$tag@example.test"
        val patient=LocalClinic.actor(email); val dentist=LocalClinic.actor(dentistEmail); val admin=LocalClinic.actor(adminEmail); val reception=LocalClinic.actor(receptionEmail)
        val date=Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH,4); while(get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY) add(Calendar.DAY_OF_MONTH,1) }
        val day=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(date.time)
        val c=Cita("evidence-cita",patient,"Ana Demostración",dentist,"Dra. Elena Prueba","evidence-service","Consulta general",day,"09:00 - 10:00",inicioEn=CitaValidation.inicioMillis(day,"09:00 - 10:00")!!)
        val appointment=JSONObject().put("id",c.id).put("uid",patient).put("pacienteNombre",c.pacienteNombre).put("dentistaId",dentist).put("dentistaNombre",c.dentistaNombre)
            .put("servicioId",c.servicioId).put("servicioNombre",c.servicioNombre).put("fecha",day).put("horario",c.horario).put("slotKey",c.slotKey).put("inicioEn",c.inicioEn).put("estado","confirmada").put("asistenciaConfirmada",false).put("anio",c.anio).put("mes",c.mes).put("dia",c.dia)
        val now=System.currentTimeMillis()
        LocalClinic.seed(JSONObject().put("usuarios/$patient",JSONObject().put("nombre",c.pacienteNombre).put("telefono","+503 7000-0000").put("direccion","San Salvador"))
            .put("roles/$dentist",JSONObject().put("personal",true).put("activo",true))
            .put("roles/$admin",JSONObject().put("admin",true).put("activo",true))
            .put("roles/$reception",JSONObject().put("recepcionista",true).put("activo",true))
            .put("dentistas/$dentist",JSONObject().put("nombre",c.dentistaNombre).put("especialidad","Odontología general").put("activo",true))
            .put("dentistas/$admin",JSONObject().put("nombre","Administrador Demo").put("especialidad","Administración").put("activo",true))
            .put("dentistas/$reception",JSONObject().put("nombre","Recepción Demo").put("activo",true))
            .put("clinica/dentistas/$dentist",JSONObject().put("nombre",c.dentistaNombre).put("especialidad","Odontología general"))
            .put("clinica/nombre","Clínica DetalMick de demostración").put("clinica/direccion","San Salvador · Dirección de demostración")
            .put("clinica/telefono","50370000000").put("clinica/whatsapp","50370000000")
            .put("servicios/evidence-service",JSONObject().put("nombre","Consulta general"))
            .put("servicios/evidence-cleaning",JSONObject().put("nombre","Limpieza dental"))
            .put("citas/$patient/${c.id}",appointment).put("agenda/$dentist/$day/0900",JSONObject().put("uid",patient).put("id",c.id))
            .put("tratamientos/$patient/evidence-treatment",JSONObject().put("titulo","Limpieza dental").put("descripcion","Retiramos la placa y revisamos el cuidado diario de tus dientes.").put("estado","realizado").put("fecha",day).put("creadoEn",now).put("actualizadoEn",now).put("registradoPor",dentist))
            .put("pagos/$patient/evidence-invoice",JSONObject().put("concepto","Consulta y limpieza").put("citaId",c.id).put("totalCentavos",10000).put("abonadoCentavos",2500).put("estado","pendiente").put("fechaLimite",day).put("creadoEn",now).put("actualizadoEn",now).put("registradoPor",dentist)
                .put("abonos",JSONObject().put("first",JSONObject().put("montoCentavos",2500).put("creadoEn",now).put("registradoPor",dentist))))
            .put("inventario/evidence-gloves",JSONObject().put("nombre","Guantes de examen").put("stock",2).put("minimo",5).put("revision",1).put("actualizadoEn",now)))
        signIn(email)
        screen<MainActivity> { visible("Hola, Ana"); capture("06-inicio-paciente"); onView(withId(R.id.clinic)).perform(scrollTo()); capture("07-menu-paciente") }
        screen<ClinicActivity> { visible("Clínica y ayuda"); capture("08-contacto"); click("Activar texto grande"); visible("Clínica y ayuda"); capture("09-texto-grande"); click("Usar tamaño de texto del teléfono") }
        screen<CitasActivity> {
            visible("Mis citas y recordatorios"); waitFor { onView(withId(R.id.btnConfirmarAsistencia)).check(matches(isEnabled())) }
            capture("10-citas-recordatorios"); onView(withId(R.id.btnConfirmarAsistencia)).perform(scrollTo(),click())
            waitFor { onView(withText("Asistencia confirmada")).check(matches(isDisplayed())) }; capture("11-asistencia-confirmada")
            onView(withId(R.id.btnAgendarNuevaCita)).perform(scrollTo(),click()); visible("Paso 1 de 3"); capture("12-agendar-servicio")
            onView(withId(R.id.btnSiguiente1)).perform(scrollTo(),click()); visible("Paso 2 de 3"); capture("13-agendar-fecha")
            onView(withId(R.id.btnElegirFecha)).perform(scrollTo(),click())
            val target=Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH,3); while(get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY) add(Calendar.DAY_OF_MONTH,1) }
            if(target.get(Calendar.MONTH)!=Calendar.getInstance().get(Calendar.MONTH)) onView(withId(com.google.android.material.R.id.month_navigation_next)).perform(click())
            onView(allOf(withText(target.get(Calendar.DAY_OF_MONTH).toString()),isDisplayed(),isDescendantOfA(withClassName(equalTo("com.google.android.material.datepicker.MaterialCalendarGridView"))))).perform(click())
            capture("14-calendario"); onView(withId(com.google.android.material.R.id.confirm_button)).perform(click())
            waitFor { onView(withText("11:00")).check(matches(isEnabled())) }; onView(withText("11:00")).perform(scrollTo(),click()); capture("15-horarios-disponibles")
            onView(withId(R.id.btnSiguiente2)).perform(scrollTo(),click()); visible("Paso 3 de 3"); capture("16-confirmacion-cita")
            onView(withId(R.id.btnConfirmarCita)).perform(scrollTo(),click()); visible("Mis citas y recordatorios"); capture("17-cita-guardada")
        }
        screen<ClinicalActivity>(mapOf("tab" to "tratamientos")) {
            visible("Mis tratamientos y pagos"); waitFor { onView(withText("Limpieza dental")).check(matches(isDisplayed())) }; capture("18-tratamientos-paciente")
            click("Ver detalle"); visible("Cerrar"); capture("19-detalle-tratamiento"); onView(withText("Cerrar")).perform(click())
            onView(withId(R.id.paymentsTab)).perform(scrollTo(),click()); waitFor { onView(withText("Consulta y limpieza")).check(matches(isDisplayed())) }; capture("20-pagos-paciente")
            click("Ver detalle"); visible("Ver factura"); capture("21-detalle-pago"); onView(withText("Ver factura")).perform(click()); visible("Factura de la cita"); capture("22-factura")
        }
        signIn(dentistEmail)
        screen<MainActivity> { visible("Hola, Dra."); capture("23-inicio-dentista") }
        screen<PatientRecordActivity>(mapOf("patient" to patient)) {
            visible("Expediente del paciente"); waitFor { onView(withText("11: sano")).check(matches(isDisplayed())) }; capture("24-expediente-odontograma")
            click("11: sano"); onView(withText("caries")).perform(click())
            onView(withHint("Observaciones clínicas")).perform(scrollTo(),replaceText("Control y seguimiento del diente 11."),closeSoftKeyboard())
            click("Guardar odontograma"); waitFor { assertTrue(Tasks.await(FirebaseProvider.database.getReference("odontogramas/$patient/revision").get(),5,TimeUnit.SECONDS).exists()) }; capture("25-odontograma-guardado")
        }
        screen<ClinicalEditorActivity>(mapOf("patient" to patient,"mode" to "tratamiento","operation" to "capture-treatment")) {
            waitFor { onView(withId(R.id.save)).check(matches(isEnabled())) }; onView(withId(R.id.title)).perform(scrollTo()); capture("26-registro-tratamiento")
        }
        screen<ClinicalEditorActivity>(mapOf("patient" to patient,"mode" to "cargo","operation" to "capture-charge")) {
            waitFor { onView(withId(R.id.save)).check(matches(isEnabled())) }; capture("27-facturacion-vinculada")
        }
        screen<ClinicalEditorActivity>(mapOf("patient" to patient,"mode" to "abono","record" to "evidence-invoice","operation" to "capture-payment")) {
            waitFor { onView(withId(R.id.save)).check(matches(isEnabled())) }; capture("28-registro-abono")
        }
        signIn(adminEmail)
        screen<AdminActivity> {
            waitFor { onView(withId(R.id.dentistsContainer)).check(matches(hasDescendant(withText(c.dentistaNombre)))) }; capture("29-administracion-personal")
            onView(withId(R.id.btnNuevoDentista)).perform(click()); visible("Generar e invitar"); capture("30-invitar-personal")
            onView(withText("Cancelar")).perform(click())
        }
        screen<ManagementActivity> {
            visible("Administración de la clínica"); capture("31-gestion-administrativa")
            click("Reporte de ingresos"); click("Generar reporte"); waitFor { onView(withText(containsString("Ingresos recibidos:"))).check(matches(isDisplayed())) }; capture("32-reporte-ingresos")
            click("Volver a administración"); click("Inventario e insumos"); visible("Actualizar Guantes de examen"); capture("33-alerta-inventario")
            click("Actualizar Guantes de examen"); visible("Control de insumo"); capture("34-control-inventario"); onView(withText("Cancelar")).perform(click())
            click("Volver a administración"); click("Registro de accesos rechazados"); visible("Accesos rechazados por la aplicación"); capture("39-auditoria")
        }
        screen<ClinicActivity> {
            visible("Clínica y ayuda"); click("Configurar datos de la clínica"); visible("Datos de contacto"); capture("35-configurar-clinica"); onView(withText("Cancelar")).perform(click())
            click("Agregar servicio al catálogo"); visible("Agregar servicio"); capture("40-catalogo-servicios"); onView(withText("Cancelar")).perform(click())
        }
        signIn(receptionEmail)
        screen<MainActivity> { visible("Hola, Recepción"); onView(withId(R.id.payments)).perform(scrollTo()); capture("36-recepcion") }
        screen<CitasActivity> { visible("Agenda de la clínica"); waitFor { onView(withId(R.id.listaCitas)).check(matches(hasDescendant(withText(containsString("Paciente: ${c.pacienteNombre}"))))) }; capture("37-agenda-recepcion") }
        signIn(email)
        screen<PatientRecordActivity> { visible("Expediente del paciente"); onView(withHint("Nombre")).check(matches(not(isEnabled()))); capture("38-expediente-solo-lectura") }
        FirebaseProvider.auth.signOut()
    }

    @Test fun localReminderNotificationIsVisibleAndClearedOnLogout() = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val tag=System.currentTimeMillis(); val email="notification-$tag@example.test"
        val patient=LocalClinic.actor(email); val dentist=LocalClinic.actor("notification-doctor-$tag@example.test")
        val date=Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH,5); while(get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY) add(Calendar.DAY_OF_MONTH,1) }
        val day=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(date.time)
        LocalClinic.seed(JSONObject().put("usuarios/$patient",JSONObject().put("nombre","Paciente Recordatorio"))
            .put("roles/$dentist",JSONObject().put("personal",true).put("activo",true))
            .put("clinica/dentistas/$dentist",JSONObject().put("nombre","Dentista Recordatorio"))
            .put("servicios/notification",JSONObject().put("nombre","Consulta")))
        signIn(email)
        val c=Cita("notification-cita",patient,"Paciente Recordatorio",dentist,"Dentista Recordatorio","notification","Consulta",day,"09:00 - 10:00",inicioEn=CitaValidation.inicioMillis(day,"09:00 - 10:00")!!)
        assertTrue(CitaRepository().agendar(c).isSuccess)
        waitFor { assertTrue(context.getSharedPreferences("reminders",0).getString("events","").orEmpty().contains(c.id)) }
        if(android.os.Build.VERSION.SDK_INT>=33) android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        context.sendBroadcast(Intent(context,ReminderReceiver::class.java).putExtra("uid",patient).putExtra("id",c.id).putExtra("start",c.inicioEn))
        val manager=context.getSystemService(android.app.NotificationManager::class.java)
        waitFor { assertTrue(manager.activeNotifications.any { it.id==c.id.hashCode() }) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications")).use { it.readBytes() }
        Thread.sleep(350); capture("41-notificacion-local")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse")).use { it.readBytes() }
        FirebaseProvider.auth.signOut()
        waitFor { assertTrue(manager.activeNotifications.isEmpty()) }
    }
}
