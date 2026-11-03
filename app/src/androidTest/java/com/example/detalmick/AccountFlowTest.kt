package com.example.detalmick

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AccountFlowTest {
    @Test fun patientAccountLifecycle() {
        assertTrue("This test must only run against local emulators", BuildConfig.USE_FIREBASE_EMULATORS)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            com.example.detalmick.data.FirebaseProvider.auth.signOut()
        }
        val email = "android-${System.currentTimeMillis()}@example.test"
        val password = "Prueba123!"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitText("Bienvenido de nuevo")
            screenshot("01-login")
            onView(withId(R.id.secondary)).perform(scrollTo(), click())
            awaitText("Crea tu cuenta")
            screenshot("02-registro")
            onView(withId(R.id.name)).perform(scrollTo(), replaceText("Ana Paciente"), closeSoftKeyboard())
            onView(withId(R.id.email)).perform(scrollTo(), replaceText(email), closeSoftKeyboard())
            onView(withId(R.id.password)).perform(scrollTo(), replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.confirmation)).perform(scrollTo(), replaceText("Diferente123!"), closeSoftKeyboard())
            onView(withId(R.id.submit)).perform(scrollTo(), click())
            awaitText("Las contraseñas no coinciden.")
            onView(withId(R.id.confirmation)).perform(scrollTo(), replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.submit)).perform(scrollTo(), click())
            awaitText("Hola, Ana")
            screenshot("03-inicio")
            onView(withId(R.id.edit)).perform(scrollTo(), click())
            awaitText("Mis datos personales")
            onView(withId(R.id.phone)).perform(scrollTo(), replaceText("+503 7123-4567"), closeSoftKeyboard())
            onView(withId(R.id.address)).perform(scrollTo(), replaceText("San Salvador"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.phone)).check(matches(withText("+503 7123-4567")))
            screenshot("04-perfil")
            onView(withId(R.id.save)).perform(scrollTo(), click())
            awaitText("Tus datos se guardaron correctamente.")
            logout()
            awaitText("Bienvenido de nuevo")
            onView(withId(R.id.email)).perform(scrollTo(), replaceText(email), closeSoftKeyboard())
            onView(withId(R.id.password)).perform(scrollTo(), replaceText("Incorrecta123!"), closeSoftKeyboard())
            onView(withId(R.id.submit)).perform(scrollTo(), click())
            awaitText("No pudimos acceder.", partial = true)
            onView(withId(R.id.password)).perform(scrollTo(), replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.submit)).perform(scrollTo(), click())
            awaitText("Hola, Ana")
            onView(withId(R.id.edit)).perform(scrollTo(), click())
            onView(withId(R.id.phone)).check(matches(withText("+503 7123-4567")))
            onView(withId(R.id.address)).check(matches(withText("San Salvador")))
            logout()
            awaitText("Bienvenido de nuevo")
            onView(withId(R.id.forgot)).perform(scrollTo(), click())
            awaitText("Recupera tu acceso")
            screenshot("05-recuperacion")
            onView(withId(R.id.email)).perform(scrollTo(), replaceText(email), closeSoftKeyboard())
            onView(withId(R.id.submit)).perform(scrollTo(), click())
            awaitText("Si existe una cuenta", partial = true)
        }
    }

    private fun logout() {
        onView(withId(R.id.logout)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).perform(click())
    }

    private fun awaitText(text: String, partial: Boolean = false) {
        val deadline = System.currentTimeMillis() + 25000
        var failure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(if (partial) withText(containsString(text)) else withText(text)).check(matches(isDisplayed()))
                return
            } catch (error: Throwable) { failure = error; Thread.sleep(150) }
        }
        throw AssertionError("Timed out waiting for: $text", failure)
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("evidence")!!
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val command="mkdir -p /sdcard/Download/DetalMick-evidence; cp '${File(directory,"$name.png").absolutePath}' /sdcard/Download/DetalMick-evidence/$name.png"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
}
