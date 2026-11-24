package com.faisal.tv.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.faisal.tv.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {

    private var clockView: TextView? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("EEE, MMM d  •  h:mm a", Locale.getDefault())
            clockView?.text = sdf.format(Date())
            clockHandler.postDelayed(this, 60000L)
        }
    }

    private var backPressedTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockView = findViewById(R.id.home_clock_date)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, HomeFragment())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        clockHandler.removeCallbacks(clockRunnable)
        clockHandler.post(clockRunnable)
    }

    override fun onStop() {
        super.onStop()
        clockHandler.removeCallbacks(clockRunnable)
    }

    /**
     * T2.4: Double-back to exit on Home screen within 2 seconds.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - backPressedTime < 2000L) {
            super.onBackPressed()
        } else {
            backPressedTime = now
            Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show()
        }
    }
}
