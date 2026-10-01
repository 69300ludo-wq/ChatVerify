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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
