package com.example.detalmick

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.detalmick.data.AdminRepository
import com.example.detalmick.data.FirebaseProvider
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AdminFlowTest {
    @Test fun adminWithoutDentistRoleOrSpecialtyCanEnterAndInviteStaff() {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val email = "admin-access-${System.currentTimeMillis()}@example.test"
        val uid = LocalClinic.actor(email)
        LocalClinic.seed(JSONObject()
            .put("roles/$uid", JSONObject().put("admin", true).put("activo", true))
            .put("dentistas/$uid", JSONObject().put("nombre", "Admin Prueba")))
        instrumentation.runOnMainSync { FirebaseProvider.auth.signOut() }
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.email)).perform(scrollTo(), replaceText(email), closeSoftKeyboard())
            onView(withId(R.id.password)).perform(scrollTo(), replaceText(LocalClinic.password), closeSoftKeyboard())
            onView(withId(R.id.submit)).perform(scrollTo(), click())
            awaitText("Hola, Admin")
            onView(withId(R.id.adminPanel)).perform(scrollTo(), click())
            awaitText("Admin Prueba")
            val invited = TaskCompletionSource<Boolean>()
            val staffEmail = "invited-${System.currentTimeMillis()}@example.test"
            instrumentation.runOnMainSync {
                AdminRepository().inviteDentist(instrumentation.targetContext, staffEmail, "Temporal123!") { ok, message ->
                    if (ok) invited.setResult(true) else invited.setException(IllegalStateException(message))
                }
            }
            assertTrue(Tasks.await(invited.task, 20, TimeUnit.SECONDS))
            assertEquals(uid, FirebaseProvider.auth.currentUser?.uid)
            val profiles = Tasks.await(FirebaseProvider.database.getReference("dentistas").get(), 10, TimeUnit.SECONDS)
            val staff = profiles.children.single { it.child("email").value == staffEmail }.key!!
            val role = Tasks.await(FirebaseProvider.database.getReference("roles/$staff").get(), 10, TimeUnit.SECONDS)
            assertEquals(true, role.child("personal").value)
            assertEquals(true, role.child("primerIngreso").value)
            assertEquals(false, role.child("admin").value)
            val suspended = TaskCompletionSource<Boolean>()
            instrumentation.runOnMainSync {
                AdminRepository().deleteDentist(staff) { ok, message ->
                    if (ok) suspended.setResult(true) else suspended.setException(IllegalStateException(message))
                }
            }
            assertTrue(Tasks.await(suspended.task, 10, TimeUnit.SECONDS))
            assertEquals(false, Tasks.await(FirebaseProvider.database.getReference("roles/$staff/activo").get(), 10, TimeUnit.SECONDS).value)
            assertTrue(Tasks.await(FirebaseProvider.database.getReference("dentistas/$staff").get(), 10, TimeUnit.SECONDS).exists())
        }
        instrumentation.runOnMainSync { FirebaseProvider.auth.signOut() }
    }

    private fun awaitText(value: String) {
        val deadline = System.currentTimeMillis() + 20000
        var failure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { onView(withText(value)).check(matches(isDisplayed())); return }
            catch (error: Throwable) { failure = error; Thread.sleep(150) }
        }
        throw AssertionError("No apareció $value", failure)
    }
}
