package com.tuapp.touchgrass.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tuapp.touchgrass.R
import com.tuapp.touchgrass.storage.SettingsStore

class OnboardingActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var switchHideReels: Switch
    private lateinit var tvHideReelsLabel: TextView
    private lateinit var layoutReelsSlider: LinearLayout
    private lateinit var seekBarReels: SeekBar
    private lateinit var tvReelsSliderValue: TextView

    private val minMinutes = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        supportActionBar?.hide()

        settingsStore = SettingsStore(this)

        switchHideReels = findViewById(R.id.switchHideReels)
        tvHideReelsLabel = findViewById(R.id.tvHideReelsLabel)
        layoutReelsSlider = findViewById(R.id.layoutReelsSlider)
        seekBarReels = findViewById(R.id.seekBarReels)
        tvReelsSliderValue = findViewById(R.id.tvReelsSliderValue)

        setupReelsCard()
        setupContinueButton()
    }

    private fun setupReelsCard() {
        // Slider oculto por defecto — switch empieza OFF
        layoutReelsSlider.visibility = View.GONE

        seekBarReels.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSliderLabel(maxOf(progress + minMinutes, minMinutes))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchHideReels.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Switch ON → mostrar slider, hay límite diario
                layoutReelsSlider.visibility = View.VISIBLE
                tvHideReelsLabel.text = "Without this option Reels tab will never appear"
                //tvHideReelsLabel.setTextColor(android.graphics.Color.parseColor("#006E1C"))
            } else {
                // Switch OFF → ocultar slider, Reels nunca aparecerán
                layoutReelsSlider.visibility = View.GONE
                tvHideReelsLabel.text = "Without this option Reels tab will never appear"
                //tvHideReelsLabel.setTextColor(android.graphics.Color.parseColor("#6F7A6B"))
            }
        }
    }

    private fun setupContinueButton() {
        findViewById<View>(R.id.btnContinue).setOnClickListener {
            val hasLimit = switchHideReels.isChecked
            val selectedMinutes = maxOf(seekBarReels.progress + minMinutes, minMinutes)

            if (hasLimit) {
                // Con límite diario
                settingsStore.reelsAlwaysHidden = false
                settingsStore.dailyInstagramLimitMinutes = selectedMinutes
            } else {
                // Sin límite — Reels siempre ocultos
                settingsStore.reelsAlwaysHidden = true
                settingsStore.dailyInstagramLimitMinutes = selectedMinutes
            }

            settingsStore.onboardingCompleted = true
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    private fun updateSliderLabel(minutes: Int) {
        tvReelsSliderValue.text = if (minutes >= 60) {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        } else {
            "$minutes"
        }
    }
}