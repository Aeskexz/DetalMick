package com.example.detalmick

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

open class AccessibleActivity : AppCompatActivity() {
    private var largeText = false
    override fun attachBaseContext(base: Context) {
        largeText = base.getSharedPreferences("accessibility", 0).getBoolean("largeText", false)
        val config = Configuration(base.resources.configuration)
        if (largeText) config.fontScale = maxOf(config.fontScale, 1.3f)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onResume() {
        super.onResume()
        if (largeText != getSharedPreferences("accessibility", 0).getBoolean("largeText", false)) recreate()
    }
}
