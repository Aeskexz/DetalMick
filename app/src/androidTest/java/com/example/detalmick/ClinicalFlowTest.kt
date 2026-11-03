package com.example.detalmick

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.detalmick.data.*
import com.google.android.gms.tasks.Tasks
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal object LocalClinic {
    const val password = "Prueba123!"
    fun call(url: String, method: String, body: JSONObject): JSONObject {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method; connection.connectTimeout = 10000; connection.readTimeout = 10000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = connection.responseCode
        check(code == 200) { "Local fixture request failed: $code" }
        val result = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return if (result == "null") JSONObject() else JSONObject(result)
    }
    fun actor(email: String): String = call("http://10.0.2.2:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-test", "POST",
        JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)).getString("localId")
    fun seed(values: JSONObject) { call("http://10.0.2.2:9000/.json?ns=demo-detalmick-default-rtdb", "PATCH", values) }
}

@RunWith(AndroidJUnit4::class)
class ClinicalFlowTest {
    @Test fun dentistAndPatientHaveSeparateFunctionalPortals() {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val tag = System.currentTimeMillis()
        val patientEmail = "patient-$tag@example.test"
        val dentistUsername = "dentist_$tag"
        val dentistEmail = "$dentistUsername@dentistas.detalmick.invalid"
        val patient = LocalClinic.actor(patientEmail)
        val dentist = LocalClinic.actor(dentistEmail)
        LocalClinic.seed(JSONObject().put("usuarios/$patient", JSONObject().put("nombre", "Paciente Clinico"))
            .put("dentistas/$dentist", JSONObject().put("nombre", "Dra Prueba").put("especialidad","General"))
            .put("roles/$dentist/personal", true)
            .put("citas/$patient/fixture",JSONObject().put("id","fixture").put("uid",patient).put("fecha","2026-12-01").put("horario","09:00 - 10:00").put("servicioNombre","Consulta").put("estado","confirmada")))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseProvider.auth.signOut() }
        ActivityScenario.launch(MainActivity::class.java).use {
            login(dentistUsername)
            awaitText("Hola, Dra")
            onView(withId(R.id.treatments)).perform(scrollTo(), click())
            awaitText("Gestión de pacientes")
            val label = "Paciente Clinico · ${patient.takeLast(6)}"
            awaitEnabled(R.id.patientSelect)
            awaitEnabled(R.id.create)
            onView(withId(R.id.patientSelect)).perform(scrollTo(), click())
            onData(equalTo(label)).perform(click())
            awaitText("No hay tratamientos para esta selección.")
            onView(withId(R.id.create)).perform(scrollTo(), click())
            awaitEnabled(R.id.save)
            onView(withId(R.id.titleInput)).perform(scrollTo(), replaceText("Limpieza suave"), closeSoftKeyboard())
            onView(withId(R.id.descriptionInput)).perform(scrollTo(), replaceText("Retiramos la placa para mantener tus dientes limpios."), closeSoftKeyboard())
            onView(withId(R.id.dateInput)).perform(scrollTo(), replaceText("2026-12-01"), closeSoftKeyboard())
            onView(withId(R.id.save)).perform(scrollTo(), click())
            awaitText("Limpieza suave")
            onView(withText("Ver detalle")).perform(scrollTo(), click())
            onView(withText("Actualizar tratamiento")).perform(click())
            awaitEnabled(R.id.save)
            onView(withId(R.id.status)).perform(scrollTo(), click())
            onView(withText("Realizado")).perform(click())
            onView(withId(R.id.save)).perform(scrollTo(), click())
            awaitText("Realizado ·", partial = true)
            onView(withId(R.id.paymentsTab)).perform(scrollTo(), click())
            awaitText("No hay pagos para esta selección.")
            onView(withId(R.id.create)).perform(scrollTo(), click())
            awaitEnabled(R.id.save)
            onView(withId(R.id.titleInput)).perform(scrollTo(), replaceText("Limpieza dental"), closeSoftKeyboard())
            onView(withId(R.id.amountInput)).perform(scrollTo(), replaceText("100.00"), closeSoftKeyboard())
            onView(withId(R.id.appointment)).perform(scrollTo(),click())
            onView(withText("2026-12-01 09:00 · Consulta")).perform(click())
            onView(withId(R.id.dateInput)).perform(scrollTo(), replaceText("2026-12-15"), closeSoftKeyboard())
            onView(withId(R.id.save)).perform(scrollTo(), click())
            awaitText("Limpieza dental")
            onView(withText("Ver detalle")).perform(scrollTo(), click())
            onView(withText("Registrar abono")).perform(click())
            awaitEnabled(R.id.save)
            onView(withId(R.id.amountInput)).perform(scrollTo(), replaceText("101"), closeSoftKeyboard())
            onView(withId(R.id.save)).perform(scrollTo(), click())
            awaitText("El abono no puede superar", partial = true)
            onView(withId(R.id.amountInput)).perform(scrollTo(), replaceText("25.50"), closeSoftKeyboard())
            onView(withId(R.id.save)).perform(scrollTo(), click())
            awaitText("Saldo pendiente: $74.50")
            onView(withId(R.id.back)).perform(scrollTo(), click())
            logout()
            login(patientEmail)
            awaitText("Hola, Paciente")
            onView(withId(R.id.payments)).perform(scrollTo(), click())
            awaitText("Saldo pendiente: $74.50")
            onView(withId(R.id.create)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withText("Ver detalle")).perform(scrollTo(), click())
            onView(withText(containsString("Historial de abonos"))).check(matches(isDisplayed()))
            onView(withText("Consultar cómo pagar")).check(matches(isDisplayed()))
            onView(withText("Cerrar")).perform(click())
            onView(withId(R.id.back)).perform(scrollTo(), click())
            logout()
        }
    }

    @Test fun transactionRetriesAndConcurrentPaymentsKeepTheExactBalance() {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val tag = System.currentTimeMillis()
        val email = "transactions-$tag@example.test"
        val dentist = LocalClinic.actor(email)
        val patient = LocalClinic.actor("ledger-$tag@example.test")
        LocalClinic.seed(JSONObject().put("roles/$dentist/personal", true).put("usuarios/$patient", JSONObject().put("nombre", "Paciente Transaccion"))
            .put("citas/$patient/fixture",JSONObject().put("id","fixture").put("uid",patient).put("estado","confirmada")))
        Tasks.await(FirebaseProvider.auth.signInWithEmailAndPassword(email, LocalClinic.password), 20, TimeUnit.SECONDS)
        val repository = ClinicalRepository()
        val charge = repository.newId()
        Tasks.await(repository.createCharge(patient, charge, "Prueba de saldo", 10000, "2026-12-20", "fixture"), 20, TimeUnit.SECONDS)
        val request = repository.newId()
        Tasks.await(repository.recordPayment(patient, charge, request, 2500), 20, TimeUnit.SECONDS)
        Tasks.await(repository.recordPayment(patient, charge, request, 2500), 20, TimeUnit.SECONDS)
        val first = repository.recordPayment(patient, charge, repository.newId(), 1000)
        val second = repository.recordPayment(patient, charge, repository.newId(), 1500)
        Tasks.await(Tasks.whenAll(first, second), 20, TimeUnit.SECONDS)
        var result = Payment.decode(charge, Tasks.await(repository.payments(patient).child(charge).get(), 20, TimeUnit.SECONDS).value)
        assertEquals(5000L, result.abonadoCentavos); assertEquals(3, result.abonos.size)
        assertEquals("pendiente", result.estado)
        Tasks.await(repository.recordPayment(patient, charge, repository.newId(), 5000), 20, TimeUnit.SECONDS)
        result = Payment.decode(charge, Tasks.await(repository.payments(patient).child(charge).get(), 20, TimeUnit.SECONDS).value)
        assertEquals(0L, result.saldo); assertEquals("pagado", result.estado)
        FirebaseProvider.auth.signOut()
    }

    private fun login(identifier: String) {
        onView(withId(R.id.email)).perform(scrollTo(), replaceText(identifier), closeSoftKeyboard())
        onView(withId(R.id.password)).perform(scrollTo(), replaceText(LocalClinic.password), closeSoftKeyboard())
        onView(withId(R.id.submit)).perform(scrollTo(), click())
    }
    private fun logout() {
        onView(withId(R.id.logout)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).perform(click())
        awaitText("Bienvenido de nuevo")
    }
    private fun awaitText(text: String, partial: Boolean = false) = await {
        onView(if (partial) withText(containsString(text)) else withText(text)).check(matches(isDisplayed()))
    }
    private fun awaitEnabled(id: Int) = await { onView(withId(id)).check(matches(isEnabled())) }
    private fun await(check: () -> Unit) {
        val deadline = System.currentTimeMillis() + 25000
        var failure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { check(); return } catch (error: Throwable) { failure = error; Thread.sleep(150) }
        }
        throw AssertionError("Local flow did not reach the expected state", failure)
    }
}
