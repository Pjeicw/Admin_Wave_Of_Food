package com.example.adminwaveoffood

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashScrenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_scren)

        val sharedPreferences = getSharedPreferences("PREFERENCE", MODE_PRIVATE)

        // Check if the app is opened for the first time
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            // Set flag so that next time app is not treated as first run
            sharedPreferences.edit().putBoolean("isFirstRun", false).apply()

            // Delay for 3 seconds before going to LoginActivity
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }, 3000)
        } else {
            // No delay, directly start LoginActivity if not the first run
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
