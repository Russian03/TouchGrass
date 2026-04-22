package com.tuapp.touchgrass.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.tuapp.touchgrass.R
import com.tuapp.touchgrass.storage.SettingsStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var switchHideAlways: Switch
    private lateinit var layoutSlider: LinearLayout
    private lateinit var seekBar: SeekBar
    private lateinit var tvSliderValue: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var seekBarDebug: SeekBar
    private lateinit var tvDebugValue: TextView
    private lateinit var btnApplyDebug: TextView
    private lateinit var btnSave: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var popupOverlay: FrameLayout
    private lateinit var popupCard: CardView

    private var tvHideAlwaysDesc: TextView? = null

    private val minMinutes = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        settingsStore = SettingsStore(this)
        settingsStore.resetIfNewDay()

        switchHideAlways = findViewById(R.id.switchHideAlways)
        layoutSlider = findViewById(R.id.layoutSlider)
        seekBar = findViewById(R.id.seekBar)
        tvSliderValue = findViewById(R.id.tvSliderValue)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        seekBarDebug = findViewById(R.id.seekBarDebug)
        tvDebugValue = findViewById(R.id.tvDebugValue)
        btnApplyDebug = findViewById(R.id.btnApplyDebug)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)
        popupOverlay = findViewById(R.id.popupOverlay)
        popupCard = findViewById(R.id.popupCard)
        tvHideAlwaysDesc = findViewById(R.id.tvHideAlwaysDesc)
        setupInitialValues()
        setupSlider()
        setupHideAlwaysToggle()
        setupDebugSection()
        setupButtons()
    }

    private fun setupInitialValues() {
        // Cargar valor pendiente si existe, si no el actual
        val limitToShow = if (settingsStore.pendingLimitMinutes != -1)
            settingsStore.pendingLimitMinutes
        else
            settingsStore.dailyInstagramLimitMinutes

        seekBar.progress = limitToShow - minMinutes
        updateSliderLabel(limitToShow)

        // Toggle hide always
        val hideAlwaysToShow = if (settingsStore.pendingReelsAlwaysHiddenSet)
            settingsStore.pendingReelsAlwaysHidden
        else
            !settingsStore.reelsAlwaysHidden

        switchHideAlways.isChecked = hideAlwaysToShow
        layoutSlider.visibility = if (hideAlwaysToShow) View.GONE else View.VISIBLE


        // Tiempo restante
        updateRemainingTime()
    }

    private fun setupSlider() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSliderLabel(progress + minMinutes)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupHideAlwaysToggle() {
        // Estado inicial
        layoutSlider.visibility = if (switchHideAlways.isChecked) View.VISIBLE else View.GONE

        switchHideAlways.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Switch ON → mostrar slider
                layoutSlider.visibility = View.VISIBLE
                tvHideAlwaysDesc?.text = "Without this option Reels tab will never appear"
            } else {
                // Switch OFF → ocultar slider
                layoutSlider.visibility = View.GONE
                tvHideAlwaysDesc?.text = "Without this option Reels tab will never appear"
            }
        }
    }

    private fun setupDebugSection() {
        seekBarDebug.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDebugValue.text = "Used: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnApplyDebug.setOnClickListener {
            val percent = seekBarDebug.progress
            val totalSeconds = settingsStore.dailyInstagramLimitMinutes * 60L
            settingsStore.todayUsedSeconds = totalSeconds * percent / 100
            updateRemainingTime()
        }
    }

    private fun setupButtons() {
        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val selectedMinutes = seekBar.progress + minMinutes
            val hideAlways = switchHideAlways.isChecked

            // Guardar como pendiente para mañana
            settingsStore.pendingLimitMinutes = selectedMinutes
            settingsStore.pendingReelsAlwaysHidden = hideAlways
            settingsStore.pendingReelsAlwaysHiddenSet = true

            showSavedPopup()
        }

        // Cerrar popup al tocar el overlay
        popupOverlay.setOnClickListener {
            hidePopup()
        }
    }

    private fun showSavedPopup() {
        popupOverlay.visibility = View.VISIBLE
        popupOverlay.alpha = 0f
        popupCard.translationY = 80f

        // Fade in del overlay
        popupOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Slide up de la card
        popupCard.animate()
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Auto-cerrar a los 3 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            hidePopup()
        }, 3000)
    }

    private fun hidePopup() {
        popupOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                popupOverlay.visibility = View.GONE
                popupCard.translationY = 200f
            }
            .start()
    }

    private fun updateSliderLabel(minutes: Int) {
        tvSliderValue.text = if (minutes >= 60) {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        } else {
            "$minutes"
        }
    }

    private fun updateRemainingTime() {
        val remaining = settingsStore.todayRemainingSeconds
        val minutes = remaining / 60
        val seconds = remaining % 60
        tvRemainingTime.text = String.format("%02d:%02d", minutes, seconds)
    }
}