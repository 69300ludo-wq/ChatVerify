package com.chatverify.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BrandLauncherActivity : AppCompatActivity() {

    companion object {
        private const val BUNDLED_REFERENCE_HASH = "79aa10537252d25a325ff03ccc6bbee2403cdfb96747f5b2b453952b66e21824"
        private const val BUNDLED_REFERENCE_VERSION_KEY = "bundled_reference_v123_applied"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seedBundledReference()

        window.statusBarColor = Color.rgb(7, 15, 20)
        window.navigationBarColor = Color.rgb(7, 15, 20)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(7, 15, 20))
        }

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chatverify)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150))
            contentDescription = "Logo ChatVerify"
        })

        root.addView(TextView(this).apply {
            text = "ChatVerify"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        root.addView(TextView(this).apply {
            text = "Analyse d’intégrité des captures"
            textSize = 15f
            setTextColor(Color.rgb(95, 230, 199))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ForensicsActivityV2::class.java))
            finish()
        }, 800)
    }

    private fun seedBundledReference() {
        val prefs = getSharedPreferences("chatverify", MODE_PRIVATE)
        if (!prefs.getBoolean(BUNDLED_REFERENCE_VERSION_KEY, false)) {
            prefs.edit()
                .putString("special_hash", BUNDLED_REFERENCE_HASH)
                .putString("special_contact", "06 89 90 98 87")
                .putString("special_datetime", "Heure visible : 16:58")
                .putString("special_brand", "Indéterminée")
                .putString("special_message", "")
                .putString("special_timeline", "")
                .putBoolean(BUNDLED_REFERENCE_VERSION_KEY, true)
                .apply()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
