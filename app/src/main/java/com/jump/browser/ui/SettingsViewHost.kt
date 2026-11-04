package com.jump.browser.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.jump.browser.browser.UserAgents
import com.jump.browser.data.AppSettings
import com.jump.browser.data.DarkMode
import com.jump.browser.ui.theme.JumpPressed
import kotlin.math.roundToInt

/**
 * Settings on platform views: the category list, per-category pages with
 * the slide and drag navigation of the composable original, the row
 * widgets, and all dialogs. Row-for-row behavior of the composable
 * version.
 */
open class SettingsViewHost(
    context: Context,
    internal val palette: ViewPalette,
    initial: AppSettings,
    private val onBack: () -> Unit,
    internal val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    private val onClearData: () -> Unit,
    private val onAbout: () -> Unit,
    internal val onImportBookmarks: (onDone: (Int) -> Unit) -> Unit = { it(0) },
    internal val onExportData: () -> Unit = {},
    internal val onImportData: () -> Unit = {},
    internal var scripts: List<com.jump.browser.data.UserScript> = emptyList(),
    internal val onAddScript: (String) -> Unit = {},
    internal val onRemoveScript: (Long) -> Unit = {},
    internal val onToggleScript: (Long, Boolean) -> Unit = { _, _ -> },
    internal var logins: List<com.jump.browser.data.SavedLogin> = emptyList(),
    internal val onRemoveLogin: (Long) -> Unit = {},
    internal val onClearLogins: () -> Unit = {},
    internal val controllerSetIncognito: ((Boolean) -> Unit)? = null,
    internal val onCategoryChanged: (Boolean) -> Unit = {},
) : LinearLayout(context) {

    internal var settings: AppSettings = initial
    internal var category: String? = null
    internal var cardDialog: Dialog? = null
    internal var clearDraft = initial.clearDataMask
    internal var exitMaskDraft = initial.clearDataOnExitMask
    internal val navFrame = FrameLayout(context)
    internal val titleView = TextView(context)
    internal val mainPage = ScrollView(context)
    private var currentPage: View? = null
    private var dragStartX = 0f

    internal fun Float.dp(): Int = (this * resources.displayMetrics.density + 0.5f).roundToInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        addView(buildTitleRow())
        addView(context.dividerLine(palette))
        navFrame.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        )
        addView(navFrame)
        mainPage.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        )
        mainPage.isVerticalScrollBarEnabled = false
        navFrame.addView(mainPage)
        showMain()
    }

    internal fun buildTitleRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 54f.dp(),
            )
            setPadding(4f.dp(), 0, 0, 0)
        }
        row.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(56f.dp(), 56f.dp())
            isClickable = true
            isFocusable = true
            pressFeedback(oval = true)
            setOnClickListener { onNavigateBack() }
            addView(GlyphView(context, ReferenceIcons.paths.getValue("back"), palette.onSurface).apply {
                layoutParams = FrameLayout.LayoutParams(22f.dp(), 22f.dp(), Gravity.CENTER)
            })
        })
        titleView.text = "Settings"
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        titleView.setTextColor(palette.onBackground)
        titleView.includeFontPadding = false
        row.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    internal fun onNavigateBack() {
        if (category != null) closeCategory() else onBack()
    }

    internal fun showMain() {
        currentPage?.let { navFrame.removeView(it) }
        currentPage = null
        category = null
        onCategoryChanged(false)
        titleView.text = "Settings"
        mainPage.removeAllViews()
        val (columnScroll, column) = pageColumn(5.75f.dp())
        listOf("General", "Customization", "Privacy", "Advanced", "Scripts", "About").forEach { title ->
            column.addView(labelRow(title, vpad = 17.5f) { showCategory(title) })
        }
        column.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 24f.dp())
        })
        mainPage.addView(columnScroll)
        mainPage.translationX = 0f
    }

    internal fun showCategory(name: String) {
        category = name
        onCategoryChanged(true)
        currentPage?.let { navFrame.removeView(it) }
        val page = buildCategoryPage(name)
        currentPage = page
        navFrame.addView(page)
        val w = navFrame.width.toFloat().coerceAtLeast(1f)
        page.translationX = w
        mainPage.translationX = -w * 0.3f
        page.animate().translationX(0f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        mainPage.animate().translationX(-w * 0.3f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    internal fun closeCategory(byDrag: Boolean = false) {
        val page = currentPage ?: return
        category = null
        onCategoryChanged(false)
        val w = navFrame.width.toFloat().coerceAtLeast(1f)
        if (byDrag) {
            navFrame.removeView(page)
            currentPage = null
            mainPage.translationX = 0f
        } else {
            page.animate().translationX(w).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    navFrame.removeView(page)
                    if (currentPage === page) currentPage = null
                }
                .start()
            mainPage.animate().translationX(0f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    /** Settings push from the composing host. */
    fun submit(
        updated: AppSettings,
        newScripts: List<com.jump.browser.data.UserScript> = scripts,
        newLogins: List<com.jump.browser.data.SavedLogin> = logins,
    ) {
        scripts = newScripts
        logins = newLogins
        settings = updated
        if (cardDialog == null) {
            if (category != null) rebuildCategory() else showMain()
        }
    }

    private fun rebuildCategory() {
        val cat = category ?: return
        val replaced = buildCategoryPage(cat)
        (currentPage?.parent as? ViewGroup)?.let { parent ->
            parent.removeView(currentPage)
            currentPage = replaced
            parent.addView(replaced)
            replaced.translationX = 0f
        }
    }

    // ------------------------------------------------------- widgets

    internal fun labelRow(
        title: String,
        subtitle: String? = null,
        vpad: Float = 18f,
        action: (() -> Unit)? = null,
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            val hpad = 16f.dp(); val v = vpad.dp()
            setPadding(hpad, v, hpad, v)
        }
        column.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(palette.onBackground)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        subtitle?.takeIf { it.isNotBlank() }?.let {
            column.addView(TextView(context).apply {
                text = it
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(palette.onSurfaceVariant)
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 2f.dp() }
            })
        }
        if (action != null) {
            column.isClickable = true
            column.isFocusable = true
            pressFeedback()
            column.setOnClickListener { action() }
        }
        return column
    }

    internal fun sectionHeader(text: String): View = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(palette.primary)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lp.leftMargin = 16f.dp(); lp.rightMargin = 16f.dp()
        lp.topMargin = 22f.dp(); lp.bottomMargin = 6f.dp()
        layoutParams = lp
    }

    internal fun optionRow(label: String, selected: Boolean, action: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 43f.dp(),
            )
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { action() }
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(20f.dp(), 20f.dp())
                background = if (selected) filledOval(palette.primary)
                else strokedRound(10f.dp(), 1.5f.dp(), 0xFFC2C2C2.toInt())
            })
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(palette.onBackground)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.leftMargin = 20f.dp()
                layoutParams = lp
            })
        }

    internal fun toolbarChoiceRow(label: String, value: String, action: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            val vpad = 12f.dp()
            setPadding(0, vpad, 0, vpad)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { action() }
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(palette.onBackground)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(context.text(value, 13f, palette.onSurfaceVariant))
        }

    internal fun tickRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit): View {
        var ticked = checked
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f.dp().toFloat()
        }
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
        }
        val square = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                val r = 3f.dp().toFloat()
                val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                boxPaint.color = if (ticked) palette.primary else Color.TRANSPARENT
                canvas.drawRoundRect(rect, r, r, boxPaint)
                border.color = if (ticked) palette.primary else 0xFFC2C2C2.toInt()
                canvas.drawRoundRect(rect, r, r, border)
                if (ticked) {
                    tick.strokeWidth = width * 0.16f
                    canvas.drawLines(
                        floatArrayOf(
                            width * 0.12f, height * 0.52f, width * 0.4f, height * 0.8f,
                            width * 0.4f, height * 0.8f, width * 0.9f, height * 0.2f,
                        ), tick,
                    )
                }
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            val vpad = 9f.dp()
            setPadding(0, vpad, 0, vpad)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener {
                ticked = !ticked
                square.invalidate()
                onToggle(ticked)
            }
            addView(square, LinearLayout.LayoutParams(18f.dp(), 18f.dp()))
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(palette.onBackground)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 14f.dp()
                }
            })
        }
    }

    internal fun switchView(checked: Boolean, onChange: (Boolean) -> Unit): View {
        var on = checked
        val track = Paint(Paint.ANTI_ALIAS_FLAG)
        val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        return object : View(context) {
            init {
                layoutParams = LinearLayout.LayoutParams(42f.dp(), 26f.dp())
                isClickable = true
                isFocusable = true
                pressFeedback(oval = true)
                setOnClickListener { on = !on; invalidate(); onChange(on) }
            }

            override fun onDraw(canvas: Canvas) {
                track.color = if (on) palette.primary else palette.outlineVariant
                canvas.drawRoundRect(
                    RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    13f.dp().toFloat(), 13f.dp().toFloat(), track,
                )
                val x = (if (on) 18f.dp() else 2f.dp()).toFloat()
                canvas.drawCircle(x + 11f.dp(), height / 2f, 11f.dp().toFloat(), thumb)
            }
        }
    }

    internal inner class DragPage : FrameLayout(context) {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx > 8f.dp() && dx > dy * 1.4f) dragging = true
                    }
                }
            }
            return dragging
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - downX).coerceAtLeast(0f)
                    translationX = dx
                    val w = navFrame.width.toFloat().coerceAtLeast(1f)
                    mainPage.translationX = -w * 0.3f * (1f - (dx / w).coerceIn(0f, 1f))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val w = navFrame.width.toFloat().coerceAtLeast(1f)
                    if (event.actionMasked == MotionEvent.ACTION_UP && translationX > w * 0.46f) {
                        closeCategory(byDrag = true)
                    } else {
                        animate().translationX(0f).setDuration(300).start()
                        mainPage.animate().translationX(-w * 0.3f).setDuration(300).start()
                    }
                    dragging = false
                }
            }
            return true
        }
    }

    private fun pageColumn(top: Int): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(palette.background)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            (layoutParams as LinearLayout.LayoutParams).topMargin = top
        }
        scroll.addView(column)
        return scroll to column
    }

    private fun buildCategoryPage(cat: String): View {
        val (scroll, column) = when (cat) {
            "General" -> pageColumn(13f.dp())
            else -> pageColumn(5.75f.dp())
        }
        when (cat) {
            "General" -> generalRows(column)
            "Customization" -> customizationRows(column)
            "Privacy" -> privacyRows(column)
            "Advanced" -> advancedRows(column)
            "Scripts" -> scriptsRows(column)
            "About" -> aboutRows(column)
        }
        column.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 24f.dp())
        })
        val dragPage = DragPage()
        dragPage.addView(scroll)
        return dragPage
    }

    internal fun toast(text: String) = JumpToast.show(context, text)

    internal fun generalRows(column: LinearLayout) {
        column.addView(labelRow(
            "Incognito mode",
            if (settings.incognitoDefault) "On" else "Off",
        ) { controllerSetIncognito?.invoke(!settings.incognitoDefault) })
        column.addView(labelRow("User-agent", UserAgents.label(settings)) { dialogUserAgent() })
        column.addView(labelRow("Clear data") {
            clearDraft = settings.clearDataMask; dialogClearData(maskForExit = false)
        })
        column.addView(labelRow("Ad blocking", if (settings.adBlock) null else "Off") {
            onSettingsChange { it.copy(adBlock = !it.adBlock) }; rebuildCategory()
        })
        column.addView(labelRow("Site configuration") { toast("No per-site exceptions yet") })
        column.addView(labelRow(
            "Password manager",
            if (logins.isEmpty()) null else "${logins.size} saved",
        ) { dialogPasswords() })
        column.addView(labelRow("Night mode", if (settings.nightMode) "On" else null) {
            onSettingsChange { it.copy(nightMode = !it.nightMode) }; rebuildCategory()
        })
        column.addView(labelRow("Reader mode", if (settings.readerMode) "On" else null) {
            onSettingsChange { it.copy(readerMode = !it.readerMode) }; rebuildCategory()
        })
        column.addView(labelRow("Toolbars settings") { dialogToolbars() })
        column.addView(labelRow("Customize menu") { dialogCustomizeMenu() })
        column.addView(labelRow("Customize context menu") {
            toast("Context menu customization is not available yet")
        })
        column.addView(labelRow("Language", "System") { dialogLanguage() })
        column.addView(labelRow("Homepage", settings.homepageUrl.ifBlank { null }) { dialogHomepage() })
        column.addView(labelRow(
            "Search settings",
            if (settings.searchEngineId == "google") null else settings.searchEngine.name,
        ) { dialogEngine() })
        column.addView(labelRow(
            "AI settings",
            if (settings.ai.baseUrl.isBlank()) "Not configured" else settings.ai.model.ifBlank { "Endpoint set" },
        ) { dialogAi() })
        column.addView(labelRow("Orientation", when (settings.orientation) {
            "portrait" -> "Portrait"
            "landscape" -> "Landscape"
            "sensor" -> "Auto-rotate"
            else -> null
        }) { dialogOrientation() })
        column.addView(labelRow("Download location", "Public Downloads folder") {
            toast("Downloads are saved to the public Downloads folder")
        })
        column.addView(labelRow("Font", if (settings.textZoom == 100) null else "${settings.textZoom}%") {
            dialogZoom()
        })
        column.addView(labelRow("Gestures") { toast("Gestures use the platform defaults") })
        column.addView(sectionHeader("Data"))
        column.addView(labelRow(
            "Clear data on exit",
            clearMaskSummary(settings.clearDataOnExitMask),
        ) {
            exitMaskDraft = settings.clearDataOnExitMask
            dialogClearData(maskForExit = true)
        })
        column.addView(labelRow(
            "Restore tabs on startup",
            if (settings.restoreTabs) "Always restore" else "Don't restore",
        ) { dialogRestoreTabs() })
        column.addView(labelRow(
            title = "Show toast to undo closing tab",
            subtitle = if (settings.incognitoDefault) {
                "The toast will not be shown in incognito mode"
            } else null,
        ) { onSettingsChange { it.copy(showUndoToast = !it.showUndoToast) }; rebuildCategory() })
        column.addView(labelRow("Import bookmarks") { dialogImportBookmarks() })
        column.addView(labelRow("Export bookmarks") { launchExportBookmarks() })
        column.addView(labelRow("Import data") { dialogImportData() })
        column.addView(labelRow("Export data") { dialogExportData() })
        column.addView(labelRow("Set as default browser") { openDefaultBrowserSettings(context) })
    }

    internal fun customizationRows(column: LinearLayout) {
        column.addView(labelRow("Homepage", settings.homepageUrl.ifBlank { null }) { dialogHomepage() })
        column.addView(labelRow("Logo", if (settings.homepageLogoPath.isBlank()) null else "Custom") {
            pickHomepageImage(context) { path ->
                onSettingsChange { it.copy(homepageLogoPath = path) }
                rebuildCategory()
            }
        })
        column.addView(labelRow("Background", if (settings.homepageBackgroundPath.isBlank()) null else "Custom") {
            pickHomepageImage(context) { path ->
                onSettingsChange { it.copy(homepageBackgroundPath = path) }
                rebuildCategory()
            }
        })
        column.addView(labelRow("Background opacity", "${settings.homepageBackgroundOpacity}%") {
            dialogOpacity()
        })
        column.addView(labelRow("Search bar", if (settings.homepageSearchBar) null else "Off") {
            onSettingsChange { it.copy(homepageSearchBar = !it.homepageSearchBar) }; rebuildCategory()
        })
        column.addView(labelRow("Colored bookmarks", if (settings.homepageFavoriteColor) null else "Off") {
            onSettingsChange { it.copy(homepageFavoriteColor = !it.homepageFavoriteColor) }; rebuildCategory()
        })
        column.addView(labelRow("Appearance", when (settings.darkMode) {
            DarkMode.SYSTEM -> "Follow system"
            DarkMode.LIGHT -> "Light"
            DarkMode.DARK -> "Dark"
        }) { dialogTheme() })
        column.addView(labelRow("Font", if (settings.textZoom == 100) null else "${settings.textZoom}%") {
            dialogZoom()
        })
        column.addView(labelRow("Customize menu") { dialogCustomizeMenu() })
    }

    internal fun privacyRows(column: LinearLayout) {
        column.addView(labelRow("Do not track", if (settings.doNotTrack) "On" else null) {
            onSettingsChange { it.copy(doNotTrack = !it.doNotTrack) }; rebuildCategory()
        })
        column.addView(labelRow("Disable WebRTC") { toast("WebRTC uses Android defaults") })
        column.addView(labelRow(
            "Do not sell or share data",
            "Request the website not to sell or share my data to protect privacy",
        ) { toast("Do not sell requests are sent with page loads") })
        column.addView(labelRow("Block third-party cookies", if (settings.blockThirdPartyCookies) "On" else null) {
            onSettingsChange { it.copy(blockThirdPartyCookies = !it.blockThirdPartyCookies) }; rebuildCategory()
        })
    }

    internal fun advancedRows(column: LinearLayout) {
        column.addView(labelRow("Save data", "Enable data-savings mode on compatible websites") {
            toast("Data saver is controlled by the website")
        })
        column.addView(labelRow("Show sniffer button automatically") { toast("Resource sniffer is not available") })
        column.addView(labelRow("Enable webpage debugging") { toast("Debugging is available in debug builds") })
        column.addView(labelRow("Disable predictive back gesture") { toast("Predictive back uses Android defaults") })
        column.addView(labelRow("Disable Custom Tabs") { toast("Custom Tabs are not supported") })
        column.addView(labelRow("Disable safe browsing", "Safe browsing protects against malware and phishing") {
            toast("Safe browsing uses Android defaults")
        })
        column.addView(labelRow("Ignore SSL certificate warnings", "Ignoring warnings may make connections insecure") {
            toast("SSL warnings are always enforced")
        })
        column.addView(labelRow("JavaScript", if (settings.javaScript) null else "Off") {
            onSettingsChange { it.copy(javaScript = !it.javaScript) }; rebuildCategory()
        })
        column.addView(labelRow("Load images", if (settings.loadImages) null else "Off") {
            onSettingsChange { it.copy(loadImages = !it.loadImages) }; rebuildCategory()
        })
        column.addView(labelRow("Search suggestions", if (settings.searchSuggestions) null else "Off") {
            onSettingsChange { it.copy(searchSuggestions = !it.searchSuggestions) }; rebuildCategory()
        })
        column.addView(labelRow("Desktop site by default", if (settings.desktopMode) "On" else null) {
            onSettingsChange { it.copy(desktopMode = !it.desktopMode) }; rebuildCategory()
        })
        column.addView(labelRow("Text scaling", "${settings.textZoom}%") { dialogZoom() })
    }

    internal fun scriptsRows(column: LinearLayout) {
        column.addView(labelRow(
            "Add userscript",
            if (scripts.isEmpty()) "No scripts installed" else null,
        ) { dialogAddScript() })
        scripts.forEach { script ->
            column.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                val hpad = 16f.dp(); val vpad = 12f.dp()
                setPadding(hpad, vpad, hpad, vpad)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = script.name
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(palette.onBackground)
                    })
                    addView(TextView(context).apply {
                        text = if (script.matches.isEmpty()) "All sites" else script.matches.joinToString(", ")
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(palette.onSurfaceVariant)
                        maxLines = 2
                    })
                })
                addView(switchView(script.enabled) { onToggleScript(script.id, it) })
                addView(TextView(context).apply {
                    text = "Delete"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(palette.error)
                    isClickable = true
                    isFocusable = true
                    pressFeedback()
                    setOnClickListener { onRemoveScript(script.id) }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = 10f.dp() }
                })
            })
        }
    }

    internal fun aboutRows(column: LinearLayout) {
        column.addView(labelRow("Jump", appVersion()) { onAbout() })
        column.addView(labelRow("Open source licenses") { toast("MIT License") })
        column.addView(labelRow("Terms of use") { toast("Personal and educational use") })
        column.addView(labelRow("Privacy policy") { toast("Jump keeps browsing data on this device") })
    }

    internal fun appVersion(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Version ${info.versionName}"
    } catch (_: Exception) { "Version 1.0.0" }

    internal fun clearMaskSummary(mask: Int): String {
        if (mask == 0) return "Off"
        val options = com.jump.browser.browser.CLEAR_DATA_OPTIONS
        val chosen = options.count { mask and it.second != 0 }
        return when {
            chosen == options.size -> "All"
            chosen == 1 -> options.first { mask and it.second != 0 }.first
            else -> "$chosen items"
        }
    }

    // -------------------------------------------------------- dialogs

    private fun closeDialog() { cardDialog?.dismiss(); cardDialog = null }

    private fun dialogCard(
        title: String,
        body: (LinearLayout) -> Unit,
        buttons: List<Pair<String, () -> Unit>>,
    ): Dialog {
        val dialog = Dialog(context)
        val card = FrameLayout(context).apply { background = filledRound(16f.dp(), palette.surface) }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20f.dp(), 18f.dp(), 20f.dp(), 8f.dp())
        }
        column.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(palette.onBackground)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 10f.dp() }
        })
        val bodyColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            )
        }
        body(bodyColumn)
        column.addView(bodyColumn)
        buttons.forEach { (label, action) ->
            column.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(palette.primary)
                gravity = Gravity.CENTER
                minWidth = 56f.dp()
                minHeight = 48f.dp()
                val hpad = 8f.dp()
                setPadding(hpad, 0, hpad, 0)
                isClickable = true
                pressFeedback(18f.dp())
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.END })
        }
        card.addView(column)
        dialog.setContentView(card)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), LayoutParams.WRAP_CONTENT)
        }
        dialog.setOnCancelListener { cardDialog = null }
        dialog.show()
        card.scaleX = 0.90f; card.alpha = 0f
        card.animate().scaleX(1f).alpha(1f).setDuration(140).start()
        return dialog
    }

    private fun underlinedField(
        placeholder: String,
        initial: String,
        uri: Boolean = false,
        multiLine: Boolean = false,
        minLines: Int = 1,
        onText: (String) -> Unit,
    ): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            )
        }
        val edit = EditText(context).apply {
            background = null
            hint = placeholder
            setHintTextColor(palette.onSurfaceVariant)
            setTextColor(palette.onBackground)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setSingleLine(!multiLine)
            if (multiLine) {
                setMinLines(minLines)
                gravity = Gravity.TOP or Gravity.START
                inputType = inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            if (uri) inputType = inputType or InputType.TYPE_TEXT_VARIATION_URI
            val vpad = 8f.dp()
            setPadding(0, vpad, 0, vpad)
            setText(initial)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
            })
        }
        column.addView(edit)
        column.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 1f.dp(),
            )
            setBackgroundColor(0x30808080.toInt())
        })
        return column
    }

    private fun optionsCard(
        title: String,
        options: List<Pair<String, String>>,
        selected: String,
        onSelect: (String) -> Unit,
    ) {
        cardDialog = dialogCard(
            title = title,
            body = { column ->
                options.forEach { (label, value) ->
                    column.addView(optionRow(label, value == selected) {
                        closeDialog()
                        onSelect(value)
                    })
                }
            },
            buttons = listOf("Cancel" to { closeDialog() }),
        ).also { it.show() }
    }

    private fun textDialog(
        title: String,
        message: String? = null,
        placeholder: String,
        initial: String = "",
        uri: Boolean = false,
        onConfirm: (String) -> Unit,
    ) {
        var draft = initial
        cardDialog = dialogCard(
            title = title,
            body = { column ->
                message?.let {
                    column.addView(TextView(context).apply {
                        text = it
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(palette.onSurfaceVariant)
                        layoutParams = LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                        ).apply { bottomMargin = 8f.dp() }
                    })
                }
                column.addView(underlinedField(placeholder, draft, uri) { draft = it })
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Save" to {
                    onConfirm(draft)
                    closeDialog()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogEngine() {
        optionsCard(
            title = "Search engine",
            options = listOf(
                "Google" to "google", "Baidu" to "baidu", "Bing" to "bing",
                "Yahoo" to "yahoo", "StartPage" to "startpage", "DuckDuckGo" to "duckduckgo",
            ),
            selected = settings.searchEngineId,
        ) { id ->
            onSettingsChange { it.copy(searchEngineId = id) }
            rebuildCategory()
        }
    }

    private fun dialogHomepage() {
        textDialog(
            title = "Homepage",
            placeholder = "Leave empty for Jump Homepage",
            initial = settings.homepageUrl,
            uri = true,
        ) { draft ->
            onSettingsChange { it.copy(homepageUrl = draft.trim()) }
            rebuildCategory()
        }
    }

    private fun dialogTheme() {
        optionsCard(
            title = "Appearance",
            options = listOf("Follow system" to "SYSTEM", "Light" to "LIGHT", "Dark" to "DARK"),
            selected = settings.darkMode.name,
        ) { id ->
            onSettingsChange { it.copy(darkMode = DarkMode.valueOf(id)) }
            rebuildCategory()
        }
    }

    private fun dialogZoom() {
        optionsCard(
            title = "Text scaling",
            options = listOf(
                "80%" to "80", "90%" to "90", "100%" to "100",
                "115%" to "115", "130%" to "130", "150%" to "150",
            ),
            selected = settings.textZoom.toString(),
        ) { id ->
            onSettingsChange { it.copy(textZoom = id.toInt()) }
            rebuildCategory()
        }
    }

    private fun dialogRestoreTabs() {
        optionsCard(
            title = "Restore tabs on startup",
            options = listOf("Don't restore" to "NEVER", "Always restore" to "ALWAYS"),
            selected = if (settings.restoreTabs) "ALWAYS" else "NEVER",
        ) { value ->
            onSettingsChange { it.copy(restoreTabs = value == "ALWAYS") }
            rebuildCategory()
        }
    }

    private fun dialogOrientation() {
        val options = listOf(
            "system" to "Follow system", "portrait" to "Portrait",
            "landscape" to "Landscape", "sensor" to "Auto-rotate",
        )
        cardDialog = dialogCard(
            title = "Orientation",
            body = { column ->
                options.forEach { (value, label) ->
                    column.addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                        )
                        val vpad = 12f.dp()
                        setPadding(0, vpad, 0, vpad)
                        isClickable = true
                        isFocusable = true
                        pressFeedback()
                        setOnClickListener {
                            onSettingsChange { it.copy(orientation = value) }
                            closeDialog()
                            rebuildCategory()
                        }
                        addView(TextView(context).apply {
                            text = label
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                            setTextColor(if (settings.orientation == value) palette.primary else palette.onBackground)
                            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                        })
                        if (settings.orientation == value) {
                            addView(TextView(context).apply {
                                text = "✓"
                                setTextColor(palette.primary)
                            })
                        }
                    })
                }
            },
            buttons = emptyList(),
        ).also { it.show() }
    }

    private fun dialogLanguage() {
        cardDialog = dialogCard(
            title = "Language",
            body = { column ->
                column.addView(TextView(context).apply {
                    text = "Jump follows the system language. Open Android Settings to change it for the whole device."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(palette.onBackground)
                })
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Open settings" to {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    closeDialog()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogImportBookmarks() {
        cardDialog = dialogCard(
            title = "Import bookmarks",
            body = { column ->
                column.addView(TextView(context).apply {
                    text = "Choose a bookmarks.html file exported from another browser. " +
                        "Folders are kept and bookmarks you already have are skipped."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(palette.onSurfaceVariant)
                })
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Choose file" to {
                    closeDialog()
                    onImportBookmarks { count ->
                        toast(
                            if (count > 0) "Imported $count bookmark${if (count == 1) "" else "s"}"
                            else "No new bookmarks found",
                        )
                    }
                },
            ),
        ).also { it.show() }
    }

    private fun launchExportBookmarks() {
        val chooser = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/html"
            putExtra(android.content.Intent.EXTRA_TITLE, "bookmarks.html")
        }
        runCatching {
            context.startActivity(android.content.Intent.createChooser(chooser, "Export bookmarks"))
        }
    }

    private fun dialogImportData() {
        cardDialog = dialogCard(
            title = "Import data",
            body = { column ->
                column.addView(TextView(context).apply {
                    text = "Choose a Jump backup file. Bookmarks and history are merged into what " +
                        "you already have, and settings are applied. Saved passwords are never " +
                        "included in a backup."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(palette.onSurfaceVariant)
                })
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Choose file" to {
                    closeDialog()
                    onImportData()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogExportData() {
        cardDialog = dialogCard(
            title = "Export data",
            body = { column ->
                column.addView(TextView(context).apply {
                    text = "Saves a backup file with your settings, bookmarks and history. " +
                        "Passwords are not included."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(palette.onSurfaceVariant)
                })
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Export" to {
                    closeDialog()
                    onExportData()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogPasswords() {
        cardDialog = dialogCard(
            title = "Saved passwords",
            body = { column ->
                if (logins.isEmpty()) {
                    column.addView(TextView(context).apply {
                        text = "No passwords saved. Turn on \"Save passwords\" and Jump will offer " +
                            "to remember logins for the sites you sign in to."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(palette.onSurfaceVariant)
                    })
                } else {
                    column.addView(TextView(context).apply {
                        text = "Stored on this device, encrypted with the system keystore."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(palette.onSurfaceVariant)
                        layoutParams = LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                        ).apply { bottomMargin = 8f.dp() }
                    })
                    logins.forEach { login ->
                        column.addView(LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                            )
                            val vpad = 8f.dp()
                            setPadding(0, vpad, 0, vpad)
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                                addView(TextView(context).apply {
                                    text = login.host
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                                    setTextColor(palette.onBackground)
                                })
                                addView(TextView(context).apply {
                                    text = login.username
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                    setTextColor(palette.onSurfaceVariant)
                                })
                            })
                            addView(TextView(context).apply {
                                text = "Remove"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setTextColor(palette.error)
                                isClickable = true
                                isFocusable = true
                                pressFeedback()
                                setOnClickListener { onRemoveLogin(login.id); rebuildCategory() }
                            })
                        })
                    }
                }
            },
            buttons = if (logins.isNotEmpty()) listOf(
                "Close" to { closeDialog() },
                "Remove all" to {
                    onClearLogins()
                    rebuildCategory()
                    closeDialog()
                },
            ) else listOf("Close" to { closeDialog() }),
        ).also { it.show() }
    }

    private fun dialogOpacity() {
        var draft = settings.homepageBackgroundOpacity
        val percent = TextView(context).apply {
            text = "$draft%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onBackground)
        }
        val bar = SeekBar(context).apply {
            max = 100
            progress = draft
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 6f.dp() }
            progressTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(palette.outlineVariant)
            thumbTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        draft = value
                        percent.text = "$draft%"
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        cardDialog = dialogCard(
            title = "Background opacity",
            body = { column ->
                column.addView(percent)
                column.addView(bar)
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Save" to {
                    onSettingsChange { it.copy(homepageBackgroundOpacity = draft) }
                    closeDialog()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogClearData(maskForExit: Boolean) {
        if (maskForExit) exitMaskDraft = settings.clearDataOnExitMask
        else clearDraft = settings.clearDataMask
        var bodyColumn: LinearLayout? = null
        fun rebuildTicks() {
            val host = bodyColumn ?: return
            host.removeAllViews()
            com.jump.browser.browser.CLEAR_DATA_OPTIONS.forEach { (label, flag) ->
                val current = if (maskForExit) exitMaskDraft else clearDraft
                host.addView(tickRow(label, current and flag != 0) { ticked ->
                    if (maskForExit) {
                        exitMaskDraft = if (ticked) exitMaskDraft or flag else exitMaskDraft and flag.inv()
                    } else {
                        clearDraft = if (ticked) clearDraft or flag else clearDraft and flag.inv()
                    }
                })
            }
        }
        cardDialog = dialogCard(
            title = if (maskForExit) "Clear data on exit" else "Clear data",
            body = { column ->
                bodyColumn = column
                rebuildTicks()
            },
            buttons = if (maskForExit) listOf(
                "Cancel" to { closeDialog() },
                "Save" to {
                    val mask = exitMaskDraft
                    onSettingsChange { it.copy(clearDataOnExitMask = mask) }
                    rebuildCategory()
                },
            ) else listOf(
                "Cancel" to { closeDialog() },
                "Clear" to {
                    val mask = clearDraft
                    onSettingsChange { it.copy(clearDataMask = mask) }
                    onClearData()
                    rebuildCategory()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogAddScript() {
        var source = ""
        cardDialog = dialogCard(
            title = "Add userscript",
            body = { column ->
                column.addView(TextView(context).apply {
                    text = "Paste a userscript. The // ==UserScript== header supplies " +
                        "its name and the sites it applies to."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(palette.onSurfaceVariant)
                    layoutParams = LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = 10f.dp() }
                })
                column.addView(underlinedField(
                    "// ==UserScript==\n// @name My script\n// @match *://*/*\n// ==/UserScript==",
                    "",
                    multiLine = true,
                    minLines = 6,
                ) { source = it })
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Add" to {
                    if (source.isNotBlank()) {
                        onAddScript(source)
                        rebuildCategory()
                        closeDialog()
                    }
                },
            ),
        ).also { it.show() }
    }

    private fun dialogAi() {
        var baseUrl = settings.ai.baseUrl
        var model = settings.ai.model
        var key = settings.ai.apiKey
        var prompt = settings.ai.systemPrompt
        cardDialog = dialogCard(
            title = "AI settings",
            body = { column ->
                column.addView(TextView(context).apply {
                    text = "Jump has no AI backend of its own. Point it at any service that " +
                        "speaks the OpenAI chat API; the key is stored on this device only."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(palette.onSurfaceVariant)
                    layoutParams = LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = 12f.dp() }
                })
                fun labeled(label: String, hint: String, value: String, onText: (String) -> Unit) {
                    column.addView(TextView(context).apply {
                        text = label
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(palette.onBackground)
                        layoutParams = LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                        )
                    })
                    column.addView(underlinedField(hint, value) { onText(it) })
                }
                labeled("Endpoint", "https://api.openai.com/v1", baseUrl) { baseUrl = it }
                labeled("Model", "gpt-4o-mini", model) { model = it }
                labeled("API key", "sk-...", key) { key = it }
                labeled("System prompt (optional)", "Answer briefly.", prompt) { prompt = it }
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Save" to {
                    onSettingsChange {
                        it.copy(
                            ai = com.jump.browser.data.ChatSettings(
                                baseUrl = baseUrl.trim(),
                                model = model.trim(),
                                apiKey = key.trim(),
                                systemPrompt = prompt.trim(),
                            ),
                        )
                    }
                    rebuildCategory()
                    closeDialog()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogToolbars() {
        var bodyColumn: LinearLayout? = null
        fun rebuildRows() {
            val host = bodyColumn ?: return
            host.removeAllViews()
            fun openPicker(title: String, options: List<Pair<String, String>>, apply: (String) -> Unit) {
                host.addView(TextView(context).apply {
                    text = title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(palette.onSurfaceVariant)
                    val hpad = 2f.dp(); val vpad = 6f.dp()
                    setPadding(hpad, vpad, hpad, vpad)
                    layoutParams = LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    )
                })
                options.forEach { (label, value) ->
                    host.addView(optionRow(label, selected = false) {
                        apply(value)
                        rebuildRows()
                    })
                }
            }
            host.addView(toolbarChoiceRow(
                label = "Toolbar UI mode",
                value = when (settings.toolbarPosition) {
                    "BOTTOM" -> "Toolbar at bottom"
                    "TWO_ROW" -> "Two-row toolbar"
                    "SANDWICH" -> "Sandwich"
                    else -> "Toolbar at top"
                },
                action = {
                    openPicker(
                        "Toolbar UI mode",
                        listOf(
                            "Sandwich" to "SANDWICH", "Toolbar at top" to "TOP",
                            "Toolbar at bottom" to "BOTTOM", "Two-row toolbar" to "TWO_ROW",
                        ),
                    ) { value -> onSettingsChange { it.copy(toolbarPosition = value) } }
                },
            ))
            host.addView(toolbarChoiceRow(
                label = "Auto-hide toolbars",
                value = when (settings.autoHideToolbars) {
                    "SCROLL" -> "Scroll to hide/show"
                    "PRESS" -> "Press to hide/show"
                    else -> "Always show"
                },
                action = {
                    openPicker(
                        "Auto-hide toolbars",
                        listOf(
                            "Always show" to "NEVER", "Scroll to hide/show" to "SCROLL",
                            "Press to hide/show" to "PRESS",
                        ),
                    ) { value -> onSettingsChange { it.copy(autoHideToolbars = value) } }
                },
            ))
            host.addView(toolbarChoiceRow("Enable tab bar", if (settings.showTabBar) "On" else "Off") {
                onSettingsChange { it.copy(showTabBar = !it.showTabBar) }
            })
            host.addView(toolbarChoiceRow(
                label = "URL field content",
                value = when (settings.urlFieldContent) {
                    "URL" -> "URL"
                    "DOMAIN" -> "Domain"
                    else -> "Title"
                },
                action = {
                    openPicker(
                        "URL field content",
                        listOf("Title" to "TITLE", "URL" to "URL", "Domain" to "DOMAIN"),
                    ) { value -> onSettingsChange { it.copy(urlFieldContent = value) } }
                },
            ))
            host.addView(toolbarChoiceRow(
                label = "Color mode",
                value = when (settings.toolbarColor) {
                    "DARK" -> "Dark"
                    "LIGHT" -> "Light"
                    else -> "Auto"
                },
                action = {
                    openPicker(
                        "Color mode",
                        listOf("Auto" to "AUTO", "Light" to "LIGHT", "Dark" to "DARK"),
                    ) { value -> onSettingsChange { it.copy(toolbarColor = value) } }
                },
            ))
            host.addView(toolbarChoiceRow(
                label = "Auto-adjust color of toolbars",
                value = if (settings.autoAdjustToolbarColor) "On" else "Off",
                action = { onSettingsChange { it.copy(autoAdjustToolbarColor = !it.autoAdjustToolbarColor) } },
            ))
        }
        rebuildRows()
    }

    private fun dialogCustomizeMenu() {
        var bodyColumn: LinearLayout? = null
        fun rebuildMenu() {
            val host = bodyColumn ?: return
            host.removeAllViews()
            val order = if (settings.customizeMenu.isEmpty()) {
                com.jump.browser.ui.DEFAULT_MENU_ORDER
            } else settings.customizeMenu
            order.forEach { label ->
                host.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    ).apply {
                        val vpad = 4f.dp()
                        setPadding(0, vpad, 0, vpad)
                    }
                    addView(TextView(context).apply {
                        text = label.replace('\n', ' ')
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTextColor(palette.onBackground)
                        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(context).apply {
                        text = "▲"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(palette.onBackground)
                        isClickable = true
                        isFocusable = true
                        pressFeedback(oval = true)
                        setOnClickListener {
                            val list = order.toMutableList()
                            val i = list.indexOf(label)
                            if (i > 0) {
                                list.removeAt(i); list.add(i - 1, label)
                                onSettingsChange { it.copy(customizeMenu = list) }
                                rebuildMenu()
                            }
                        }
                        layoutParams = LinearLayout.LayoutParams(40f.dp(), 40f.dp())
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(context).apply {
                        text = "▼"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(palette.onBackground)
                        isClickable = true
                        isFocusable = true
                        pressFeedback(oval = true)
                        setOnClickListener {
                            val list = order.toMutableList()
                            val i = list.indexOf(label)
                            if (i in 0 until list.lastIndex) {
                                list.removeAt(i); list.add(i + 1, label)
                                onSettingsChange { it.copy(customizeMenu = list) }
                                rebuildMenu()
                            }
                        }
                        layoutParams = LinearLayout.LayoutParams(40f.dp(), 40f.dp())
                        gravity = Gravity.CENTER
                    })
                })
            }
        }
        cardDialog = dialogCard(
            title = "Customize menu",
            body = { column ->
                bodyColumn = column
                rebuildMenu()
            },
            buttons = listOf(
                "Reset" to {
                    onSettingsChange { it.copy(customizeMenu = emptyList()) }
                    rebuildMenu()
                    rebuildCategory()
                },
                "Done" to {
                    rebuildCategory()
                    closeDialog()
                },
            ),
        ).also { it.show() }
    }

    private fun dialogUserAgent() {
        var draft = settings.userAgent
        var customDraft = settings.customUserAgent
        var desktopDraft = settings.desktopUserAgent
        val options = listOf(
            "Default" to "DEFAULT", "Android (Phone)" to "ANDROID",
            "Android (Tablet)" to "ANDROID_TABLET", "Chrome (PC)" to "WINDOWS_CHROME",
            "Windows (IE 11)" to "WINDOWS_IE", "macOS" to "MACOS",
            "iPhone" to "IPHONE", "iPad" to "IPAD", "Symbian" to "SYMBIAN", "Custom" to "CUSTOM",
        )
        cardDialog = dialogCard(
            title = "User-agent",
            body = { column ->
                fun rebuildUaBody() {
                    column.removeAllViews()
                    options.forEach { (label, value) ->
                        column.addView(optionRow(label, draft == value) {
                            draft = value
                            onSettingsChange { it.copy(userAgent = value) }
                            rebuildUaBody()
                        })
                    }
                    if (draft == "CUSTOM") {
                        column.addView(underlinedField("Custom user-agent", customDraft) { customDraft = it },
                            LinearLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = 10f.dp() })
                    }
                    column.addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val vpad = 10f.dp()
                        setPadding(0, vpad, 0, vpad)
                        isClickable = true
                        isFocusable = true
                        pressFeedback()
                        setOnClickListener {
                            desktopDraft = if (desktopDraft.isNotBlank()) "" else UserAgents.DESKTOP
                            onSettingsChange { it.copy(desktopUserAgent = desktopDraft) }
                            rebuildUaBody()
                        }
                        addView(TextView(context).apply {
                            text = "User-agent in desktop mode"
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                            setTextColor(palette.onBackground)
                            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(switchView(settings.desktopUserAgent.isNotBlank()) {
                            desktopDraft = if (it) UserAgents.DESKTOP else ""
                            onSettingsChange { it.copy(desktopUserAgent = desktopDraft) }
                            rebuildUaBody()
                        })
                    }, LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = 10f.dp() })
                    if (settings.desktopUserAgent.isNotBlank()) {
                        column.addView(underlinedField(UserAgents.DESKTOP, desktopDraft) { desktopDraft = it })
                    }
                    column.addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val vpad = 10f.dp()
                        setPadding(0, vpad, 0, vpad)
                        isClickable = true
                        isFocusable = true
                        pressFeedback()
                        setOnClickListener {
                            onSettingsChange { it.copy(userAgentReduction = !it.userAgentReduction) }
                            rebuildUaBody()
                        }
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                            addView(TextView(context).apply {
                                text = "User-agent reduction"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                                setTextColor(palette.onBackground)
                            })
                            addView(TextView(context).apply {
                                text = "Remove device information and minor version information from User-agent"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                setTextColor(palette.onSurfaceVariant)
                            })
                        })
                        addView(switchView(settings.userAgentReduction) { reduced ->
                            onSettingsChange { it.copy(userAgentReduction = reduced) }
                            rebuildUaBody()
                        })
                    }, LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = 10f.dp() })
                }
                rebuildUaBody()
            },
            buttons = listOf(
                "Cancel" to { closeDialog() },
                "Save" to {
                    if (draft == "CUSTOM" && customDraft.isBlank()) {
                        toast("The user-agent can not be empty")
                    } else {
                        onSettingsChange {
                            it.copy(
                                userAgent = draft,
                                customUserAgent = customDraft.trim(),
                                desktopUserAgent = desktopDraft.trim(),
                            )
                        }
                        rebuildCategory()
                        closeDialog()
                    }
                },
            ),
        ).also { it.show() }
}
}
