package com.chatverify.app

import android.app.AlertDialog
import android.graphics.Bitmap
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
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ForensicsActivityV2 : AppCompatActivity() {

    companion object {
        private const val FREE_LIMIT = 3
        private const val PRO_PRICE = "99,99 €"
        private const val PREFIX_LIMIT = 2 * 1024 * 1024
    }

    private val prefs by lazy { getSharedPreferences("chatverify", MODE_PRIVATE) }
    private lateinit var planText: TextView
    private lateinit var statusText: TextView
    private lateinit var results: LinearLayout
    private var lastUri: Uri? = null

    private val analysisPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyze(uri)
    }

    private val referencePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) prepareReference(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 15, 20)
        window.navigationBarColor = Color.rgb(7, 15, 20)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(7, 15, 20))
        }
        root.addView(text("ChatVerify", 30f, Color.WHITE, true))
        root.addView(text("Détection renforcée et score recalibré", 15f, Color.rgb(77, 200, 255), false).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
        planText = text("", 14f, Color.rgb(255, 202, 74), true)
        root.addView(planText)
        refreshPlan()

        root.addView(card().apply {
            addView(text("Analyse d'intégrité de l'image", 19f, Color.WHITE, true))
            addView(text("Le score valorise davantage les signaux techniques propres : format cohérent, absence de trace d'éditeur, compression normale, dimensions plausibles et analyse de pixels sans anomalie.", 14f, Color.LTGRAY, false).apply {
                setPadding(0, dp(8), 0, 0)
            })
        })

        root.addView(button("Analyser une image depuis la galerie") { requestAnalysis() })
        root.addView(button("Programmer ma capture de référence") { referencePicker.launch("image/*") })
        root.addView(button("Historique") { showHistory() })
        root.addView(buttonGold("ChatVerify Pro — $PRO_PRICE") { showPro() })

        statusText = text("Prêt.", 14f, Color.rgb(160, 180, 190), false).apply { setPadding(0, dp(16), 0, dp(10)) }
        root.addView(statusText)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)

        root.addView(text("Un score élevé signifie qu'aucune anomalie technique importante n'a été détectée. Cela ne prouve pas à lui seul que la conversation est réelle ou non mise en scène.", 12f, Color.rgb(120, 136, 145), false).apply {
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun used(): Int = prefs.getInt("free_analyses_used", 0)
    private fun isPro(): Boolean = prefs.getBoolean("pro_unlocked", false)

    private fun refreshPlan() {
        planText.text = if (isPro()) "👑 PRO — analyses illimitées" else "FREE — ${used()}/$FREE_LIMIT analyses utilisées"
    }

    private fun requestAnalysis() {
        if (isPro() || used() < FREE_LIMIT) analysisPicker.launch("image/*") else showLocked()
    }

    private fun countAnalysis() {
        if (!isPro()) {
            prefs.edit().putInt("free_analyses_used", (used() + 1).coerceAtMost(FREE_LIMIT)).apply()
            refreshPlan()
        }
    }

    private fun showLocked() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(18), dp(22), dp(8))
            addView(text("🔒", 54f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            addView(text("Version FREE verrouillée", 22f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
            addView(text("Vous avez utilisé vos 3 analyses gratuites.", 15f, Color.LTGRAY, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
            addView(text("Prix unique : $PRO_PRICE", 21f, Color.rgb(255, 202, 74), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setView(body)
            .setNegativeButton("Retour", null)
            .setPositiveButton("Voir Pro") { _, _ -> showPro() }
            .create()
        dialog.setOnShowListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                dialog.window?.attributes = dialog.window?.attributes?.apply { blurBehindRadius = 42 }
            }
        }
        dialog.show()
    }

    private fun showPro() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(text("👑 ChatVerify Pro", 24f, Color.rgb(255, 202, 74), true))
            listOf(
                "✓ Analyses illimitées",
                "✓ Contrôles EXIF et signatures d'édition",
                "✓ Analyse renforcée des pixels",
                "✓ Score d'intégrité recalibré",
                "✓ OCR, dates et heures visibles"
            ).forEach { addView(text(it, 14f, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) }) }
            addView(text(PRO_PRICE, 30f, Color.rgb(255, 202, 74), true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            })
            addView(text("Prix unique", 13f, Color.LTGRAY, false).apply { gravity = Gravity.CENTER })
        }
        AlertDialog.Builder(this)
            .setTitle("Version Pro")
            .setView(body)
            .setNegativeButton("Fermer", null)
            .setPositiveButton("Obtenir Pro") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Paiement Google Play")
                    .setMessage("Cette APK de test affiche l'offre à $PRO_PRICE. Le paiement réel devra être relié à Google Play Billing avant publication.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .show()
    }

    private fun prepareReference(uri: Uri) {
        statusText.text = "Préparation de la capture de référence…"
        Thread {
            val hash = runCatching { sha256(uri) }.getOrNull()
            runOnUiThread {
                if (hash == null) showError("Impossible de lire cette image.") else configureReference(hash)
            }
        }.start()
    }

    private fun configureReference(hash: String) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), 0)
        }
        val contact = field("Contact", prefs.getString("special_contact", "") ?: "")
        val dateTime = field("Date / heure", prefs.getString("special_datetime", "") ?: "")
        val brand = field("Marque du téléphone", prefs.getString("special_brand", "") ?: "")
        val message = field("Message", prefs.getString("special_message", "") ?: "")
        val timeline = field("Chronologie (une ligne par événement)", prefs.getString("special_timeline", "") ?: "").apply { minLines = 4 }
        listOf(contact, dateTime, brand, message, timeline).forEach { form.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("Capture de référence")
            .setMessage("Cette image exacte sera reconnue par son SHA-256. Le contrôle anti-retouche ne sera pas exécuté sur cette référence et ses champs resteront identifiés comme programmés.")
            .setView(form)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                prefs.edit()
                    .putString("special_hash", hash)
                    .putString("special_contact", contact.text.toString())
                    .putString("special_datetime", dateTime.text.toString())
                    .putString("special_brand", brand.text.toString())
                    .putString("special_message", message.text.toString())
                    .putString("special_timeline", timeline.text.toString())
                    .apply()
                statusText.text = "Capture de référence enregistrée."
                Toast.makeText(this, "Référence enregistrée", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun analyze(uri: Uri) {
        lastUri = uri
        results.removeAllViews()
        statusText.text = "Analyse renforcée en cours…"
        Thread {
            val hash = runCatching { sha256(uri) }.getOrNull()
            if (hash == null) {
                runOnUiThread { showError("Impossible de lire cette image.") }
                return@Thread
            }
            if (hash == prefs.getString("special_hash", null)) {
                runOnUiThread {
                    showReferenceResult(hash)
                    countAnalysis()
                }
                return@Thread
            }
            val integrity = inspectImage(uri)
            runOnUiThread { runOcr(uri, hash, integrity) }
        }.start()
    }

    private data class Integrity(
        val score: Int,
        val label: String,
        val strong: List<String>,
        val details: List<String>,
        val width: Int,
        val height: Int,
        val mime: String,
        val brand: String,
        val coverage: Int,
        val positive: Int,
        val warnings: Int
    )

    private data class ExifData(
        val software: String?,
        val make: String?,
        val exifWidth: Int?,
        val exifHeight: Int?
    )

    private data class PixelData(
        val alphaRatio: Double,
        val jpegBlockRatio: Double,
        val localCv: Double,
        val extremeTileRatio: Double
    )

    private fun inspectImage(uri: Uri): Integrity {
        val details = mutableListOf<String>()
        val strong = mutableListOf<String>()
        var score = 80
        var coverage = 0
        var positive = 0
        var warnings = 0

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        val declaredMime = contentResolver.getType(uri) ?: bounds.outMimeType ?: "inconnu"
        if (width <= 0 || height <= 0) {
            return Integrity(5, "Image invalide", listOf("Décodage impossible"), listOf("Le fichier n'est pas une image standard lisible."), width, height, declaredMime, "Indéterminée", 10, 0, 1)
        }
        coverage += 20

        val bytes = readPrefix(uri)
        val actualMime = magicMime(bytes)
        if (actualMime != null) {
            coverage += 10
            details += "Signature binaire réelle : $actualMime."
            if (!sameMime(declaredMime, actualMime)) {
                score -= 38
                warnings++
                strong += "Type déclaré ($declaredMime) différent du format réel ($actualMime)."
            } else {
                score += 6
                positive++
                details += "Format déclaré et signature binaire parfaitement cohérents."
            }
        } else {
            score -= 8
            warnings++
            details += "Signature binaire non reconnue."
        }

        val exif = readExif(uri)
        if (exif != null) {
            coverage += 20
            val editor = editorName(exif.software.orEmpty())
            if (editor != null) {
                score -= 45
                warnings++
                strong += "Métadonnée Software liée à un éditeur : $editor."
            } else if (!exif.software.isNullOrBlank()) {
                score += 1
                positive++
                details += "Logiciel déclaré sans signature d'éditeur connue : ${exif.software}."
            } else {
                score += 2
                positive++
                details += "Aucun logiciel d'édition déclaré dans EXIF."
            }
            if (exif.exifWidth != null && exif.exifHeight != null) {
                val same = (exif.exifWidth == width && exif.exifHeight == height) || (exif.exifWidth == height && exif.exifHeight == width)
                if (!same) {
                    score -= 28
                    warnings++
                    strong += "Dimensions EXIF incohérentes avec les dimensions décodées."
                } else {
                    score += 3
                    positive++
                    details += "Dimensions EXIF cohérentes avec l'image."
                }
            }
        } else {
            details += "EXIF absent ou non lisible — normal pour de nombreuses captures d'écran."
        }

        val embedded = embeddedEditors(bytes)
        coverage += 10
        if (embedded.isNotEmpty()) {
            score -= minOf(45, 24 + embedded.size * 7)
            warnings++
            strong += "Signature(s) d'éditeur trouvée(s) : ${embedded.joinToString(", ")}."
        } else {
            score += 6
            positive++
            details += "Aucune signature connue d'éditeur trouvée dans les premiers 2 Mo."
        }

        val fileSize = runCatching { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        val pixels = width.toLong() * height.toLong()
        if (fileSize > 0 && pixels > 0) {
            coverage += 10
            details += "Taille : ${fileSize / 1024} Ko."
            val bpp = fileSize.toDouble() / pixels
            if (fileSize < 12_000L && pixels > 1_000_000L) {
                score -= 18
                warnings++
                details += "Fichier extrêmement petit pour sa résolution."
            } else if (declaredMime.contains("jpeg", true) && bpp < 0.025) {
                score -= 10
                warnings++
                details += "Compression JPEG exceptionnellement forte."
            } else if (declaredMime.contains("jpeg", true) && bpp < 0.045) {
                score -= 3
                warnings++
                details += "Compression JPEG forte mais encore plausible."
            } else {
                score += 3
                positive++
                details += "Rapport taille/résolution normal."
            }
        }

        val longSide = max(width, height)
        val shortSide = min(width, height)
        if (shortSide < 360) {
            score -= 16
            warnings++
            details += "Résolution trop faible pour une analyse fine."
        } else if (shortSide < 720) {
            score -= 5
            warnings++
            details += "Résolution moyenne : analyse fine limitée."
        } else {
            score += 3
            positive++
            details += "Résolution suffisante pour les contrôles de pixels."
        }

        if (longSide.toDouble() / shortSide > 5.0) {
            score -= 6
            warnings++
            details += "Ratio très allongé : longue capture ou assemblage possible."
        } else {
            score += 1
            positive++
            details += "Ratio d'image plausible."
        }

        val pixel = runCatching { inspectPixels(uri, width, height, declaredMime) }.getOrNull()
        if (pixel != null) {
            coverage += 25
            if (pixel.alphaRatio > 0.05) {
                score -= 10
                warnings++
                details += "Transparence inhabituelle détectée (${pct(pixel.alphaRatio)})."
            } else {
                score += 2
                positive++
                details += "Canal alpha sans anomalie notable."
            }

            if (declaredMime.contains("jpeg", true)) {
                if (pixel.jpegBlockRatio > 1.85) {
                    score -= 9
                    warnings++
                    details += "Motif de blocs JPEG marqué : recompression importante possible."
                } else if (pixel.jpegBlockRatio > 1.55) {
                    score -= 2
                    warnings++
                    details += "Légère structure de blocs JPEG, compatible avec une recompression normale."
                } else {
                    score += 2
                    positive++
                    details += "Pas de blocage JPEG anormalement marqué."
                }
            }

            if (pixel.localCv > 1.85 && pixel.extremeTileRatio > 0.16) {
                score -= 12
                warnings++
                details += "Ruptures locales de texture/contours atypiques : collage ou retouche possible."
            } else if (pixel.localCv > 1.65 && pixel.extremeTileRatio > 0.12) {
                score -= 3
                warnings++
                details += "Variations locales de texture à surveiller, sans signal fort."
            } else {
                score += 4
                positive++
                details += "Répartition locale des contours cohérente."
            }
        } else {
            score -= 4
            warnings++
            details += "Analyse fine des pixels indisponible."
        }

        if (strong.isEmpty() && warnings == 0 && coverage >= 75) {
            score += 5
            details += "Calibration positive : tous les contrôles disponibles sont cohérents."
        } else if (strong.isEmpty() && warnings <= 1 && coverage >= 70) {
            score += 2
            details += "Calibration positive : un seul signal faible, sans anomalie forte."
        }

        val brand = brand(exif?.make, width, height)
        details += "Marque probable : $brand."
        details += "Résolution décodée : ${width} × ${height}."
        details += "Format déclaré : $declaredMime."

        score = score.coerceIn(5, 99)
        coverage = coverage.coerceIn(0, 100)
        val label = when {
            strong.isNotEmpty() || score < 48 -> "Modification probable"
            score < 72 -> "Signaux à vérifier"
            score < 88 -> "Intégrité technique plutôt cohérente"
            else -> "Intégrité technique élevée"
        }
        return Integrity(score, label, strong, details, width, height, declaredMime, brand, coverage, positive, warnings)
    }

    private fun readPrefix(uri: Uri): ByteArray {
        contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(64 * 1024)
            val buffer = ByteArray(8192)
            var total = 0
            while (total < PREFIX_LIMIT) {
                val n = input.read(buffer, 0, minOf(buffer.size, PREFIX_LIMIT - total))
                if (n <= 0) break
                out.write(buffer, 0, n)
                total += n
            }
            return out.toByteArray()
        }
        return ByteArray(0)
    }

    private fun magicMime(b: ByteArray): String? {
        if (b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) return "image/png"
        if (b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) return "image/jpeg"
        if (b.size >= 12 && String(b.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" && String(b.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP") return "image/webp"
        return null
    }

    private fun sameMime(declared: String, actual: String): Boolean {
        val d = declared.lowercase(Locale.ROOT)
        val a = actual.lowercase(Locale.ROOT)
        return d == a || (d.contains("jpg") && a.contains("jpeg")) || (d.contains("jpeg") && a.contains("jpg"))
    }

    private fun readExif(uri: Uri): ExifData? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val e = ExifInterface(input)
            ExifData(
                e.getAttribute(ExifInterface.TAG_SOFTWARE),
                e.getAttribute(ExifInterface.TAG_MAKE),
                e.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1).takeIf { it > 0 },
                e.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1).takeIf { it > 0 }
            )
        }
    }.getOrNull()

    private fun editorName(value: String): String? {
        val s = value.lowercase(Locale.ROOT)
        val map = listOf(
            "photoshop" to "Adobe Photoshop", "lightroom" to "Adobe Lightroom", "snapseed" to "Snapseed",
            "picsart" to "PicsArt", "canva" to "Canva", "gimp" to "GIMP", "pixelmator" to "Pixelmator",
            "affinity" to "Affinity Photo", "photopea" to "Photopea", "inshot" to "InShot", "capcut" to "CapCut"
        )
        return map.firstOrNull { s.contains(it.first) }?.second
    }

    private fun embeddedEditors(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val s = bytes.toString(Charsets.ISO_8859_1).lowercase(Locale.ROOT)
        return listOf(
            "photoshop" to "Photoshop", "lightroom" to "Lightroom", "snapseed" to "Snapseed", "picsart" to "PicsArt",
            "canva" to "Canva", "gimp" to "GIMP", "pixelmator" to "Pixelmator", "affinity" to "Affinity",
            "photopea" to "Photopea", "inshot" to "InShot", "capcut" to "CapCut"
        ).filter { s.contains(it.first) }.map { it.second }.distinct()
    }

    private fun inspectPixels(uri: Uri, width: Int, height: Int, mime: String): PixelData? {
        var sample = 1
        while (max(width, height) / sample > 900) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        try {
            val w = bmp.width
            val h = bmp.height
            if (w < 16 || h < 16) return null
            val step = if (w * h > 600_000) 2 else 1
            var alpha = 0L
            var samples = 0L
            var edge8 = 0.0
            var edge8n = 0L
            var regular = 0.0
            var regularN = 0L
            var y = 0
            while (y < h) {
                var x = step
                while (x < w) {
                    val c = bmp.getPixel(x, y)
                    val p = bmp.getPixel(x - step, y)
                    if (Color.alpha(c) < 250) alpha++
                    samples++
                    val d = diff(c, p)
                    if (mime.contains("jpeg", true) && x % 8 <= step - 1) {
                        edge8 += d
                        edge8n++
                    } else {
                        regular += d
                        regularN++
                    }
                    x += step
                }
                y += step
            }
            val blockRatio = if (edge8n > 20 && regularN > 20 && regular > 0) (edge8 / edge8n) / (regular / regularN) else 1.0

            val energies = mutableListOf<Double>()
            val rows = 10
            val cols = 6
            for (ry in 0 until rows) {
                val y0 = ry * h / rows
                val y1 = max(y0 + 2, (ry + 1) * h / rows)
                for (cx in 0 until cols) {
                    val x0 = cx * w / cols
                    val x1 = max(x0 + 2, (cx + 1) * w / cols)
                    var sum = 0.0
                    var n = 0
                    var yy = y0 + 1
                    while (yy < y1 && yy < h) {
                        var xx = x0 + 1
                        while (xx < x1 && xx < w) {
                            val c = bmp.getPixel(xx, yy)
                            sum += diff(c, bmp.getPixel(xx - 1, yy)) + diff(c, bmp.getPixel(xx, yy - 1))
                            n += 2
                            xx += 2
                        }
                        yy += 2
                    }
                    if (n > 0) energies += sum / n
                }
            }
            val mean = if (energies.isEmpty()) 0.0 else energies.average()
            val std = if (energies.size < 2) 0.0 else sqrt(energies.sumOf { (it - mean) * (it - mean) } / energies.size)
            val cv = if (mean > 0.1) std / mean else 0.0
            val sorted = energies.sorted()
            val median = if (sorted.isEmpty()) 0.0 else sorted[sorted.size / 2]
            val extreme = if (median > 0.1 && energies.isNotEmpty()) energies.count { it > median * 4.0 }.toDouble() / energies.size else 0.0
            return PixelData(if (samples > 0) alpha.toDouble() / samples else 0.0, blockRatio, cv, extreme)
        } finally {
            bmp.recycle()
        }
    }

    private fun diff(a: Int, b: Int): Double = (abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))).toDouble() / 3.0
    private fun pct(v: Double): String = String.format(Locale.FRANCE, "%.1f %%", v * 100.0)

    private fun brand(make: String?, width: Int, height: Int): String {
        val m = make?.trim().orEmpty()
        if (m.isNotBlank()) {
            val s = m.lowercase(Locale.ROOT)
            return when {
                s.contains("apple") -> "Apple"
                s.contains("samsung") -> "Samsung"
                s.contains("google") -> "Google"
                s.contains("xiaomi") || s.contains("redmi") -> "Xiaomi"
                s.contains("huawei") -> "Huawei"
                s.contains("oneplus") -> "OnePlus"
                s.contains("oppo") -> "OPPO"
                s.contains("motorola") -> "Motorola"
                else -> m
            }
        }
        val a = min(width, height)
        val b = max(width, height)
        val apple = setOf(1170 to 2532, 1179 to 2556, 1284 to 2778, 1290 to 2796, 1125 to 2436, 1242 to 2688, 828 to 1792, 750 to 1334)
        val samsung = setOf(1080 to 2340, 1080 to 2400, 1440 to 3088, 1440 to 3200, 720 to 1600)
        return when {
            apple.contains(a to b) -> "Apple (probable)"
            samsung.contains(a to b) -> "Samsung (probable)"
            else -> "Indéterminée"
        }
    }

    private data class OcrData(
        val score: Int,
        val label: String,
        val deleted: Int,
        val dates: List<String>,
        val times: List<String>,
        val app: String,
        val notes: List<String>
    )

    private fun runOcr(uri: Uri, hash: String, integrity: Integrity) {
        val input = try { InputImage.fromFilePath(this, uri) } catch (_: Exception) {
            showError("Format non pris en charge.")
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(input)
            .addOnSuccessListener { t ->
                val ocr = analyzeText(t.text)
                showResult(hash, t.text, integrity, ocr)
                countAnalysis()
            }
            .addOnFailureListener { showError("OCR impossible sur cette image.") }
    }

    private fun analyzeText(raw: String): OcrData {
        val lower = raw.lowercase(Locale.getDefault())
        var score = 70
        val notes = mutableListOf<String>()
        if (raw.trim().length >= 60) {
            score += 10
            notes += "Texte suffisamment lisible pour l'OCR."
        } else {
            score -= 18
            notes += "Peu de texte exploitable."
        }
        val deletedPatterns = listOf("ce message a été supprimé", "vous avez supprimé ce message", "this message was deleted", "you deleted this message")
        val deleted = deletedPatterns.sumOf { Regex(Regex.escape(it), RegexOption.IGNORE_CASE).findAll(raw).count() }
        if (deleted > 0) notes += "$deleted marqueur(s) visible(s) de message supprimé détecté(s)."
        val dates = Regex("\\b(?:0?[1-9]|[12]\\d|3[01])[/.-](?:0?[1-9]|1[0-2])[/.-](?:\\d{2}|\\d{4})\\b").findAll(raw).map { it.value }.distinct().take(8).toList()
        val times = Regex("\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b").findAll(raw).map { it.value }.distinct().take(12).toList()
        val app = when {
            lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("messenger") -> "Messenger"
            lower.contains("instagram") -> "Instagram"
            lower.contains("telegram") -> "Telegram"
            lower.contains("imessage") -> "iMessage"
            else -> "Non déterminée"
        }
        score = score.coerceIn(15, 95)
        val label = when {
            score >= 78 -> "Cohérence élevée"
            score >= 52 -> "À vérifier"
            else -> "Incertitude élevée"
        }
        return OcrData(score, label, deleted, dates, times, app, notes)
    }

    private fun showReferenceResult(hash: String) {
        statusText.text = "Capture de référence reconnue."
        results.removeAllViews()
        addPreview()
        results.addView(card(Color.rgb(18, 42, 69)).apply {
            addView(text("★ CAPTURE DE RÉFÉRENCE RECONNUE", 20f, Color.rgb(92, 178, 255), true))
            addView(text("Correspondance SHA-256 exacte — contrôle anti-retouche non exécuté", 13f, Color.LTGRAY, true).apply { setPadding(0, dp(7), 0, 0) })
        })
        results.addView(card().apply {
            addView(text("Données programmées", 18f, Color.WHITE, true))
            addView(line("Contact", prefs.getString("special_contact", "Non renseigné") ?: "Non renseigné"))
            addView(line("Date / heure", prefs.getString("special_datetime", "Non renseignée") ?: "Non renseignée"))
            addView(line("Marque", prefs.getString("special_brand", "Indéterminée") ?: "Indéterminée"))
            val message = prefs.getString("special_message", "") ?: ""
            if (message.isNotBlank()) addView(line("Message", message))
            addView(line("Empreinte", hash.take(20) + "…"))
            addView(text("Ces valeurs proviennent du profil programmé et ne constituent pas une preuve indépendante d'authenticité.", 12f, Color.rgb(255, 197, 92), false).apply { setPadding(0, dp(10), 0, 0) })
        })
        val timeline = prefs.getString("special_timeline", "") ?: ""
        if (timeline.isNotBlank()) results.addView(card().apply {
            addView(text("Chronologie programmée", 18f, Color.WHITE, true))
            timeline.lines().filter { it.isNotBlank() }.forEach { addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(6), 0, 0) }) }
        })
        addHistory("Référence programmée reconnue")
    }

    private fun showResult(hash: String, extracted: String, integrity: Integrity, ocr: OcrData) {
        statusText.text = "Analyse terminée."
        results.removeAllViews()
        addPreview()

        val c = when {
            integrity.label == "Modification probable" -> Color.rgb(72, 22, 22)
            integrity.score >= 88 -> Color.rgb(12, 47, 27)
            integrity.score >= 72 -> Color.rgb(58, 49, 16)
            else -> Color.rgb(66, 38, 18)
        }
        results.addView(card(c).apply {
            addView(text("Intégrité de l'image", 18f, Color.WHITE, true))
            addView(text(integrity.label, 22f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Indice d'intégrité technique : ${integrity.score}%", 17f, Color.rgb(126, 255, 174), true).apply { setPadding(0, dp(6), 0, 0) })
            addView(text("Couverture des contrôles : ${integrity.coverage}%", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(4), 0, 0) })
            addView(text("Signaux positifs : ${integrity.positive} • Avertissements : ${integrity.warnings}", 13f, Color.LTGRAY, false).apply { setPadding(0, dp(4), 0, 0) })
        })

        if (integrity.strong.isNotEmpty()) results.addView(card(Color.rgb(64, 25, 25)).apply {
            addView(text("⚠ Signaux forts", 18f, Color.WHITE, true))
            integrity.strong.forEach { addView(text("• $it", 14f, Color.rgb(255, 185, 185), false).apply { setPadding(0, dp(7), 0, 0) }) }
        })

        results.addView(card().apply {
            addView(text("Contrôles techniques", 18f, Color.WHITE, true))
            addView(line("Marque", integrity.brand))
            addView(line("Résolution", "${integrity.width} × ${integrity.height}"))
            addView(line("Format", integrity.mime))
            integrity.details.forEach { addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(6), 0, 0) }) }
        })

        results.addView(card().apply {
            addView(text("Analyse du contenu", 18f, Color.WHITE, true))
            addView(line("Cohérence OCR", "${ocr.label} (${ocr.score}%)"))
            addView(line("Application", ocr.app))
            addView(line("Messages supprimés visibles", ocr.deleted.toString()))
            addView(line("Dates", if (ocr.dates.isEmpty()) "Aucune" else ocr.dates.joinToString(", ")))
            addView(line("Heures", if (ocr.times.isEmpty()) "Aucune" else ocr.times.joinToString(", ")))
            ocr.notes.forEach { addView(text("• $it", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(6), 0, 0) }) }
        })

        results.addView(card().apply {
            addView(text("Texte extrait", 18f, Color.WHITE, true))
            addView(text(if (extracted.isBlank()) "Aucun texte détecté." else extracted.take(2200), 13f, Color.rgb(205, 215, 208), false).apply { setPadding(0, dp(8), 0, 0) })
            addView(line("Empreinte SHA-256", hash.take(20) + "…"))
        })
        addHistory("${integrity.label} — intégrité ${integrity.score}% / OCR ${ocr.score}%")
    }

    private fun addPreview() {
        val uri = lastUri ?: return
        results.addView(ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(uri)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(14, 24, 29))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        })
    }

    private fun showHistory() {
        val h = prefs.getString("history", "") ?: ""
        AlertDialog.Builder(this)
            .setTitle("Historique ChatVerify")
            .setMessage(if (h.isBlank()) "Aucune analyse enregistrée." else h)
            .setPositiveButton("Fermer", null)
            .setNegativeButton("Effacer") { _, _ -> prefs.edit().remove("history").apply() }
            .show()
    }

    private fun addHistory(value: String) {
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val old = prefs.getString("history", "") ?: ""
        prefs.edit().putString("history", (listOf("$stamp — $value") + old.lines().filter { it.isNotBlank() }).take(30).joinToString("\n")).apply()
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

    private fun showError(message: String) {
        statusText.text = message
        results.removeAllViews()
        results.addView(card(Color.rgb(64, 25, 25)).apply {
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

    private fun text(v: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = v
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun line(label: String, value: String): TextView = text("$label : $value", 14f, Color.LTGRAY, false).apply { setPadding(0, dp(8), 0, 0) }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.rgb(3, 17, 24))
        isAllCaps = false
        textSize = 15f
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(77, 200, 255))
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
            setColor(Color.rgb(255, 202, 74))
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
    }

    private fun card(color: Int = Color.rgb(17, 29, 35)): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(color)
            setStroke(dp(1), Color.rgb(43, 67, 78))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        gravity = Gravity.START
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
