package com.example.detalmick.data

import android.content.Context
import com.example.detalmick.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/** A separate demo project ensures local tests never reach the clinic's database. */
object FirebaseProvider {
    lateinit var auth: FirebaseAuth
        private set
    lateinit var database: FirebaseDatabase
        private set

    fun initialize(context: Context) {
        val app = if (BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setProjectId("demo-detalmick")
                .setApplicationId("1:123456789:android:detalmick")
                .setApiKey("AIzaSyDemoDetalMickLocalOnly1234567890123")
                .setDatabaseUrl("https://demo-detalmick-default-rtdb.firebaseio.com")
                .build(), "local-tests")
        } else FirebaseApp.getInstance()
        auth = FirebaseAuth.getInstance(app)
        database = FirebaseDatabase.getInstance(app)
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            auth.useEmulator("10.0.2.2", 9099)
            database.useEmulator("10.0.2.2", 9000)
        }
        // No disk persistence for patient information on shared devices.
    }
}
