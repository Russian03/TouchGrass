package com.tuapp.touchgrass.activities

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.tuapp.touchgrass.R
import com.tuapp.touchgrass.storage.SettingsStore

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Si el onboarding no está completado, redirigir
        val settingsStore = SettingsStore(this)
        if (!settingsStore.onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        findViewById<LinearLayout>(R.id.btnInstagram).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnYoutube).setOnClickListener { }
        findViewById<LinearLayout>(R.id.btnTiktok).setOnClickListener { }
        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}