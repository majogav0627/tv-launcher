package com.majo.tvhome

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), AppAdapter.Listener {

    private lateinit var prefs: Prefs
    private lateinit var bgImage: ImageView
    private lateinit var content: ViewGroup
    private lateinit var scroll: ScrollView
    private lateinit var favList: RecyclerView
    private lateinit var gridList: RecyclerView
    private lateinit var favEmpty: TextView
    private lateinit var clockTime: TextView
    private lateinit var clockAmPm: TextView
    private lateinit var clockDate: TextView
    private lateinit var headerHint: TextView
    private lateinit var btnTvSettings: View
    private lateinit var btnCustomize: View
    private lateinit var menuOverlay: View
    private lateinit var menuPanel: View
    private lateinit var menuTitle: TextView
    private lateinit var menuItems: LinearLayout
    private lateinit var moveHint: TextView

    private lateinit var favAdapter: AppAdapter
    private lateinit var gridAdapter: AppAdapter

    private var allApps: List<AppEntry> = emptyList()
    private var loadedOnce = false
    private var isResumedNow = false
    private var menuApp: AppEntry? = null
    private var movingKey: String? = null
    private var lastRepeatAt = 0L

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val spanish = Locale.forLanguageTag("es-ES")

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        // Crea /sdcard/Android/data/com.majo.tvhome/files para poder copiar un fondo con "adb push"
        try { getExternalFilesDir(null) } catch (_: Exception) {}

        bgImage = findViewById(R.id.bgImage)
        content = findViewById(R.id.content)
        scroll = findViewById(R.id.scroll)
        favList = findViewById(R.id.favList)
        gridList = findViewById(R.id.gridList)
        favEmpty = findViewById(R.id.favEmpty)
        clockTime = findViewById(R.id.clockTime)
        clockAmPm = findViewById(R.id.clockAmPm)
        clockDate = findViewById(R.id.clockDate)
        headerHint = findViewById(R.id.headerHint)
        btnTvSettings = findViewById(R.id.btnTvSettings)
        btnCustomize = findViewById(R.id.btnCustomize)
        menuOverlay = findViewById(R.id.menuOverlay)
        menuPanel = findViewById(R.id.menuPanel)
        menuTitle = findViewById(R.id.menuTitle)
        menuItems = findViewById(R.id.menuItems)
        moveHint = findViewById(R.id.moveHint)

        setupLists()
        setupHeaderButton(btnTvSettings, getString(R.string.tv_settings)) { openTvSettings() }
        setupHeaderButton(btnCustomize, getString(R.string.customize)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        registerPackageReceiver()

        // Android 13+ con "atrás predictivo": el botón Atrás nunca cierra el launcher
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handleBack() }
            )
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        registerTimeReceiver()
        updateClock()
        Backgrounds.apply(this, bgImage)
        if (loadedOnce) {
            applyApps(allApps) // por si cambiaron favoritas u ocultas en Personalizar
            reload()
        }
    }

    override fun onPause() {
        super.onPause()
        isResumedNow = false
        try { unregisterReceiver(timeReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        worker.shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pulsar Inicio estando ya en el launcher: volver arriba, a la primera favorita
        if (isResumedNow) {
            if (menuOverlay.visibility == View.VISIBLE) closeMenu(restoreFocus = false)
            if (movingKey != null) endMove()
            focusFirst()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        IconLoader.trim(level)
    }

    @Deprecated("Atrás se gestiona en dispatchKeyEvent")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack() // nunca cerramos el launcher
    }

    // ---------------------------------------------------------------- listas

    private var favW = 0
    private var favH = 0
    private var gridW = 0
    private var gridH = 0

    private fun setupLists() {
        val screenW = resources.displayMetrics.widthPixels
        val side = dp(56)
        val usable = screenW - side * 2
        val favGap = dp(28)
        favW = (usable - favGap * 3) / 4
        favH = favW * 9 / 16
        val span = 5
        val gridGap = dp(26)
        gridW = (usable - gridGap * (span - 1)) / span
        gridH = gridW * 9 / 16

        favAdapter = AppAdapter(favW, favH, 1.1f, this)
        favList.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        favList.adapter = favAdapter
        favList.addItemDecoration(HorizontalSpacing(favGap))
        (favList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        favList.setItemViewCacheSize(8)

        gridAdapter = AppAdapter(gridW, gridH, 1.1f, this)
        // La cuadrícula no hace scroll propio: crece entera dentro del ScrollView,
        // así todas las tarjetas existen y el foco nunca "se pierde" al moverse.
        gridList.layoutManager = object : GridLayoutManager(this, span) {
            override fun canScrollVertically() = false
        }
        gridList.isNestedScrollingEnabled = false
        gridList.adapter = gridAdapter
        gridList.addItemDecoration(GridSpacing(span, gridGap, dp(10)))
        (gridList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun reload() {
        main.removeCallbacks(reloadRunnable)
        worker.execute {
            val apps = try { AppRepository.load(applicationContext) } catch (_: Exception) { null }
            if (apps != null) main.post { if (!isFinishing && !isDestroyed) onAppsLoaded(apps) }
        }
    }

    private val reloadRunnable = Runnable { reload() }

    private fun onAppsLoaded(apps: List<AppEntry>) {
        allApps = apps
        if (!prefs.favoritesInitialized) prefs.favorites = AppRepository.defaultFavorites(apps)
        applyApps(apps)
        if (!loadedOnce) {
            loadedOnce = true
            focusFirst()
        }
    }

    /** Reparte las apps entre favoritas y cuadrícula; solo refresca si algo cambió. */
    private fun applyApps(apps: List<AppEntry>) {
        val hidden = prefs.hidden
        val byKey = apps.associateBy { it.key }
        val favs = prefs.favorites.mapNotNull { byKey[it] }.filter { it.key !in hidden }
        val favKeys = favs.mapTo(HashSet()) { it.key }
        val rest = apps.filter { it.key !in hidden && it.key !in favKeys }

        if (!sameList(favAdapter.items, favs)) favAdapter.submit(favs)
        if (!sameList(gridAdapter.items, rest)) gridAdapter.submit(rest)
        favEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE

        // Si la app enfocada desapareció (desinstalada), recolocar el foco
        if (loadedOnce) main.post {
            val f = currentFocus
            if ((f == null || !f.isShown) && menuOverlay.visibility != View.VISIBLE) focusFirst()
        }
    }

    private fun sameList(a: List<AppEntry>, b: List<AppEntry>) =
        a.size == b.size && a.indices.all { a[it].sameAs(b[it]) }

    // ---------------------------------------------------------------- foco

    private fun focusFirst() {
        scroll.scrollTo(0, 0)
        when {
            favAdapter.itemCount > 0 -> focusAt(favList, 0)
            gridAdapter.itemCount > 0 -> focusAt(gridList, 0)
            else -> btnTvSettings.requestFocus()
        }
    }

    private fun focusKey(key: String?) {
        if (key == null) { focusFirst(); return }
        val f = favAdapter.indexOf(key)
        if (f >= 0) { focusAt(favList, f); return }
        val g = gridAdapter.indexOf(key)
        if (g >= 0) { focusAt(gridList, g); return }
        focusFirst()
    }

    private fun focusAt(list: RecyclerView, position: Int) {
        if (list === favList) list.scrollToPosition(position)
        list.afterNextLayout {
            val v = list.findViewHolderForAdapterPosition(position)?.itemView
            if (v != null) v.requestFocus() else list.getChildAt(0)?.requestFocus()
        }
    }

    // ---------------------------------------------------------------- teclas

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handleBack()
            return true
        }

        if (movingKey != null) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        moveBy(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) endMove()
                    return true
                }
            }
        }

        // Al mantener pulsada una flecha, limitar la velocidad para que la animación siga el ritmo
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && isDpad(code)) {
            val now = SystemClock.uptimeMillis()
            if (now - lastRepeatAt < 75) return true
            lastRepeatAt = now
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isDpad(code: Int) = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
        code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT

    private fun handleBack() {
        when {
            menuOverlay.visibility == View.VISIBLE -> closeMenu(restoreFocus = true)
            movingKey != null -> endMove()
            else -> focusFirst()
        }
    }

    // ---------------------------------------------------------------- abrir apps

    override fun onAppClick(app: AppEntry) {
        if (movingKey != null || menuOverlay.visibility == View.VISIBLE) return
        try {
            startActivity(app.launchIntent())
        } catch (_: Exception) {
            val fallback = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: packageManager.getLaunchIntentForPackage(app.packageName)
            try {
                if (fallback != null) startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else toast(getString(R.string.cannot_open))
            } catch (_: Exception) {
                toast(getString(R.string.cannot_open))
            }
        }
    }

    private fun openTvSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast(getString(R.string.cannot_open))
        }
    }

    private fun openAppInfo(app: AppEntry) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            toast(getString(R.string.cannot_open))
        }
    }

    // ---------------------------------------------------------------- menú contextual

    override fun onAppMenu(app: AppEntry) {
        if (movingKey != null || menuOverlay.visibility == View.VISIBLE) return
        menuApp = app
        menuTitle.text = app.label
        menuItems.removeAllViews()
        val isFav = app.key in prefs.favorites
        addMenuItem(getString(if (isFav) R.string.remove_favorite else R.string.add_favorite)) { toggleFavorite(app) }
        if (isFav) addMenuItem(getString(R.string.move)) { startMove(app) }
        addMenuItem(getString(R.string.app_info)) { openAppInfo(app) }
        addMenuItem(getString(R.string.hide)) { hideApp(app) }

        // El foco no puede salir del menú
        val count = menuItems.childCount
        for (i in 0 until count) {
            val item = menuItems.getChildAt(i)
            item.nextFocusLeftId = item.id
            item.nextFocusRightId = item.id
            if (i == 0) item.nextFocusUpId = item.id
            if (i == count - 1) item.nextFocusDownId = item.id
        }

        menuOverlay.alpha = 0f
        menuOverlay.visibility = View.VISIBLE
        menuPanel.scaleX = 0.94f; menuPanel.scaleY = 0.94f
        menuOverlay.animate().alpha(1f).setDuration(160).start()
        menuPanel.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        menuItems.getChildAt(0)?.requestFocus()
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun addMenuItem(text: String, action: () -> Unit) {
        val tv = TextView(this).apply {
            id = View.generateViewId()
            this.text = text
            textSize = 19f
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(56)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setTextColor(colorStateList(R.color.on_light_text))
            setBackgroundResource(R.drawable.menu_item_bg)
            isFocusable = true
            isClickable = true
            noDefaultHighlight()
            setOnClickListener {
                closeMenu(restoreFocus = false)
                action()
            }
        }
        menuItems.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4) })
    }

    private fun closeMenu(restoreFocus: Boolean) {
        val key = menuApp?.key
        menuApp = null
        menuOverlay.animate().cancel()
        menuOverlay.visibility = View.GONE
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (restoreFocus) focusKey(key) else if (currentFocus == null || currentFocus?.isShown != true) focusKey(key)
    }

    private fun toggleFavorite(app: AppEntry) {
        val favs = prefs.favorites.toMutableList()
        if (app.key in favs) favs.remove(app.key) else favs.add(app.key)
        prefs.favorites = favs
        applyApps(allApps)
        focusKey(app.key)
    }

    private fun hideApp(app: AppEntry) {
        val neighbor = neighborOf(app.key)
        prefs.hidden = prefs.hidden + app.key
        applyApps(allApps)
        focusKey(neighbor)
        toast(getString(R.string.app_hidden))
    }

    private fun neighborOf(key: String): String? {
        for (list in listOf(favAdapter.items, gridAdapter.items)) {
            val i = list.indexOfFirst { it.key == key }
            if (i >= 0) return list.getOrNull(i + 1)?.key ?: list.getOrNull(i - 1)?.key
        }
        return null
    }

    // ---------------------------------------------------------------- mover favoritas

    private fun startMove(app: AppEntry) {
        val index = favAdapter.indexOf(app.key)
        if (index < 0) return
        movingKey = app.key
        favAdapter.movingKey = app.key
        favAdapter.notifyItemChanged(index)
        moveHint.alpha = 0f
        moveHint.visibility = View.VISIBLE
        moveHint.animate().alpha(1f).setDuration(200).start()
        focusAt(favList, index)
    }

    private fun moveBy(delta: Int) {
        val key = movingKey ?: return
        val list = favAdapter.items.toMutableList()
        val from = list.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to < 0 || to >= list.size) return
        Collections.swap(list, from, to)
        favAdapter.replaceSilently(list)
        favAdapter.notifyItemMoved(from, to)
        favList.scrollToPosition(to)
    }

    private fun endMove() {
        val key = movingKey ?: return
        movingKey = null
        favAdapter.movingKey = null
        // Guardar el nuevo orden, conservando al final las favoritas ocultas o desinstaladas
        val visible = favAdapter.items.map { it.key }
        prefs.favorites = visible + prefs.favorites.filter { it !in visible }
        val index = favAdapter.indexOf(key)
        if (index >= 0) favAdapter.notifyItemChanged(index)
        moveHint.visibility = View.GONE
        focusAt(favList, index.coerceAtLeast(0))
    }

    // ---------------------------------------------------------------- cabecera y reloj

    private fun setupHeaderButton(button: View, label: String, action: () -> Unit) {
        button.noDefaultHighlight()
        button.setOnClickListener { action() }
        button.setOnFocusChangeListener { v, hasFocus ->
            FocusAnim.scale(v, hasFocus, 1.15f)
            if (hasFocus) {
                headerHint.text = label
                headerHint.animate().alpha(1f).setDuration(180).start()
            } else {
                headerHint.animate().alpha(0f).setDuration(120).start()
            }
        }
    }

    private fun updateClock() {
        val now = Date()
        clockTime.text = SimpleDateFormat("h:mm", spanish).format(now)
        clockAmPm.text = SimpleDateFormat("a", spanish).format(now)
        clockDate.text = SimpleDateFormat("EEEE, d 'de' MMMM", spanish).format(now)
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    private fun registerTimeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerSystemReceiver(timeReceiver, filter)
    }

    // ---------------------------------------------------------------- instalar / desinstalar

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { IconLoader.invalidate(it) }
            // Varias señales seguidas (p. ej. al actualizar) se agrupan en una sola recarga
            main.removeCallbacks(reloadRunnable)
            main.postDelayed(reloadRunnable, 400)
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerSystemReceiver(packageReceiver, filter)
    }

    private fun registerSystemReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    // ---------------------------------------------------------------- utilidades

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
