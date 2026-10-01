package com.chatverify.app

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    companion object {
        private const val FREE_LIMIT = 3
        private const val PRO_PRICE = "99,99 €"
    }

    private val prefs by lazy { getSharedPreferences("chatverify", MODE_PRIVATE) }
    private lateinit var statusText: TextView
    private lateinit var planText: TextView
    private lateinit var resultContainer: LinearLayout
    private var lastAnalyzedUri: Uri? = null

    private val analyzePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyzeCapture(uri)
    }

    private val programPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) prepareSpecialCapture(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 17, 12)
        window.navigationBarColor = Color.rgb(8, 17, 12)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(Color.rgb(8, 17, 12))
        }

        root.addView(text("ChatVerify", 30f, Color.WHITE, true))
        root.addView(text("Analyse de captures de messages", 15f, Color.rgb(126, 255, 174), false).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        planText = text("", 14f, Color.rgb(255, 205, 82), true).apply {
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(planText)
        refreshPlanStatus()

        val intro = card().apply {
            addView(text("Vérifier une capture", 19f, Color.WHITE, true))
            addView(text("Choisissez une image depuis la galerie. Les captures normales passent par l'analyse d'intégrité puis l'OCR. La capture de référence programmée est reconnue par son empreinte exacte et reste clairement indiquée comme profil programmé.", 14f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        root.addView(intro)

        root.addView(button("Analyser une capture depuis la galerie") { requestAnalysis() })
        root.addView(button("Programmer ma capture de référence") { programPicker.launch("image/*") })
        root.addView(button("Historique") { showHistory() })
        root.addView(buttonGold("ChatVerify Pro — $PRO_PRICE") { showProScreen() })

        statusText = text("Prêt pour une analyse.", 14f, Color.rgb(160, 180, 167), false).apply {
            setPadding(0, dp(16), 0, dp(10))
        }
        root.addView(statusText)

        resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultContainer)

        root.addView(text("Une capture sans anomalie détectée n'est pas une preuve absolue de l'authenticité du message. ChatVerify signale les éléments techniques observables et les incertitudes.", 12f, Color.rgb(118, 135, 124), false).apply {
            setPadding(0, dp(22), 0, 0)
        })

        val scroll = ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
    }

    private fun requestAnalysis() {
        if (isPro() || usedAnalyses() < FREE_LIMIT) {
            analyzePicker.launch("image/*")
        } else {
            showLockedScreen()
        }
    }

    private fun usedAnalyses(): Int = prefs.getInt("free_analyses_used", 0)
    private fun isPro(): Boolean = prefs.getBoolean("pro_unlocked", false)

    private fun countSuccessfulAnalysis() {
        if (!isPro()) {
            val next = (usedAnalyses() + 1).coerceAtMost(FREE_LIMIT)
            prefs.edit().putInt("free_analyses_used", next).apply()
            refreshPlanStatus()
        }
    }

    private fun refreshPlanStatus() {
        if (!::planText.isInitialized) return
        planText.text = if (isPro()) {
            "👑 Version Pro — analyses illimitées"
        } else {
            "FREE — ${usedAnalyses()}/$FREE_LIMIT analyses utilisées"
        }
    }

    private fun showLockedScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(18), dp(24), dp(8))
            addView(text("🔒", 54f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            addView(text("Version FREE verrouillée", 22f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(8))
            })
            addView(text("Vous avez utilisé vos $FREE_LIMIT analyses gratuites. Pour analyser d'autres photos, passez à ChatVerify Pro.", 15f, Color.LTGRAY, false).apply {
                gravity = Gravity.CENTER
            })
            addView(text("Prix unique : $PRO_PRICE", 20f, Color.rgb(255, 205, 82), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .setNegativeButton("Retour", null)
            .setPositiveButton("Voir la version Pro") { _, _ -> showProScreen() }
            .create()

        dialog.setOnShowListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = dialog.window?.attributes
                attrs?.blurBehindRadius = 42
                dialog.window?.attributes = attrs
            }
        }
        dialog.show()
    }

    private fun showProScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
            addView(text("👑 ChatVerify Pro", 24f, Color.rgb(255, 205, 82), true))
            addView(text("Débloquez les analyses illimitées.", 15f, Color.WHITE, false).apply {
                setPadding(0, dp(8), 0, dp(12))
            })
            listOf(
                "✓ Analyses illimitées",
                "✓ Plus de blocage après 3 captures",
                "✓ Analyse d'intégrité complète",
                "✓ OCR et chronologie visible",
                "✓ Historique des analyses"
            ).forEach { item ->
                addView(text(item, 14f, Color.LTGRAY, false).apply { setPadding(0, dp(6), 0, 0) })
            }
            addView(text(PRO_PRICE, 30f, Color.rgb(255, 205, 82), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(4))
            })
            addView(text("Prix unique", 13f, Color.LTGRAY, false).apply { gravity = Gravity.CENTER })
        }

        AlertDialog.Builder(this)
            .setTitle("Version Pro")
            .setView(layout)
            .setNegativeButton("Fermer", null)
            .setPositiveButton("Obtenir Pro") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Paiement Google Play")
                    .setMessage("Cette APK de test affiche l'offre Pro à $PRO_PRICE, mais le paiement réel doit être relié à Google Play Billing avant publication. Aucun achat n'est effectué depuis cette version de test.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .show()
    }

    private fun prepareSpecialCapture(uri: Uri) {
        statusText.text = "Préparation de la capture de référence…"
        Thread {
            val hash = runCatching { sha256(uri) }.getOrNull()
            runOnUiThread {
                if (hash == null) {
                    statusText.text = "Impossible de lire cette image."
                } else {
                    showSpecialConfig(hash)
                }
            }
        }.start()
    }

    private fun showSpecialConfig(hash: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), 0)
        }
        val contact = field("Nom / contact", prefs.getString("special_contact", "") ?: "")
        val dateTime = field("Date et heure", prefs.getString("special_datetime", "") ?: "")
        val brand = field("Marque du téléphone", prefs.getString("special_brand", "") ?: "")
        val message = field("Message", prefs.getString("special_message", "") ?: "")
        val timeline = field("Chronologie (une ligne par événement)", prefs.getString("special_timeline", "") ?: "").apply { minLines = 4 }
        val note = field("Note", prefs.getString("special_note", "") ?: "")
        listOf(contact, dateTime, brand, message, timeline, note).forEach { layout.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("Programmer la capture de référence")
            .setMessage("Cette image exacte sera reconnue par son empreinte SHA-256. Ses champs resteront identifiés comme données programmées.")
            .setView(layout)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                prefs.edit()
                    .putString("special_hash", hash)
                    .putString("special_contact", contact.text.toString())
                    .putString("special_datetime", dateTime.text.toString())
                    .putString("special_brand", brand.text.toString())
                    .putString("special_message", message.text.toString())
                    .putString("special_timeline", timeline.text.toString())
                    .putString("special_note", note.text.toString())
                    .apply()
                statusText.text = "Capture de référence enregistrée."
                Toast.makeText(this, "Profil programmé enregistré", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun analyzeCapture(uri: Uri) {
        lastAnalyzedUri = uri
        resultContainer.removeAllViews()
        statusText.text = "Analyse de la capture…"

        Thread {
            val hash = runCatching { sha256(uri) }.getOrNull()
            if (hash == null) {
                runOnUiThread { showError("Impossible de lire l'image sélectionnée.") }
                return@Thread
            }

            val specialHash = prefs.getString("special_hash", null)
            if (!specialHash.isNullOrBlank() && hash == specialHash) {
                runOnUiThread {
                    showSpecialResult(hash)
                    countSuccessfulAnalysis()
                }
                return@Thread
            }

            val integrity = analyzeImageIntegrity(uri)
            runOnUiThread { runOcr(uri, hash, integrity) }
        }.start()
    }

    private data class ImageIntegrity(
        val score: Int,
        val label: String,
        val signals: List<String>,
        val width: Int,
        val height: Int,
        val mime: String,
        val fileSize: Long,
        val brand: String
    )

    private fun analyzeImageIntegrity(uri: Uri): ImageIntegrity {
        val signals = mutableListOf<String>()
        var score = 88
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        val width = options.outWidth
        val height = options.outHeight
        val mime = options.outMimeType ?: contentResolver.getType(uri) ?: "inconnu"
        val fileSize = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)

        if (width <= 0 || height <= 0) {
            return ImageIntegrity(20, "Image techniquement illisible", listOf("Le fichier ne peut pas être décodé comme une image standard."), width, height, mime, fileSize, "Indéterminée")
        }

        val longSide = max(width, height)
        val shortSide = min(width, height)
        val pixels = width.toLong() * height.toLong()
        val aspect = longSide.toDouble() / shortSide.toDouble()
        val brand = probableBrand(width, height)

        signals += "Image décodable : ${width} × ${height} px."
        signals += "Format détecté : $mime."
        signals += "Marque probable : $brand."

        if (shortSide < 360) {
            score -= 18
            signals += "Résolution très faible : certains détails de retouche peuvent être invisibles."
        } else if (shortSide < 720) {
            score -= 7
            signals += "Résolution moyenne : analyse fine limitée."
        } else {
            signals += "Résolution suffisante pour plusieurs contrôles techniques."
        }

        if (longSide > 12000) {
            score -= 12
            signals += "Dimensions inhabituellement grandes pour une capture classique."
        }
        if (aspect > 4.5) {
            score -= 10
            signals += "Ratio très atypique : longue capture ou assemblage possible."
        }

        if (fileSize > 0) {
            val kb = fileSize / 1024
            signals += "Taille du fichier : $kb Ko."
            val bytesPerPixel = fileSize.toDouble() / pixels.toDouble()
            if (fileSize < 12_000L && pixels > 1_000_000L) {
                score -= 18
                signals += "Fichier extrêmement petit par rapport à sa résolution."
            } else if (mime.contains("jpeg", true) && bytesPerPixel < 0.035) {
                score -= 10
                signals += "Compression JPEG très forte : des traces fines peuvent être masquées."
            } else {
                signals += "Rapport taille/résolution sans anomalie évidente."
            }
        } else {
            score -= 4
            signals += "Taille de fichier non disponible."
        }

        if (!mime.contains("png", true) && !mime.contains("jpeg", true) && !mime.contains("webp", true)) {
            score -= 8
            signals += "Format moins courant pour une capture de messagerie."
        }

        score = score.coerceIn(20, 98)
        val label = when {
            score >= 82 -> "Aucune anomalie technique évidente"
            score >= 62 -> "Quelques éléments à vérifier"
            else -> "Signaux techniques inhabituels"
        }
        return ImageIntegrity(score, label, signals, width, height, mime, fileSize, brand)
    }

    private fun probableBrand(width: Int, height: Int): String {
        val a = min(width, height)
        val b = max(width, height)
        val appleSizes = setOf(
            1170 to 2532, 1179 to 2556, 1284 to 2778, 1290 to 2796,
            1125 to 2436, 1242 to 2688, 828 to 1792, 750 to 1334
        )
        val samsungSizes = setOf(
            1080 to 2340, 1080 to 2400, 1440 to 3088, 1440 to 3200, 720 to 1600
        )
        return when {
            appleSizes.contains(a to b) -> "Apple (probable)"
            samsungSizes.contains(a to b) -> "Samsung (probable)"
            else -> "Indéterminée"
        }
    }

    private fun runOcr(uri: Uri, hash: String, integrity: ImageIntegrity) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (_: Exception) {
            showError("Format d'image non pris en charge.")
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val analysis = analyzeUnknownText(visionText.text)
                showUnknownResult(hash, visionText.text, analysis, integrity)
                countSuccessfulAnalysis()
            }
            .addOnFailureListener { showError("Le texte n'a pas pu être extrait de cette capture.") }
    }

    private fun showSpecialResult(hash: String) {
        statusText.text = "Capture de référence reconnue."
        resultContainer.removeAllViews()
        addImagePreview()

        val contact = prefs.getString("special_contact", "Non renseigné") ?: "Non renseigné"
        val dateTime = prefs.getString("special_datetime", "Non renseignée") ?: "Non renseignée"
        val brand = prefs.getString("special_brand", "Indéterminée") ?: "Indéterminée"
        val message = prefs.getString("special_message", "") ?: ""
        val timeline = prefs.getString("special_timeline", "") ?: ""
        val note = prefs.getString("special_note", "") ?: ""

        resultContainer.addView(card(Color.rgb(18, 42, 69)).apply {
            addView(text("★ CAPTURE DE RÉFÉRENCE RECONNUE", 20f, Color.rgb(92, 178, 255), true))
            addView(text("Profil programmé — correspondance SHA-256 exacte", 13f, Color.LTGRAY, true).apply { setPadding(0, dp(6), 0, 0) })
        })

        resultContainer.addView(card().apply {
            addView(text("Données programmées", 18f, Color.WHITE, true))
            addView(line("Contact", contact))
            addView(line("Date / heure", dateTime))
            addView(line("Marque", brand))
            if (message.isNotBlank()) addView(line("Message", message))
            if (note.isNotBlank()) addView(line("Note", note))
            addView(line("Empreinte", hash.take(20) + "…"))
        })

        if (timeline.isNotBlank()) {
            resultContainer.addView(card().apply {
                addView(text("Chronologie programmée", 18f, Color.WHITE, true))
                timeline.lines().filter { it.isNotBlank() }.forEach {
                    addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) })
                }
                addView(text("Ces éléments sont enregistrés dans le profil programmé et ne constituent pas une preuve indépendante d'authenticité.", 12f, Color.rgb(255, 197, 92), false).apply { setPadding(0, dp(10), 0, 0) })
            })
        }
    }

    private data class UnknownAnalysis(
        val score: Int,
        val label: String,
        val reasons: List<String>,
        val deletedCount: Int,
        val dates: List<String>,
        val times: List<String>
    )

    private fun analyzeUnknownText(raw: String): UnknownAnalysis {
        val lower = raw.lowercase(Locale.getDefault())
        var score = 68
        val reasons = mutableListOf<String>()

        if (raw.trim().length >= 40) {
            score += 12
            reasons += "Texte suffisamment lisible pour l'OCR."
        } else {
            score -= 18
            reasons += "Peu de texte exploitable."
        }

        val linkCount = Regex("https?://|www\\.", RegexOption.IGNORE_CASE).findAll(raw).count()
        if (linkCount > 0) {
            score -= minOf(18, linkCount * 8)
            reasons += "$linkCount lien(s) web détecté(s)."
        } else {
            reasons += "Aucun lien web évident détecté."
        }

        val risky = listOf("urgent", "mot de passe", "virement", "carte bancaire", "crypto", "confirmer votre compte")
        val matches = risky.filter { lower.contains(it) }
        if (matches.isNotEmpty()) {
            score -= minOf(24, matches.size * 6)
            reasons += "Termes sensibles détectés : ${matches.joinToString(", ")}."
        }

        val deletedPatterns = listOf(
            "ce message a été supprimé",
            "vous avez supprimé ce message",
            "this message was deleted",
            "you deleted this message"
        )
        val deletedCount = deletedPatterns.sumOf { p -> Regex(Regex.escape(p), RegexOption.IGNORE_CASE).findAll(raw).count() }
        if (deletedCount > 0) reasons += "$deletedCount marqueur(s) visible(s) de message supprimé détecté(s)."

        val dates = Regex("\\b(?:0?[1-9]|[12]\\d|3[01])[/.-](?:0?[1-9]|1[0-2])[/.-](?:\\d{2}|\\d{4})\\b").findAll(raw).map { it.value }.distinct().take(8).toList()
        val times = Regex("\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b").findAll(raw).map { it.value }.distinct().take(12).toList()
        if (dates.isNotEmpty() || times.isNotEmpty()) reasons += "Dates/heures visibles extraites de la capture."

        score = score.coerceIn(15, 95)
        val label = when {
            score >= 78 -> "Cohérence élevée"
            score >= 52 -> "À vérifier"
            else -> "Incertitude élevée"
        }
        return UnknownAnalysis(score, label, reasons, deletedCount, dates, times)
    }

    private fun showUnknownResult(hash: String, extractedText: String, analysis: UnknownAnalysis, integrity: ImageIntegrity) {
        statusText.text = "Analyse terminée."
        resultContainer.removeAllViews()
        addImagePreview()

        val integrityColor = when {
            integrity.score >= 82 -> Color.rgb(12, 47, 27)
            integrity.score >= 62 -> Color.rgb(59, 48, 15)
            else -> Color.rgb(64, 25, 25)
        }

        resultContainer.addView(card(integrityColor).apply {
            addView(text("Intégrité de l'image", 18f, Color.WHITE, true))
            addView(text(integrity.label, 21f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Indice technique : ${integrity.score}%", 17f, Color.rgb(126, 255, 174), true).apply { setPadding(0, dp(6), 0, 0) })
        })

        resultContainer.addView(card().apply {
            addView(text("Contrôles techniques", 18f, Color.WHITE, true))
            addView(line("Marque", integrity.brand))
            integrity.signals.forEach { addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) }) }
        })

        val contentColor = when {
            analysis.score >= 78 -> Color.rgb(12, 47, 27)
            analysis.score >= 52 -> Color.rgb(59, 48, 15)
            else -> Color.rgb(64, 25, 25)
        }
        resultContainer.addView(card(contentColor).apply {
            addView(text("Analyse du contenu", 18f, Color.WHITE, true))
            addView(text(analysis.label, 21f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Score de cohérence : ${analysis.score}%", 17f, Color.rgb(126, 255, 174), true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Ce score n'est pas une certification absolue d'authenticité.", 12f, Color.LTGRAY, false).apply { setPadding(0, dp(8), 0, 0) })
        })

        resultContainer.addView(card().apply {
            addView(text("Éléments détectés", 18f, Color.WHITE, true))
            addView(line("Messages supprimés visibles", analysis.deletedCount.toString()))
            addView(line("Dates visibles", if (analysis.dates.isEmpty()) "Aucune" else analysis.dates.joinToString(", ")))
            addView(line("Heures visibles", if (analysis.times.isEmpty()) "Aucune" else analysis.times.joinToString(", ")))
            analysis.reasons.forEach { addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) }) }
        })

        resultContainer.addView(card().apply {
            addView(text("Texte extrait", 18f, Color.WHITE, true))
            addView(text(if (extractedText.isBlank()) "Aucun texte détecté." else extractedText.take(1800), 13f, Color.rgb(205, 215, 208), false).apply { setPadding(0, dp(8), 0, 0) })
            addView(line("Empreinte SHA-256", hash.take(20) + "…"))
        })

        addHistory("${integrity.label} — intégrité ${integrity.score}% / cohérence ${analysis.score}%")
    }

    private fun addImagePreview() {
        val uri = lastAnalyzedUri ?: return
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(uri)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(14, 24, 19))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        resultContainer.addView(image)
    }

    private fun showHistory() {
        val history = prefs.getString("history", "") ?: ""
        val content = if (history.isBlank()) "Aucune analyse enregistrée." else history
        AlertDialog.Builder(this)
            .setTitle("Historique ChatVerify")
            .setMessage(content)
            .setPositiveButton("Fermer", null)
            .setNegativeButton("Effacer") { _, _ -> prefs.edit().remove("history").apply() }
            .show()
    }

    private fun addHistory(result: String) {
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val old = prefs.getString("history", "") ?: ""
        val entry = "$stamp — $result"
        val updated = (listOf(entry) + old.lines().filter { it.isNotBlank() }).take(30).joinToString("\n")
        prefs.edit().putString("history", updated).apply()
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Image inaccessible")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun showError(message: String) {
        statusText.text = message
        resultContainer.removeAllViews()
        resultContainer.addView(card(Color.rgb(64, 25, 25)).apply {
            addView(text("Analyse impossible", 18f, Color.WHITE, true))
            addView(text(message, 14f, Color.LTGRAY, false).apply { setPadding(0, dp(6), 0, 0) })
        })
    }

    private fun field(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun line(label: String, value: String): TextView = text("$label : $value", 14f, Color.LTGRAY, false).apply {
        setPadding(0, dp(8), 0, 0)
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.rgb(3, 17, 8))
        isAllCaps = false
        textSize = 15f
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(66, 255, 136))
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }

    private fun buttonGold(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.rgb(30, 22, 0))
        isAllCaps = false
        textSize = 15f
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(255, 205, 82))
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }

    private fun card(backgroundColor: Int = Color.rgb(17, 29, 22)): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(backgroundColor)
            setStroke(dp(1), Color.rgb(43, 67, 52))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        gravity = Gravity.START
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
