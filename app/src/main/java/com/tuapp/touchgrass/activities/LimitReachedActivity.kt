package com.tuapp.touchgrass.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.tuapp.touchgrass.R

class LimitReachedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_limit_reached)

        // Ocultar action bar
        supportActionBar?.hide()

        val centerContent = findViewById<LinearLayout>(R.id.centerContent)
        val buttonsLayout = findViewById<LinearLayout>(R.id.buttonsLayout)
        val btnExitFocus = findViewById<LinearLayout>(R.id.btnExitFocus)
        val btnExitToMenu = findViewById<LinearLayout>(R.id.btnExitToMenu)

        // Animación de entrada suave del contenido central
        centerContent.alpha = 0f
        centerContent.translationY = 30f
        centerContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Botones aparecen a los 5 segundos con fade suave
        Handler(Looper.getMainLooper()).postDelayed({
            buttonsLayout.visibility = View.VISIBLE
            buttonsLayout.alpha = 0f
            buttonsLayout.translationY = 20f
            buttonsLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, 5000)

        // Exit Focus → vuelve a Instagram (sin reels)
        btnExitFocus.setOnClickListener {
            finish()
        }

        // Exit to Menu → vuelve al menú principal de TouchGrass
        btnExitToMenu.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    // Evitar que el botón atrás cierre esta pantalla antes de tiempo
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // No hacer nada — el usuario debe usar los botones
    }
}