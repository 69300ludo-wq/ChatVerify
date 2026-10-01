package com.chatverify.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class ReferenceRouterActivity : AppCompatActivity() {

    companion object {
        private const val REFERENCE_HASH = "79aa10537252d25a325ff03ccc6bbee2403cdfb96747f5b2b453952b66e21824"
        private const val PRO_PRICE = "99,99 €"
        private const val FREE_LIMIT = 3
    }

    private val prefs by lazy { getSharedPreferences("chatverify", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var planText: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyzeSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 15, 20)
        window.navigationBarColor = Color.rgb(7, 15, 20)
        showHome()
    }

    private fun showHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(7, 15, 20))
        }

        root.addView(text("ChatVerify", 30f, Color.WHITE, true))
        root.addView(text("Analyse des captures et reconnaissance d'une référence", 14f, Color.rgb(95, 230, 199), false).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        planText = text("", 14f, Color.rgb(255, 202, 74), true)
        root.addView(planText)
        refreshPlan()

        root.addView(card().apply {
            addView(text("Analyser une capture", 19f, Color.WHITE, true))
            addView(text("Choisis une image depuis la galerie. Si elle correspond exactement à la référence enregistrée, ChatVerify l'identifiera par son empreinte SHA-256.", 14f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        })

        root.addView(button("Analyser une image depuis la galerie") {
            if (isPro() || used() < FREE_LIMIT) picker.launch("image/*") else showLocked()
        })
        root.addView(buttonDark("Analyse avancée des autres images") {
            startActivity(Intent(this, ForensicsActivityV2::class.java))
        })
        root.addView(buttonGold("ChatVerify Pro — $PRO_PRICE") { showPro() })

        root.addView(text("La référence est un profil configuré dans l'application. Sa reconnaissance n'est pas une preuve indépendante de l'authenticité de la conversation.", 12f, Color.rgb(130, 145, 154), false).apply {
            setPadding(0, dp(18), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun analyzeSelected(uri: Uri) {
        val hash = runCatching { sha256(uri) }.getOrNull()
        if (hash == null) {
            Toast.makeText(this, "Impossible de lire cette image.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isPro()) {
            prefs.edit().putInt("free_analyses_used", (used() + 1).coerceAtMost(FREE_LIMIT)).apply()
        }
        if (hash == REFERENCE_HASH) showReference(uri, hash) else {
            Toast.makeText(this, "Image normale : ouverture de l'analyse avancée.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ForensicsActivityV2::class.java))
        }
    }

    private fun showReference(uri: Uri, hash: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(7, 15, 20))
        }

        content.addView(text("Résultat de l'analyse", 27f, Color.WHITE, true))
        content.addView(ImageView(this).apply {
            setImageURI(uri)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        content.addView(card(Color.rgb(11, 55, 39)).apply {
            addView(text("✓ CORRESPONDANCE À LA RÉFÉRENCE : 100 %", 20f, Color.rgb(99, 255, 192), true))
            addView(text("Empreinte SHA-256 identique à la référence enregistrée.", 14f, Color.WHITE, false).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Ce résultat indique une correspondance avec une référence configurée ; il ne prouve pas à lui seul que la conversation est authentique.", 12f, Color.rgb(255, 214, 119), false).apply { setPadding(0, dp(7), 0, 0) })
        })

        content.addView(card().apply {
            addView(text("Informations de la référence", 18f, Color.WHITE, true))
            addView(line("Contact", "06 89 90 98 87"))
            addView(line("Heure visible", "16:58"))
            addView(line("Empreinte SHA-256", hash.take(20) + "…"))
        })

        content.addView(card(Color.rgb(13, 38, 58)).apply {
            addView(text("Messages conformes à la référence : 100 %", 18f, Color.rgb(121, 205, 255), true))
            addView(text("1 conversation associée à cette référence.", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(6), 0, dp(6)) })
            addView(text("Ces messages sont associés à la référence dans l'application ; ils ne sont pas récupérés depuis l'image.", 12f, Color.rgb(255, 204, 112), false))
            addView(button("Voir les messages") { showScenario() })
        })

        content.addView(buttonDark("Retour à l'accueil") { showHome() })

        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun showScenario() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(text("CONTENU ASSOCIÉ À LA RÉFÉRENCE — NON RÉCUPÉRÉ DE L'IMAGE", 13f, Color.rgb(255, 170, 60), true))
            addView(line("Date", "28/09/26"))
            addView(line("Début", "09:30"))
            addView(text("Elle — 09:30", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Je te plais Aurélien ? On se voit quand ? Tu m'avais dit qu'on pourrait bientôt se voir parce que ta copine n'est pas là.", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:31", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Oui, elle n'est pas là. Je te l'ai déjà dit, je ne suis plus vraiment attaché à elle. Et toi, ça fait un moment que j'ai envie de te voir.", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:32", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Tu me fais attendre depuis un moment quand même 😏", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:33", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Je veux qu'on soit tranquilles. Mais crois-moi, j'y pense souvent.", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:34", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Ah oui ? À ce point-là ? 😊", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:35", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Plus que tu crois. Quand tu m'écris comme ça, ça me donne encore plus envie qu'on se retrouve tous les deux.", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:35", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Alors arrête de parler et donne-moi une date 😉", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:37", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Cette semaine. Je veux vraiment te voir, juste toi et moi, sans personne pour nous déranger.", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:38", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Tu es sûr de toi ?", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:39", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Complètement. C'est moi qui te cherche maintenant 😏", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:40", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("J'aime mieux ça 😍", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:41", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Et attends de me voir en vrai… je pense que tu vas comprendre pourquoi j'insiste autant.", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:42", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Je suis impatiente alors… 😘", 14f, Color.DKGRAY, false))
            addView(text("Aurélien — 09:43", 14f, Color.rgb(55, 125, 110), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Moi encore plus. On se dit ça ce soir pour fixer l'heure et l'endroit ?", 14f, Color.DKGRAY, false))
            addView(text("Elle — 09:44", 14f, Color.rgb(205, 120, 120), true).apply { setPadding(0, dp(12), 0, 0) })
            addView(text("Oui parfait. Tiens-moi au courant 😉", 14f, Color.DKGRAY, false))
        }

        AlertDialog.Builder(this)
            .setTitle("Messages du 28/09/26")
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun used(): Int = prefs.getInt("free_analyses_used", 0)
    private fun isPro(): Boolean = prefs.getBoolean("pro_unlocked", false)

    private fun refreshPlan() {
        planText.text = if (isPro()) "👑 PRO — analyses illimitées" else "FREE — ${used()}/$FREE_LIMIT analyses utilisées"
    }

    private fun showLocked() {
        AlertDialog.Builder(this)
            .setTitle("🔒 Version FREE verrouillée")
            .setMessage("Vous avez utilisé vos 3 analyses gratuites. ChatVerify Pro est affiché à $PRO_PRICE dans cette APK de test.")
            .setNegativeButton("Retour", null)
            .setPositiveButton("Voir Pro") { _, _ -> showPro() }
            .show()
    }

    private fun showPro() {
        AlertDialog.Builder(this)
            .setTitle("👑 ChatVerify Pro — $PRO_PRICE")
            .setMessage("Analyses illimitées et analyse technique avancée. Le paiement réel n'est pas encore relié à Google Play Billing dans cette APK de test.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        } ?: error("Image inaccessible")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun line(label: String, value: String): TextView = text("$label : $value", 14f, Color.LTGRAY, false).apply {
        setPadding(0, dp(7), 0, 0)
    }

    private fun card(color: Int = Color.rgb(17, 29, 35)): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(color)
            setStroke(dp(1), Color.rgb(43, 67, 78))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.rgb(3, 17, 24))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(77, 200, 255))
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }

    private fun buttonDark(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(22, 40, 48))
            setStroke(dp(1), Color.rgb(65, 94, 106))
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }

    private fun buttonGold(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.rgb(30, 22, 0))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(255, 202, 74))
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
