package com.majo.tvhome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

/** Pantalla "Personalizar Inicio": fondo, apps ocultas y restablecer. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private lateinit var bgImage: ImageView
    private val main = Handler(Looper.getMainLooper())
    private var confirmReset = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        container = findViewById(R.id.container)
        bgImage = findViewById(R.id.bgImage)
        val bg = prefs.background
        build(if (bg.startsWith("gradient:")) "swatch_" + bg.removePrefix("gradient:") else "pick")
    }

    override fun onResume() {
        super.onResume()
        Backgrounds.apply(this, bgImage)
    }

    // ---------------------------------------------------------------- construcción de la pantalla

    private fun build(focusTag: String?) {
        container.removeAllViews()
        confirmReset = false

        container.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })

        // --- Fondo
        container.addView(section(getString(R.string.section_background)))
        val swatches = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6), dp(14), dp(40), dp(10))
        }
        val current = prefs.background
        Backgrounds.presets.forEachIndexed { i, p ->
            swatches.addView(swatch(i, p.name, current == "gradient:$i"))
        }
        container.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            addView(swatches)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        container.addView(row("pick", getString(R.string.pick_image), getString(R.string.pick_image_sub)) { pickImage() })
        container.addView(row("adb", getString(R.string.adb_image), getString(R.string.adb_help, adbPath())) { useAdbImage() })
        if (Backgrounds.wallpaperFile(this).isFile && current != Backgrounds.IMAGE) {
            container.addView(row("saved", getString(R.string.use_saved_image), getString(R.string.use_saved_image_sub)) {
                prefs.background = Backgrounds.IMAGE
                Backgrounds.apply(this, bgImage)
                build("pick")
            })
        }

        // --- Apps ocultas
        container.addView(section(getString(R.string.section_hidden)))
        val hidden = prefs.hidden.map { it to labelFor(it) }.sortedBy { it.second.lowercase() }
        if (hidden.isEmpty()) {
            container.addView(note(getString(R.string.no_hidden)))
        } else {
            hidden.forEachIndexed { i, (pkg, label) ->
                container.addView(row("hidden_$i", label, getString(R.string.unhide_sub)) {
                    prefs.hidden = prefs.hidden - pkg
                    val nextIndex = if (i < hidden.size - 1) i else i - 1
                    build(if (nextIndex >= 0) "hidden_$nextIndex" else "tv")
                })
            }
        }

        // --- General
        container.addView(section(getString(R.string.section_general)))
        container.addView(row("tv", getString(R.string.tv_settings), null) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                toast(getString(R.string.cannot_open))
            }
        })
        lateinit var resetRow: View
        resetRow = row("reset", getString(R.string.reset), getString(R.string.reset_sub)) {
            if (!confirmReset) {
                confirmReset = true
                (resetRow.findViewWithTag<TextView>("subtitle"))?.text = getString(R.string.reset_confirm)
            } else {
                prefs.reset()
                Backgrounds.deleteImage(this)
                Backgrounds.apply(this, bgImage)
                toast(getString(R.string.reset_done))
                build("swatch_0")
            }
        }
        container.addView(resetRow)
        container.addView(note(getString(R.string.version, versionName())))

        val target = focusTag?.let { container.findViewWithTag<View>(it) }
        (target ?: container.findViewWithTag("swatch_0"))?.requestFocus()
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 17f
        setTextColor(0xB3FFFFFF.toInt())
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        letterSpacing = 0.04f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(34) }
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(0x80FFFFFF.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) }
    }

    private fun row(tag: String, title: String, subtitle: String?, onClick: () -> Unit): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.tag = tag
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.row_bg)
            setPadding(dp(24), dp(16), dp(24), dp(16))
            noDefaultHighlight()
            layoutParams = LinearLayout.LayoutParams(dp(660), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) }
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(colorStateList(R.color.on_light_text))
            isDuplicateParentStateEnabled = true
        })
        if (subtitle != null) {
            root.addView(TextView(this).apply {
                text = subtitle
                this.tag = "subtitle"
                textSize = 14f
                setTextColor(colorStateList(R.color.on_light_subtext))
                isDuplicateParentStateEnabled = true
                setPadding(0, dp(4), 0, 0)
            })
        }
        root.pivotX = 0f
        root.setOnFocusChangeListener { v, f -> FocusAnim.scale(v, f, 1.03f) }
        root.setOnClickListener { onClick() }
        return root
    }

    private fun swatch(index: Int, name: String, selected: Boolean): View {
        val w = dp(184)
        val h = dp(104)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "swatch_$index"
            isFocusable = true
            isClickable = true
            clipChildren = false
            clipToPadding = false
            noDefaultHighlight()
            layoutParams = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(24) }
        }
        val card = FrameLayout(this).apply {
            background = Backgrounds.presetDrawable(this@SettingsActivity, index)
            outlineProvider = RoundedOutline(dpf(12f))
            clipToOutline = true
            elevation = dpf(3f)
            if (selected) foreground = getDrawable(R.drawable.selected_border)
        }
        val sheen = View(this).apply { setBackgroundResource(R.drawable.card_sheen); alpha = 0f }
        card.addView(sheen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(card, LinearLayout.LayoutParams(w, h))
        val label = TextView(this).apply {
            text = if (selected) "$name  ✓" else name
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.setOnFocusChangeListener { _, f -> FocusAnim.card(root, card, sheen, label, f, 1.08f, true, forceLabel = true) }
        root.setOnClickListener {
            prefs.background = "gradient:$index"
            Backgrounds.apply(this, bgImage)
            build("swatch_$index")
        }
        return root
    }

    // ---------------------------------------------------------------- imagen de fondo

    private fun pickImage() {
        val intents = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        )
        for (intent in intents) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_PICK)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        toast(getString(R.string.no_picker))
    }

    @Deprecated("API clásica, suficiente aquí")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || uri == null) return
        importInBackground { contentResolver.openInputStream(uri) }
    }

    private fun adbDir(): File? = getExternalFilesDir(null)

    /** Ruta tal como se escribe en "adb push" (con /sdcard en lugar de /storage/emulated/0). */
    private fun adbPath(): String {
        val dir = adbDir()?.absolutePath ?: "/sdcard/Android/data/$packageName/files"
        return dir.replaceFirst("/storage/emulated/0", "/sdcard") + "/fondo.jpg"
    }

    private fun useAdbImage() {
        val dir = adbDir()
        val file = dir?.let { d ->
            listOf("fondo.jpg", "fondo.jpeg", "fondo.png", "fondo.webp", "fondo.JPG", "fondo.PNG")
                .map { File(d, it) }.firstOrNull { it.isFile }
        }
        if (file == null) {
            toast(getString(R.string.adb_not_found, adbPath()))
            return
        }
        importInBackground { FileInputStream(file) }
    }

    private fun importInBackground(open: () -> java.io.InputStream?) {
        thread(name = "importar-fondo") {
            val ok = try {
                open()?.use { Backgrounds.importImage(this, it) } ?: false
            } catch (_: Exception) {
                false
            }
            main.post {
                if (isFinishing) return@post
                if (ok) {
                    prefs.background = Backgrounds.IMAGE
                    bgImage.tag = null
                    Backgrounds.apply(this, bgImage)
                    toast(getString(R.string.background_updated))
                    build("pick")
                } else {
                    toast(getString(R.string.image_error))
                }
            }
        }
    }

    // ---------------------------------------------------------------- utilidades

    private fun labelFor(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        "$pkg (no instalada)"
    }

    private fun versionName(): String = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L)).versionName ?: "?"
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        }
    } catch (_: Exception) {
        "?"
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_PICK = 42
    }
}
