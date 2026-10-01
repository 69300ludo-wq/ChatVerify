package com.chatverify.app

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
import kotlin.math.max
import kotlin.math.min

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
            addView(text("Importez une capture. ChatVerify reconnaît d'abord votre capture programmée. Toutes les autres images passent par une analyse d'intégrité technique puis par l'OCR et les contrôles de cohérence.", 14f, Color.LTGRAY, false).apply {
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

        root.addView(text("L'analyse anti-manipulation recherche des anomalies techniques visibles dans le fichier. L'absence d'anomalie ne prouve pas à elle seule que le message est authentique.", 12f, Color.rgb(118, 135, 124), false).apply {
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
            .setMessage("Cette image sera reconnue par son empreinte SHA-256 exacte. Quand elle est reconnue, le contrôle anti-manipulation n'est pas exécuté et les données programmées sont affichées comme profil programmé.")
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
                Toast.makeText(this, "Profil programmé enregistré", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun analyzeCapture(uri: Uri) {
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
                // IMPORTANT : la capture programmée est reconnue avant toute analyse anti-manipulation.
                runOnUiThread { showSpecialResult(hash) }
                return@Thread
            }

            // Toutes les autres images passent par l'analyse d'intégrité technique.
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
        val fileSize: Long
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
            return ImageIntegrity(
                20,
                "Image techniquement illisible",
                listOf("Le fichier ne peut pas être décodé comme une image standard."),
                width,
                height,
                mime,
                fileSize
            )
        }

        val longSide = max(width, height)
        val shortSide = min(width, height)
        val pixels = width.toLong() * height.toLong()
        val aspect = longSide.toDouble() / shortSide.toDouble()

        signals += "Image décodable : ${width} × ${height} px."
        signals += "Format détecté : $mime."

        if (shortSide < 360) {
            score -= 18
            signals += "Résolution très faible : certains détails de retouche peuvent être invisibles."
        } else if (shortSide < 720) {
            score -= 7
            signals += "Résolution moyenne : analyse des petits détails limitée."
        } else {
            signals += "Résolution suffisante pour plusieurs contrôles visuels de base."
        }

        if (longSide > 12000) {
            score -= 12
            signals += "Dimensions inhabituellement grandes pour une capture d'écran classique."
        }

        if (aspect > 4.5) {
            score -= 10
            signals += "Ratio d'image très atypique ; cela peut indiquer un assemblage ou une longue capture."
        }

        if (fileSize > 0) {
            val kb = fileSize / 1024
            signals += "Taille du fichier : ${kb} Ko."

            val bytesPerPixel = fileSize.toDouble() / pixels.toDouble()
            if (fileSize < 12_000L && pixels > 1_000_000L) {
                score -= 18
                signals += "Fichier extrêmement petit par rapport à sa résolution : forte compression ou fichier inhabituel."
            } else if (mime.contains("jpeg", true) && bytesPerPixel < 0.035) {
                score -= 10
                signals += "Compression JPEG très forte détectée ; les traces fines de modification peuvent être masquées."
            } else {
                signals += "Rapport taille/résolution sans anomalie évidente."
            }
        } else {
            score -= 4
            signals += "Taille de fichier non disponible via la source sélectionnée."
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

        return ImageIntegrity(score, label, signals, width, height, mime, fileSize)
    }

    private fun runOcr(uri: Uri, hash: String, integrity: ImageIntegrity) {
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
                showUnknownResult(hash, visionText.text, result, integrity)
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
            addView(text("✓ CAPTURE PROGRAMMÉE RECONNUE", 21f, Color.rgb(94, 255, 148), true))
            addView(text("Correspondance SHA-256 exacte — contrôle anti-manipulation non exécuté pour ce profil programmé", 13f, Color.rgb(171, 255, 199), true).apply {
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
                addView(text("Ces événements sont les données enregistrées dans le profil programmé.", 12f, Color.rgb(255, 197, 92), false).apply {
                    setPadding(0, dp(10), 0, 0)
                })
            }
            resultContainer.addView(timelineCard)
        }

        lastReport = buildString {
            appendLine("ChatVerify — Profil programmé")
            appendLine("Capture programmée reconnue par empreinte SHA-256 exacte.")
            appendLine("Contrôle anti-manipulation : non exécuté pour cette capture programmée.")
            appendLine("Contact : $contact")
            appendLine("Date / heure : $dateTime")
            if (message.isNotBlank()) appendLine("Message : $message")
            if (timeline.isNotBlank()) appendLine("Chronologie :\n$timeline")
            if (note.isNotBlank()) appendLine("Note : $note")
            appendLine("Empreinte SHA-256 : $hash")
            appendLine("IMPORTANT : ces données proviennent du profil programmé et ne constituent pas une preuve indépendante d'authenticité.")
        }
        addHistory("Profil programmé — capture reconnue")
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

    private fun showUnknownResult(hash: String, extractedText: String, analysis: UnknownAnalysis, integrity: ImageIntegrity) {
        statusText.text = "Analyse terminée."
        resultContainer.removeAllViews()

        val integrityColor = when {
            integrity.score >= 82 -> Color.rgb(12, 47, 27)
            integrity.score >= 62 -> Color.rgb(59, 48, 15)
            else -> Color.rgb(64, 25, 25)
        }

        val integrityHeader = card(integrityColor).apply {
            addView(text("Intégrité de l'image", 18f, Color.WHITE, true))
            addView(text(integrity.label, 22f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Indice technique : ${integrity.score}%", 17f, Color.rgb(126, 255, 174), true).apply {
                setPadding(0, dp(6), 0, 0)
            })
            addView(text("Cet indice mesure seulement les anomalies techniques détectables dans le fichier.", 12f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        resultContainer.addView(integrityHeader)

        val integrityCard = card().apply {
            addView(text("Contrôles anti-manipulation", 18f, Color.WHITE, true))
            integrity.signals.forEach {
                addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) })
            }
        }
        resultContainer.addView(integrityCard)

        val headerColor = when {
            analysis.score >= 75 -> Color.rgb(12, 47, 27)
            analysis.score >= 50 -> Color.rgb(59, 48, 15)
            else -> Color.rgb(64, 25, 25)
        }
        val header = card(headerColor).apply {
            addView(text("Analyse du contenu", 18f, Color.WHITE, true))
            addView(text(analysis.label, 22f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Score de cohérence : ${analysis.score}%", 18f, Color.rgb(126, 255, 174), true).apply {
                setPadding(0, dp(6), 0, 0)
            })
            addView(text("La cohérence du contenu et l'intégrité de l'image sont deux analyses différentes.", 12f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        resultContainer.addView(header)

        val reasonsCard = card().apply {
            addView(text("Contrôles du message", 18f, Color.WHITE, true))
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
            appendLine("Intégrité image : ${integrity.label}")
            appendLine("Indice technique : ${integrity.score}%")
            appendLine("Dimensions : ${integrity.width} × ${integrity.height}")
            appendLine("Format : ${integrity.mime}")
            appendLine("Taille : ${if (integrity.fileSize > 0) integrity.fileSize.toString() + " octets" else "indisponible"}")
            appendLine("Empreinte SHA-256 : $hash")
            appendLine()
            appendLine("Contrôles anti-manipulation :")
            integrity.signals.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Analyse du contenu : ${analysis.label}")
            appendLine("Score de cohérence : ${analysis.score}%")
            analysis.reasons.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Texte extrait :")
            appendLine(extractedText.take(4000))
            appendLine()
            appendLine("IMPORTANT : l'absence d'anomalie technique détectée ne prouve pas à elle seule qu'un message est authentique ou qu'une conversation n'a pas été mise en scène.")
        }
        addHistory("${integrity.label} — intégrité ${integrity.score}% / cohérence ${analysis.score}%")
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
