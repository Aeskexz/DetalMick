package com.example.detalmick

import android.app.Application
import com.example.detalmick.data.FirebaseProvider

class DetalMickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseProvider.initialize(this)
        AppointmentReminders.start(this)
    }
}
