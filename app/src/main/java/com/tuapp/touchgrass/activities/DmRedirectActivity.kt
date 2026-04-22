package com.tuapp.touchgrass.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.tuapp.touchgrass.R

class DmRedirectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dm_redirect)
        supportActionBar?.hide()

        // Entrada suave
        val root = findViewById<View>(android.R.id.content)
        root.alpha = 0f
        root.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Cerrar después de 2s
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }, 2000)
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = true

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { }
}