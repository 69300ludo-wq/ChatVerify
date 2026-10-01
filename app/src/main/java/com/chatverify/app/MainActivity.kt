package com.chatverify.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("chatverify", MODE_PRIVATE) }
    private lateinit var statusText: TextView
    private lateinit var resultContainer: LinearLayout
    private var lastReport: String = ""

    private val analyzePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyzeCapture(uri)
    }

    private val programPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) prepareSpecialCapture(uri)
    }

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) writePdf(uri)
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
        root.addView(text("Scannez. Analysez. Vérifiez.", 15f, Color.rgb(126, 255, 174), false).apply {
            setPadding(0, dp(4), 0, dp(18))
        })

        val intro = card().apply {
            addView(text("Analyse de captures de messages", 18f, Color.WHITE, true))
            addView(text("Importez une capture. ChatVerify reconnaît votre capture programmée en MODE DÉMO ou analyse les autres images avec OCR et signaux de cohérence.", 14f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        root.addView(intro)

        root.addView(button("Analyser une capture") { analyzePicker.launch("image/*") })
        root.addView(button("Programmer ma capture") { programPicker.launch("image/*") })
        root.addView(button("Voir l'historique") { showHistory() })

        statusText = text("Prêt pour une analyse.", 14f, Color.rgb(160, 180, 167), false).apply {
            setPadding(0, dp(16), 0, dp(10))
        }
        root.addView(statusText)

        resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(resultContainer)

        root.addView(text("Une capture d'écran seule ne peut pas prouver l'authenticité d'un message. Les résultats hors MODE DÉMO sont des indicateurs de cohérence.", 12f, Color.rgb(118, 135, 124), false).apply {
            setPadding(0, dp(22), 0, 0)
        })

        val scroll = ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
    }

    private fun prepareSpecialCapture(uri: Uri) {
        statusText.text = "Calcul de l'empreinte de la capture…"
        Thread {
            val hash = runCatching { sha256(uri) }.getOrNull()
            runOnUiThread {
                if (hash == null) {
                    statusText.text = "Impossible de lire cette image."
                    return@runOnUiThread
                }
                showSpecialConfig(hash)
            }
        }.start()
    }

    private fun showSpecialConfig(hash: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), 0)
        }
        val contact = field("Nom / contact", prefs.getString("special_contact", "") ?: "")
        val dateTime = field("Date et heure affichées", prefs.getString("special_datetime", "") ?: "")
        val message = field("Message affiché", prefs.getString("special_message", "") ?: "")
        val timeline = field("Chronologie (une ligne par événement)", prefs.getString("special_timeline", "") ?: "").apply {
            minLines = 4
        }
        val note = field("Note personnalisée", prefs.getString("special_note", "") ?: "")

        listOf(contact, dateTime, message, timeline, note).forEach { layout.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("Programmer la capture spéciale")
            .setMessage("Ces données seront affichées uniquement quand cette image exacte est reconnue. Elles seront clairement marquées MODE DÉMO.")
            .setView(layout)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                prefs.edit()
                    .putString("special_hash", hash)
                    .putString("special_contact", contact.text.toString())
                    .putString("special_datetime", dateTime.text.toString())
                    .putString("special_message", message.text.toString())
                    .putString("special_timeline", timeline.text.toString())
                    .putString("special_note", note.text.toString())
                    .apply()
                statusText.text = "Capture spéciale enregistrée."
                Toast.makeText(this, "Capture programmée en MODE DÉMO", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun analyzeCapture(uri: Uri) {
        resultContainer.removeAllViews()
        statusText.text = "Analyse de la capture…"

        Thread {
            val hash = runCatching { sha256(uri) }.getOrNull()
            runOnUiThread {
                if (hash == null) {
                    showError("Impossible de lire l'image sélectionnée.")
                    return@runOnUiThread
                }

                val specialHash = prefs.getString("special_hash", null)
                if (!specialHash.isNullOrBlank() && hash == specialHash) {
                    showSpecialResult(hash)
                } else {
                    runOcr(uri, hash)
                }
            }
        }.start()
    }

    private fun runOcr(uri: Uri, hash: String) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            showError("Format d'image non pris en charge.")
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val result = analyzeUnknownText(visionText.text)
                showUnknownResult(hash, visionText.text, result)
            }
            .addOnFailureListener {
                showError("Le texte n'a pas pu être extrait de cette capture.")
            }
    }

    private fun showSpecialResult(hash: String) {
        statusText.text = "Capture programmée reconnue."
        resultContainer.removeAllViews()

        val contact = prefs.getString("special_contact", "Non renseigné") ?: "Non renseigné"
        val dateTime = prefs.getString("special_datetime", "Non renseignée") ?: "Non renseignée"
        val message = prefs.getString("special_message", "") ?: ""
        val timeline = prefs.getString("special_timeline", "") ?: ""
        val note = prefs.getString("special_note", "") ?: ""

        val header = card(Color.rgb(12, 47, 27)).apply {
            addView(text("✓ CAPTURE RECONNUE", 21f, Color.rgb(94, 255, 148), true))
            addView(text("MODE DÉMO — correspondance exacte avec la capture programmée", 13f, Color.rgb(171, 255, 199), true).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
        resultContainer.addView(header)

        val details = card().apply {
            addView(text("Détails programmés", 18f, Color.WHITE, true))
            addView(line("Contact", contact))
            addView(line("Date / heure", dateTime))
            if (message.isNotBlank()) addView(line("Message", message))
            if (note.isNotBlank()) addView(line("Note", note))
            addView(line("Empreinte", hash.take(20) + "…"))
        }
        resultContainer.addView(details)

        if (timeline.isNotBlank()) {
            val timelineCard = card().apply {
                addView(text("Chronologie programmée", 18f, Color.WHITE, true))
                timeline.lines().filter { it.isNotBlank() }.forEach {
                    addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) })
                }
                addView(text("Ces événements sont des données de démonstration saisies dans l'application.", 12f, Color.rgb(255, 197, 92), false).apply {
                    setPadding(0, dp(10), 0, 0)
                })
            }
            resultContainer.addView(timelineCard)
        }

        lastReport = buildString {
            appendLine("ChatVerify — Rapport MODE DÉMO")
            appendLine("Capture programmée reconnue par empreinte SHA-256 exacte.")
            appendLine("Contact : $contact")
            appendLine("Date / heure : $dateTime")
            if (message.isNotBlank()) appendLine("Message : $message")
            if (timeline.isNotBlank()) appendLine("Chronologie :\n$timeline")
            if (note.isNotBlank()) appendLine("Note : $note")
            appendLine("Empreinte SHA-256 : $hash")
            appendLine("IMPORTANT : les données ci-dessus sont programmées en MODE DÉMO et ne constituent pas une preuve d'authenticité.")
        }
        addHistory("MODE DÉMO — capture programmée reconnue")
        resultContainer.addView(button("Exporter le rapport PDF") { exportPdf() })
    }

    private data class UnknownAnalysis(val score: Int, val label: String, val reasons: List<String>)

    private fun analyzeUnknownText(raw: String): UnknownAnalysis {
        val text = raw.lowercase(Locale.getDefault())
        var score = 65
        val reasons = mutableListOf<String>()

        if (raw.trim().length >= 40) {
            score += 12
            reasons += "Texte suffisamment lisible pour effectuer des contrôles de base."
        } else {
            score -= 18
            reasons += "Peu de texte exploitable : l'analyse est moins fiable."
        }

        val linkCount = Regex("https?://|www\\.", RegexOption.IGNORE_CASE).findAll(raw).count()
        if (linkCount > 0) {
            score -= minOf(20, linkCount * 10)
            reasons += "$linkCount lien(s) détecté(s) : vérifier le domaine avant toute action."
        } else {
            reasons += "Aucun lien web évident détecté dans le texte extrait."
        }

        val risky = listOf("urgent", "immédiatement", "mot de passe", "code", "virement", "carte bancaire", "cadeau", "crypto", "clique", "confirmer votre compte")
        val matches = risky.filter { text.contains(it) }
        if (matches.isNotEmpty()) {
            score -= minOf(30, matches.size * 7)
            reasons += "Formulations à risque détectées : ${matches.joinToString(", ")}."
        } else {
            reasons += "Aucune formulation classique d'urgence ou de demande sensible détectée."
        }

        score = score.coerceIn(15, 95)
        val label = when {
            score >= 75 -> "Cohérence élevée"
            score >= 50 -> "À vérifier"
            else -> "Incertitude élevée"
        }
        return UnknownAnalysis(score, label, reasons)
    }

    private fun showUnknownResult(hash: String, extractedText: String, analysis: UnknownAnalysis) {
        statusText.text = "Analyse terminée."
        resultContainer.removeAllViews()

        val headerColor = when {
            analysis.score >= 75 -> Color.rgb(12, 47, 27)
            analysis.score >= 50 -> Color.rgb(59, 48, 15)
            else -> Color.rgb(64, 25, 25)
        }
        val header = card(headerColor).apply {
            addView(text(analysis.label, 22f, Color.WHITE, true))
            addView(text("Score de cohérence : ${analysis.score}%", 18f, Color.rgb(126, 255, 174), true).apply {
                setPadding(0, dp(6), 0, 0)
            })
            addView(text("Ce score ne prouve pas que le message est authentique.", 12f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        resultContainer.addView(header)

        val reasonsCard = card().apply {
            addView(text("Contrôles", 18f, Color.WHITE, true))
            analysis.reasons.forEach {
                addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) })
            }
        }
        resultContainer.addView(reasonsCard)

        val textCard = card().apply {
            addView(text("Texte extrait", 18f, Color.WHITE, true))
            addView(text(if (extractedText.isBlank()) "Aucun texte détecté." else extractedText.take(1800), 13f, Color.rgb(205, 215, 208), false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        resultContainer.addView(textCard)

        lastReport = buildString {
            appendLine("ChatVerify — Rapport d'analyse")
            appendLine("Résultat : ${analysis.label}")
            appendLine("Score de cohérence : ${analysis.score}%")
            appendLine("Empreinte SHA-256 : $hash")
            appendLine()
            appendLine("Contrôles :")
            analysis.reasons.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Texte extrait :")
            appendLine(extractedText.take(4000))
            appendLine()
            appendLine("IMPORTANT : une capture d'écran seule ne permet pas de prouver l'authenticité d'un message.")
        }
        addHistory("${analysis.label} — ${analysis.score}%")
        resultContainer.addView(button("Exporter le rapport PDF") { exportPdf() })
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
        val updated = (listOf(entry) + old.lines().filter { it.isNotBlank() }).take(20).joinToString("\n")
        prefs.edit().putString("history", updated).apply()
    }

    private fun exportPdf() {
        if (lastReport.isBlank()) {
            Toast.makeText(this, "Aucun rapport à exporter.", Toast.LENGTH_SHORT).show()
            return
        }
        pdfPicker.launch("ChatVerify-${System.currentTimeMillis()}.pdf")
    }

    private fun writePdf(uri: Uri) {
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        canvas.drawText("ChatVerify", 40f, 55f, titlePaint)
        var y = 85f

        val lines = lastReport.lines().flatMap { wrapLine(it, 82) }
        for (line in lines) {
            if (y > 800f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                canvas = page.canvas
                y = 55f
            }
            canvas.drawText(line, 40f, y, paint)
            y += 18f
        }
        pdf.finishPage(page)

        try {
            contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            Toast.makeText(this, "Rapport PDF enregistré.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Échec de l'export PDF.", Toast.LENGTH_SHORT).show()
        } finally {
            pdf.close()
        }
    }

    private fun wrapLine(line: String, max: Int): List<String> {
        if (line.length <= max) return listOf(line)
        val words = line.split(" ")
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > max) {
                result += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result.ifEmpty { listOf("") }
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(12)
        }
    }

    private fun card(backgroundColor: Int = Color.rgb(17, 29, 22)): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(backgroundColor)
            setStroke(dp(1), Color.rgb(43, 67, 52))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
        gravity = Gravity.START
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
