package com.jump.browser.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jump.browser.browser.TabInfo

/**
 * The chrome on platform views: 48dp title bar, 48dp address bar, and the
 * five-slot bottom bar with drag-to-switch tabs. Same geometry, colors and
 * press feedback as the composable versions they replace.
 */
fun Float.cdp(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

fun Int.cdp(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

/** Recolors an (opaque) tint with a new alpha, like Compose's Color.copy. */
fun tint(color: Int, alphaFraction: Float): Int =
    (color and 0xFFFFFF) or (((alphaFraction * 255).toInt()) shl 24)

fun chromeIconView(context: Context, name: String, tint: Int, sizeDp: Int = 22): GlyphView {
    val view = GlyphView(context, ReferenceIcons.paths.getValue(name), tint)
    val px = sizeDp.cdp(context)
    view.layoutParams = FrameLayout.LayoutParams(px, px)
    return view
}

private val glyphNames = setOf("search", "scanner", "secure", "incognito", "refresh", "close", "back", "forward", "home", "tabs", "menu", "expand")

/** Pointer-square sizing helper for a pressable 56dp chrome slot. */
fun chromeSlot(
    context: Context,
    glyph: String,
    tint: Int,
    enabled: Boolean,
    onClick: () -> Unit,
): FrameLayout {
    val slot = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 56f.cdp(context), 1f,
        )
        isClickable = true
        isFocusable = true
        alpha = if (enabled) 1f else 0.35f
        pressFeedback()
    }
    slot.addView(chromeIconView(context, glyph, tint))
    slot.setOnClickListener { if (enabled) onClick() }
    return slot
}

/** The homepage's stylized magnifier, 22dp. */
class SearchGlyphView(context: Context, tint: Int) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = tint
        strokeCap = Paint.Cap.ROUND
    }

    init {
        layoutParams = FrameLayout.LayoutParams(22f.cdp(context), 22f.cdp(context))
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = stroke
        w.strokeWidth = 2f * d
        val radius = 7f * d
        val cx = width * 0.44f
        val cy = height * 0.44f
        canvas.drawCircle(cx, cy, radius, w)
        canvas.drawLine(
            cx + radius * 0.72f, cy + radius * 0.72f,
            cx + radius + 3.6f * d, cy + radius + 3.6f * d, w,
        )
    }
}

/**
 * 48dp title bar: security/toolbar glyph on the left, the URL field content
 * in the middle (tap opens the address field, long press opens the title
 * menu), and the scanner/refresh/stop button on the right.
 */
class TitleBarView(
    context: Context,
    private val palette: ViewPalette,
    private val onOpenAddress: () -> Unit,
    private val onLeftClick: () -> Unit,
    private val onRightClick: () -> Unit,
    private val onTitleLongClick: () -> Unit,
    private val onSecureClick: () -> Unit,
) : LinearLayout(context) {

    private val leftSlot = FrameLayout(context)
    private val titleView = TextView(context)
    private val rightSlot = FrameLayout(context)

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(palette.background)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 48f.cdp(context),
        )
        leftSlot.layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
        addView(leftSlot)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        titleView.setTextColor(palette.onBackground)
        titleView.maxLines = 1
        titleView.ellipsize = android.text.TextUtils.TruncateAt.END
        titleView.includeFontPadding = false
        titleView.setPadding(8f.cdp(context), 0, 4f.cdp(context), 0)
        addView(titleView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        rightSlot.layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
        addView(rightSlot)
        bindTitleTap()
    }


    /** Pushes fresh tab data; rebuilds the two icon slots when they change. */
    fun bind(tab: TabInfo?, urlFieldContent: String) {
        val isHome = tab?.isHome != false
        titleView.text = if (isHome) "Homepage" else when (urlFieldContent) {
            "URL" -> tab?.url ?: ""
            "DOMAIN" -> tab?.url?.let { UrlField.domainOf(it) } ?: ""
            else -> tab?.title?.ifBlank { tab.url } ?: ""
        }
        val leftKey = when {
            isHome -> "search"
            tab?.incognito == true && tab.secure -> "incognito"
            tab?.secure == true -> "secure"
            else -> "search"
        }
        val rightKey = when {
            isHome -> "scanner"
            tab?.loading == true -> "close"
            else -> "refresh"
        }
        leftSlot.removeAllViews()
        if (isHome) {
            // Home: the left button is a search pill that opens address editing.
            val button = FrameLayout(context).apply {
                layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onLeftClick() }
                addView(SearchGlyphView(context, palette.onBackground))
            }
            leftSlot.addView(button)
        } else {
            val button = FrameLayout(context).apply {
                layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onSecureClick() }
                addView(chromeIconView(context, leftKey, palette.onSurface))
            }
            leftSlot.addView(button)
        }
        rightSlot.removeAllViews()
        val right = FrameLayout(context).apply {
            layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onRightClick() }
            addView(chromeIconView(context, rightKey, palette.onSurface))
        }
        rightSlot.addView(right)
    }

    /** Title tap opens the address bar; long press opens the title popup. */
    fun bindTitleTap() {
        titleView.isClickable = true
        titleView.isLongClickable = true
        titleView.setOnClickListener { onOpenAddress() }
        titleView.setOnLongClickListener { onTitleLongClick(); true }
        titleView.pressFeedback()
    }
}

/**
 * 48dp inline address bar: magnifier (or incognito glyph), a single-line URI
 * field with Go on the IME, clear, expand and go buttons.
 */
class AddressBarView(
    context: Context,
    private val palette: ViewPalette,
    private val incognito: Boolean,
    private val onValueChange: (String) -> Unit,
    private val onSubmit: () -> Unit,
    private val onExpand: () -> Unit,
) : LinearLayout(context) {

    val field = EditText(context)
    private val clearSlot = FrameLayout(context)
    private var suppress = false

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(palette.background)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 48f.cdp(context),
        )
        val lead = FrameLayout(context).apply {
            layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
            addView(
                if (incognito) chromeIconView(context, "incognito", palette.onSurface)
                else SearchGlyphView(context, palette.onBackground),
            )
        }
        addView(lead)
        field.background = null
        field.hint = "Search"
        field.setHintTextColor(palette.onBackground)
        field.setTextColor(palette.onBackground)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        field.includeFontPadding = false
        field.setSingleLine(true)
        field.imeOptions = EditorInfo.IME_ACTION_GO
        field.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        field.setPadding(0, 0, 0, 0)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppress) return
                onValueChange(s?.toString().orEmpty())
                clearSlot.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onSubmit(); true
            } else false
        }
        addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        clearSlot.layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
        val clear = FrameLayout(context).apply {
            layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { field.setText(""); field.setSelection(0) }
            addView(chromeIconView(context, "close", palette.onSurface))
        }
        clearSlot.addView(clear)
        clearSlot.visibility = View.GONE
        addView(clearSlot)

        addView(button48("expand") { onExpand() })
        addView(button48("forward") { onSubmit() })
    }

    private fun button48(glyph: String, onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onClick() }
            addView(chromeIconView(context, glyph, palette.onSurface))
        }

    /** Called once when the editor appears: caret in the field, keyboard up. */
    fun focusForEditing(text: String) {
        if (field.text.toString() != text) {
            field.setText(text)
        }
        field.setSelection(text.length)
        field.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    fun setTextExternal(text: String, cursorAtEnd: Boolean) {
        if (field.text.toString() != text) {
            suppress = true
            field.setText(text)
            suppress = false
            if (cursorAtEnd) field.setSelection(text.length)
        }
        clearSlot.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        field.requestFocus()
        field.setSelection(text.length)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    fun clearFocusAndKeyboard() {
        field.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(field.windowToken, 0)
    }
}

/** The tab count: tabs glyph with the count over it, 48dp square. */
class TabCountBadgeView(context: Context, private val tint: Int) : FrameLayout(context) {
    private val count = TextView(context)

    init {
        layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
        addView(chromeIconView(context, "tabs", tint).apply {
            layoutParams = LayoutParams(22f.cdp(context), 22f.cdp(context), Gravity.CENTER)
        })
        count.setTextColor(tint)
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        count.typeface = android.graphics.Typeface.DEFAULT_BOLD
        count.maxLines = 1
        count.gravity = Gravity.CENTER
        addView(count, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }

    fun setCount(value: Int) {
        count.text = if (value > 99) ":)" else value.toString()
    }
}

/**
 * 48dp five-slot bottom bar (search toolbar replaces the home slot with a
 * compact pill). Dragging the bar with more than one tab open follows the
 * finger up to 56dp and switches tab on release past 40dp of projected
 * travel, snapping back in the same frame like the composable original.
 */
class BottomBarView(
    context: Context,
    private val palette: ViewPalette,
    private val onBack: () -> Unit,
    private val onForward: () -> Unit,
    private val onHome: () -> Unit,
    private val onTabs: () -> Unit,
    private val onNewTab: () -> Unit,
    private val onMenu: () -> Unit,
    private val onMenuLong: () -> Unit,
    private val onBackLong: () -> Unit,
    private val onForwardLong: () -> Unit,
    private val onHomeLong: () -> Unit,
    private val onSwitchTab: (Int) -> Unit,
    private val searchToolbar: Boolean,
    private val onSearch: () -> Unit,
) : LinearLayout(context) {

    private val badge = TabCountBadgeView(context, palette.onSurface)
    private val slots = mutableListOf<View>()

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(palette.background)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 48f.cdp(context) + navInset(),
        )
        setPadding(0, 0, 0, navInset())
        clipChildren = false
        clipToPadding = false
        buildSlots()
        installDrag()
    }

    private fun navInset(): Int {
        val vid = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (vid > 0) context.resources.getDimensionPixelSize(vid) else 0
    }

    private fun slot(
        glyph: String,
        enabled: Boolean,
        onClick: () -> Unit,
        onLong: (() -> Unit)? = null,
    ): FrameLayout {
        val s = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, 48f.cdp(context), 1f)
            isClickable = true
            isFocusable = true
            alpha = if (enabled) 1f else 0.35f
            pressFeedback()
            setOnClickListener { onClick() }
            onLong?.let { setOnLongClickListener { it(); true } }
            addView(chromeIconView(context, glyph, palette.onSurface))
        }
        return s
    }

    private fun buildSlots() {
        val backSlot = slot("back", true, { if (backEnabled) onBack() }, { if (backEnabled) onBackLong() })
        val forwardSlot = slot("forward", true, { if (forwardEnabled) onForward() }, { if (forwardEnabled) onForwardLong() })
        slots.add(backSlot)
        slots.add(forwardSlot)
        addView(backSlot)
        addView(forwardSlot)
        if (searchToolbar) {
            val pill = LinearLayout(context).apply {
                layoutParams = LayoutParams(0, 48f.cdp(context), 2f)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val hp = 10f.cdp(context); val vp = 6f.cdp(context)
                setPadding(hp, vp, hp, vp)
                isClickable = true
                isFocusable = true
                pressFeedback(18f.cdp(context))
                setOnClickListener { onSearch() }
                addView(SearchGlyphView(context, palette.onSurfaceVariant).apply {
                    layoutParams = LayoutParams(16f.cdp(context), 16f.cdp(context))
                })
                addView(TextView(context).apply {
                    text = "Search"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(palette.onSurfaceVariant)
                    layoutParams = LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = 8f.cdp(context) }
                })
            }
            slots.add(pill)
            addView(pill)
        } else {
            addView(slot("home", true, onHome, onHomeLong))
        }
        val tabs = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, 48f.cdp(context), 1f)
            isClickable = true
            isFocusable = true
            pressFeedback(18f.cdp(context))
            setOnClickListener { onTabs() }
            setOnLongClickListener { onNewTab(); true }
            addView(badge)
        }
        slots.add(tabs)
        addView(tabs)
        addView(slot("menu", true, onMenu, onMenuLong))
    }

    // -- drag to switch tabs -------------------------------------------------

    private val velocity = android.view.VelocityTracker.obtain()
    private var dragStartX = 0f
    private var dragging = false
    private val followLimit = 56f.cdp(context).toFloat()
    private val commitDistance = 40f.cdp(context).toFloat()

    private fun installDrag() {
        setOnTouchListener { v, event ->
            velocity.addMovement(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.x; dragging = false
                    velocity.clear(); velocity.addMovement(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - dragStartX
                    if (!dragging && kotlin.math.abs(dx) > android.view.ViewConfiguration.get(context).scaledTouchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        translationX = dx.coerceIn(-followLimit, followLimit)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        velocity.computeCurrentVelocity(1000)
                        val projected = translationX + velocity.xVelocity * 0.05f
                        when {
                            projected < -commitDistance -> onSwitchTab(1)
                            projected > commitDistance -> onSwitchTab(-1)
                        }
                    }
                    translationX = 0f
                    dragging = false
                }
            }
            if (dragging) true else v.performClick()
        }
    }

    private var backEnabled = true
    private var forwardEnabled = true

    fun bind(count: Int, canBack: Boolean, canForward: Boolean, enabled: Boolean) {
        badge.setCount(count)
        if (slots.size > 1) {
            backEnabled = canBack
            forwardEnabled = canForward
            slots[0].alpha = if (canBack) 1f else 0.35f
            slots[1].alpha = if (canForward) 1f else 0.35f
        }
    }
}

/**
 * Full-screen address editor: the whole display is the editing canvas, long
 * addresses wrap, Enter submits, and the collapse/go row rides the keyboard.
 */
class FullScreenAddressBarView(
    context: Context,
    private val palette: ViewPalette,
    private val onValueChange: (String) -> Unit,
    private val onSubmit: () -> Unit,
    private val onCollapse: () -> Unit,
) : LinearLayout(context) {

    val field = EditText(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Status bar above, keyboard/nav bar below — the composable version
        // used statusBarsPadding + imePadding; here insets do it directly.
        setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val sys = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                v.setPadding(0, sys.top, 0, maxOf(sys.bottom, ime.bottom))
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.stableInsetBottom)
            }
            android.view.WindowInsets.CONSUMED
        }

        field.background = null
        field.hint = "Search"
        field.setHintTextColor(palette.onSurfaceVariant)
        field.setTextColor(palette.onBackground)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        field.gravity = Gravity.TOP or Gravity.START
        field.imeOptions = EditorInfo.IME_ACTION_GO
        field.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_URI or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                if (!text.contains('\n')) {
                    onValueChange(text)
                } else {
                    val cleaned = text.replace("\n", "")
                    field.setText(cleaned)
                    field.setSelection(cleaned.length)
                    onSubmit()
                }
            }
        })
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onSubmit(); true
            } else false
        }
        addView(field, LayoutParams(
            LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { setMargins(16f.cdp(context), 14f.cdp(context), 16f.cdp(context), 0) })

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 64f.cdp(context),
            ).apply { setMargins(12f.cdp(context), 0, 12f.cdp(context), 0) }
            val collapse = FrameLayout(context).apply {
                layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onCollapse() }
                addView(chromeIconView(context, "expand", palette.onSurface).apply {
                    rotation = 180f
                })
            }
            addView(collapse)
            addView(View(context).apply {
                layoutParams = LayoutParams(0, 1, 1f)
            })
            addView(View(context).apply {
                layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onSubmit() }
                addView(chromeIconView(context, "forward", palette.onSurface))
            })
        }
        addView(row)
    }

    fun focus(text: String) {
        if (field.text.toString() != text) {
            field.setText(text)
        }
        field.setSelection(text.length)
        field.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
}

/** A tab's favicon: live bitmap or the globe glyph, rounded 5dp. */
fun faviconView(context: Context, tab: TabInfo, sizeDp: Int, tint: Int): View {
    val size = sizeDp.cdp(context)
    val box = FrameLayout(context)
    box.layoutParams = FrameLayout.LayoutParams(size, size)
    val bitmap = tab.favicon
    if (bitmap != null && !bitmap.isRecycled) {
        box.addView(android.widget.ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 5f.cdp(context).toFloat())
                }
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            )
        })
    } else {
        box.addView(chromeIconView(context, "globe", tint, sizeDp = sizeDp - 4).apply {
            layoutParams = FrameLayout.LayoutParams(
                (sizeDp - 4).cdp(context), (sizeDp - 4).cdp(context), Gravity.CENTER,
            )
        })
    }
    return box
}

/**
 * Tab popup rows: 44dp per tab — favicon, one-line title (active in accent),
 * 48dp close slot — and a 50dp new-tab slot. Hosted inside the sheet window.
 */
class TabPopupView(
    context: Context,
    private val palette: ViewPalette,
    private val onSelect: (Long) -> Unit,
    private val onClose: (Long) -> Unit,
    private val onNewTab: () -> Unit,
) : ScrollView(context) {

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    init {
        setBackgroundColor(palette.surface)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(
                    0, 0, view.width, view.height + 24f.cdp(context),
                    24f.cdp(context).toFloat(),
                )
            }
        }
        isVerticalScrollBarEnabled = false
        addView(column)
        background = filledRound(24f.cdp(context), palette.surface)
    }

    fun bind(tabs: List<TabInfo>, activeId: Long?) {
        column.removeAllViews()
        tabs.forEach { tab ->
            val selected = tab.id == activeId
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 44f.cdp(context),
                )
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onSelect(tab.id) }
                addView(FrameLayout(context).apply {
                    layoutParams = LayoutParams(44f.cdp(context), 44f.cdp(context))
                    addView(faviconView(context, tab, 20, palette.onBackground).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                        )
                    })
                })
                addView(TextView(context).apply {
                    text = tab.title.ifBlank { tab.url ?: "Homepage" }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(if (selected) palette.primary else palette.onBackground)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(48f.cdp(context), 48f.cdp(context))
                    isClickable = true
                    isFocusable = true
                    pressFeedback()
                    setOnClickListener { onClose(tab.id) }
                    addView(chromeIconView(context, "close", palette.onSurface, sizeDp = 20).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            20f.cdp(context), 20f.cdp(context), Gravity.CENTER,
                        )
                    })
                })
            }
            column.addView(row)
        }
        column.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 50f.cdp(context),
            )
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onNewTab() }
            addView(chromeIconView(context, "plus", palette.onSurface, sizeDp = 24).apply {
                layoutParams = FrameLayout.LayoutParams(
                    24f.cdp(context), 24f.cdp(context), Gravity.CENTER,
                )
            })
        })
    }
}

/** The optional 40dp tab strip of the two-row toolbar. */
class TabStripView(
    context: Context,
    private val palette: ViewPalette,
    private val onSelect: (Long) -> Unit,
    private val onNewTab: () -> Unit,
) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    init {
        setBackgroundColor(palette.background)
        isHorizontalScrollBarEnabled = false
        addView(row)
    }

    fun bind(tabs: List<TabInfo>, activeId: Long?) {
        row.removeAllViews()
        tabs.forEach { tab ->
            val selected = tab.id == activeId
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                val hp = 2f.cdp(context); val vp = 4f.cdp(context)
                lp.setMargins(hp, 0, hp, 0)
                layoutParams = lp
                setPadding(10f.cdp(context), vp, 10f.cdp(context), vp)
                background = if (selected) filledRound(6f.cdp(context), palette.outlineVariant) else null
                isClickable = true
                isFocusable = true
                pressFeedback(6f.cdp(context))
                setOnClickListener { onSelect(tab.id) }
                addView(faviconView(context, tab, 14, palette.onBackground))
                addView(TextView(context).apply {
                    text = tab.title.ifBlank { tab.url ?: "Homepage" }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(palette.onBackground)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = 6f.cdp(context) }
                    width = 110f.cdp(context)
                })
            })
        }
        row.addView(FrameLayout(context).apply {
            val lp = LinearLayout.LayoutParams(36f.cdp(context), 36f.cdp(context))
            lp.setMargins(2f.cdp(context), 0, 2f.cdp(context), 0)
            layoutParams = lp
            isClickable = true
            isFocusable = true
            pressFeedback(6f.cdp(context))
            setOnClickListener { onNewTab() }
            addView(chromeIconView(context, "plus", palette.onSurface, sizeDp = 18).apply {
                layoutParams = FrameLayout.LayoutParams(
                    18f.cdp(context), 18f.cdp(context), Gravity.CENTER,
                )
            })
        })
    }
}

/**
 * The 214dp menu sheet: three swipeable pages of 5x2 grid cells, the page
 * dots, and the exit/close footer overlapping the grid by 12dp. Same
 * geometry as the composable MenuSheetContent it replaces.
 */
/**
 * The 214dp menu sheet: three swipeable pages of 5x2 grid cells, the page
 * dots, and the exit/close footer overlapping the grid by 12dp. Same
 * geometry as the composable MenuSheetContent it replaces.
 */
class MenuSheetView(
    context: Context,
    private val palette: ViewPalette,
    pages: List<List<MenuEntryV>>,
    private val onActionCell: (String) -> Unit,
    private val onExit: () -> Unit,
    private val onDismiss: () -> Unit,
) : LinearLayout(context) {

    private val pagerRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scroller: HorizontalScrollView = object : HorizontalScrollView(context) {
        override fun onScrollChanged(l: Int, t: Int, ol: Int, ot: Int) {
            super.onScrollChanged(l, t, ol, ot)
            settledListener?.invoke(
                if (pagerWidth > 0) (l + pagerWidth / 2) / pagerWidth else 0,
            )
        }

        override fun fling(velocityX: Int) { /* snapped on release instead */ }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val handled = super.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                val limit = pagesRef.size.coerceAtLeast(1) - 1
                val target = if (pagerWidth > 0) {
                    ((scrollX + pagerWidth / 2) / pagerWidth).coerceIn(0, limit)
                } else 0
                smoothScrollTo(target * pagerWidth, 0)
                settledListener?.invoke(target)
            }
            return handled
        }
    }
    private var pagesRef = pages
    private var pagerWidth = 0
    private val dots = mutableListOf<View>()
    private var settledListener: ((Int) -> Unit)? = null

    init {
        orientation = LinearLayout.VERTICAL
        background = filledRound(16f.cdp(context), palette.surface)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f.cdp(context).toFloat())
            }
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 214f.cdp(context),
        )
        setPadding(0, 6f.cdp(context), 0, 0)

        scroller.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        )
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(pagerRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        addView(scroller, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 14f.cdp(context),
            )
        }
        repeat(pages.size) { index ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(4f.cdp(context), 4f.cdp(context)).apply {
                    setMargins(4f.cdp(context), 0, 4f.cdp(context), 0)
                }
                isClickable = true
                isFocusable = true
                pressFeedback(oval = true)
                setOnClickListener {
                    settledListener?.invoke(index)
                    scroller.smoothScrollTo(index * pagerWidth.coerceAtLeast(1), 0)
                }
            }
            dots.add(dot)
            dotsRow.addView(dot)
        }
        addView(dotsRow)

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 52f.cdp(context),
            ).apply { topMargin = -12f.cdp(context) }
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(footerButton("power") { onExit() })
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(24f.cdp(context), 1)
            })
            addView(footerButton("chevrondown") { onDismiss() })
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(8f.cdp(context), 1)
            })
        }
        addView(footer)

        buildPages()
    }

    private fun setDotSelected(dot: View, selected: Boolean) {
        val color = if (selected) {
            tint(palette.onSurface, 0.42f)
        } else {
            tint(palette.onSurface, 0.18f)
        }
        dot.background = filledOval(color)
    }

    private fun footerButton(glyph: String, onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(48f.cdp(context), 48f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onClick() }
            addView(chromeIconView(context, glyph, palette.onSurface).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            })
        }

    private fun buildPages() {
        pagerRow.removeAllViews()
        pagesRef.forEachIndexed { pageIndex, pageEntries ->
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    if (pagerWidth > 0) pagerWidth else ViewGroup.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                )
            }
            pageEntries.chunked(5).take(2).forEach { rowEntries ->
                page.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
                    )
                    rowEntries.forEach { entry ->
                        addView(menuCell(entry))
                    }
                    repeat(5 - rowEntries.size) {
                        addView(
                            View(context),
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
                        )
                    }
                })
            }
            val params = pagerRow.layoutParams as FrameLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            pagerRow.addView(page)
        }
        scroller.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val w = scroller.width
                if (w > 0 && w != pagerWidth) {
                    pagerWidth = w
                    for (i in 0 until pagerRow.childCount) {
                        pagerRow.getChildAt(i).layoutParams = LinearLayout.LayoutParams(
                            w, LinearLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                }
            }
        })
        settledListener = { p ->
            dots.forEachIndexed { i, dot -> setDotSelected(dot, i == p) }
        }
        setDotSelected(dots.firstOrNull() ?: return, true)
    }

    fun bind(newPages: List<List<MenuEntryV>>) {
        pagesRef = newPages
        buildPages()
    }

    private fun menuCell(entry: MenuEntryV): LinearLayout {
        val tint = if (entry.accent) palette.primary else palette.onBackground
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setPadding(4f.cdp(context), 10f.cdp(context), 4f.cdp(context), 4f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback(18f.cdp(context))
            setOnClickListener { onActionCell(entry.actionName) }
            addView(chromeIconView(context, entry.iconKey, tint).apply {
                layoutParams = LinearLayout.LayoutParams(22f.cdp(context), 22f.cdp(context))
            })
            addView(TextView(context).apply {
                text = entry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                includeFontPadding = false
                setLineSpacing(0f, 13f / 12f)
                setTextColor(tint)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 8f.cdp(context) }
            })
        }
    }
}

/** View-side mirror of [MenuEntry] over the traced icon registry. */
data class MenuEntryV(
    val label: String,
    val iconKey: String,
    val accent: Boolean,
    val actionName: String,
)

/** The title long-press menu: Copy / Paste / Paste & Go in a 48dp pill. */
class TitleActionPopupView(
    context: Context,
    private val palette: ViewPalette,
    private val onCopy: () -> Unit,
    private val onPaste: () -> Unit,
    private val onPasteAndGo: () -> Unit,
) : LinearLayout(context) {

    private val copyAction = TextView(context)

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = filledRound(17f.cdp(context), palette.outlineVariant)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 17f.cdp(context).toFloat())
            }
        }
        val hp = 18f.cdp(context)
        setPadding(hp, 0, hp, 0)
        copyAction.apply {
            text = "Copy"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onBackground)
            setPadding(16f.cdp(context), 0, 16f.cdp(context), 0)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onCopy() }
        }
        addView(copyAction, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 48f.cdp(context),
        ))
        addView(titleAction("Paste") { onPaste() })
        addView(titleAction("Paste & Go") { onPasteAndGo() })
    }

    private fun titleAction(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onBackground)
            gravity = Gravity.CENTER
            setPadding(16f.cdp(context), 0, 16f.cdp(context), 0)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onClick() }
        }

    fun bind(showCopy: Boolean) {
        copyAction.visibility = if (showCopy) View.VISIBLE else View.GONE
    }
}

/**
 * The in-app toast pill: dark rounded capsule above the bottom bar, 14sp
 * white text, optional accent action, 1.6s/3.5s durations. Reads the same
 * JumpToast bus the View screens already post to.
 */
class ToastHostView(context: Context) : FrameLayout(context) {

    private var pendingDismiss: Runnable? = null
    private var shownId = 0L

    private val pill = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = filledRound(30f.cdp(context), 0xCC1E1E1E.toInt())
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 30f.cdp(context).toFloat())
            }
        }
        setPadding(16f.cdp(context), 0, 0, 0)
    }

    private val message = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        includeFontPadding = false
        isSingleLine = false
        maxLines = 3
        setPadding(0, 9f.cdp(context), 0, 9f.cdp(context))
    }

    private val actionView = TextView(context).apply {
        setTextColor(0xFF6F8DE1.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        isClickable = true
        isFocusable = true
        pressFeedback(18f.cdp(context))
        setPadding(16f.cdp(context), 12f.cdp(context), 16f.cdp(context), 12f.cdp(context))
    }

    init {
        pill.addView(message, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ))
        pill.addView(actionView)
        addView(pill, FrameLayout.LayoutParams(
            (360f.cdp(context)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ))
    }

    fun bind(request: JumpToastData?) {
        if (request == null) {
            pill.animate().alpha(0f).translationY(20f.cdp(context).toFloat())
                .setDuration(180).start()
            return
        }
        pill.removeAllViews()
        message.text = request.text
        pill.addView(message, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f,
        ))
        if (request.action != null) {
            actionView.text = request.action
            actionView.setOnClickListener {
                request.onAction?.invoke()
                JumpToast.dismiss()
            }
            actionView.visibility = View.VISIBLE
        } else {
            View(context).apply {
                layoutParams = LayoutParams(16f.cdp(context), 1)
            }.let { pill.addView(it) }
            actionView.visibility = View.GONE
        }
        pill.alpha = 0f
        pill.translationY = 20f.cdp(context).toFloat()
        pill.animate().alpha(1f).translationY(0f).setDuration(180).start()
        pendingDismiss?.let { removeCallbacks(it) }
        val dismiss = Runnable { JumpToast.dismissIf(request.id) }
        pendingDismiss = dismiss
        postDelayed(dismiss, request.duration)
        shownId = request.id
    }
}

/**
 * Suggestions dropdown under the address bar: the current page row (favicon,
 * title + url), then up to eight matching rows at 56dp, in a card that
 * bottoms out with 20dp rounded corners and caps at 360dp.
 */
class SuggestionDropdownView(
    context: Context,
    private val palette: ViewPalette,
    private val onPick: (Int?) -> Unit,
) : LinearLayout(context) {

    private val rows = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }
    private val scroll = ScrollView(context)

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(palette.surface)
        // Round only the bottom corners: 20dp bottom start/end.
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + 20f.cdp(context), 20f.cdp(context).toFloat())
            }
        }
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(rows)
        addView(scroll, LayoutParams(
            LayoutParams.MATCH_PARENT, (360 * resources.displayMetrics.density).toInt() + 1, 1f,
        ))
    }

    /**
     * Renders the full list; the row actions dispatch by index.
     * [current] carries the tab whose row, when [showCurrent] is true, sits
     * first (tap tag "C"), followed by suggestion rows (tap tag = index).
     */
    fun bind(current: TabInfo?, showCurrent: Boolean, suggestions: List<SearchSuggestion>) {
        rows.removeAllViews()
        if (current != null && showCurrent) {
            rows.addView(row(
                faviconOf = { faviconView(context, current, 24, palette.onBackground) },
                title = current.title.ifBlank { "Page" },
                subtitle = current.url,
                onClick = { onPick(null) },
            ))
            if (suggestions.isNotEmpty()) {
                rows.addView(View(context), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1,
                ).also { lp -> (lp as LinearLayout.LayoutParams).topMargin = 1 })
                rows.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1,
                    )
                    setBackgroundColor(palette.outlineVariant)
                })
            }
        }
        suggestions.forEach { suggestion ->
            val key = when (suggestion.kind) {
                SuggestionKind.BOOKMARK -> "bookmarksuggestion"
                SuggestionKind.HISTORY -> "historysuggestion"
                else -> "search"
            }
            rows.addView(row(
                faviconOf = {
                    chromeIconView(context, key, palette.onBackground).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                        )
                    }
                },
                title = suggestion.text,
                subtitle = suggestion.subtitle,
                onClick = { onPick(suggestions.indexOf(suggestion)) },
            ))
        }
    }

    private fun row(
        faviconOf: () -> View,
        title: String,
        subtitle: String?,
        onClick: () -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 56f.cdp(context),
        )
        isClickable = true
        isFocusable = true
        pressFeedback()
        setOnClickListener { onClick() }
        addView(FrameLayout(context).apply {
            layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
            addView(faviconOf())
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(palette.onBackground)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (!subtitle.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = subtitle
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(palette.onSurfaceVariant)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
        })
        addView(FrameLayout(context).apply {
            layoutParams = LayoutParams(48f.cdp(context), 48f.cdp(context))
            gravity = Gravity.CENTER
            addView(chromeIconView(context, "openarrow", palette.onBackground).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                )
            })
        })
    }
}

/** Error page: globe, "Can't reach this page", reason, Try again pill. */
class ErrorPageView(
    context: Context,
    private val palette: ViewPalette,
    private val onRetry: () -> Unit,
) : LinearLayout(context) {

    private val reason = TextView(context)
    private val retry = FrameLayout(context)

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(palette.background)
        val d = 28f.cdp(context)
        setPadding(d, d, d, d)
        addView(chromeIconView(context, "globe", palette.onSurfaceVariant, sizeDp = 54))
        addView(space(18))
        addView(TextView(context).apply {
            text = "Can't reach this page"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(palette.onBackground)
        })
        addView(space(8))
        reason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        reason.setTextColor(palette.onSurfaceVariant)
        reason.gravity = Gravity.CENTER
        reason.setLineSpacing(0f, 18f / 13f)
        addView(reason)
        addView(space(20))
        retry.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 40f.cdp(context),
        )
        val btn = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            )
            background = filledRound(20f.cdp(context), palette.primary)
            isClickable = true
            isFocusable = true
            pressFeedback(20f.cdp(context))
            setOnClickListener { onRetry() }
            addView(TextView(context).apply {
                text = "Try again"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(24f.cdp(context), 0, 24f.cdp(context), 0)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                )
            })
        }
        retry.addView(btn)
        addView(retry)
    }

    private fun space(dp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp.cdp(context))
    }

    fun bind(message: String?) {
        reason.text = message ?: "Check your connection and try again."
    }
}

/**
 * The find bar: 40dp pill with the query field, "n/m" counter, prev/next and
 * close. Focus and keyboard arrive on appearance.
 */
class FindBarView(
    context: Context,
    private val palette: ViewPalette,
    private val onQuery: (String) -> Unit,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    val field = EditText(context)
    private val counter = TextView(context)
    private val counterBox = LinearLayout(context)
    private var suppress = false

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = filledRound(20f.cdp(context), palette.surface)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f.cdp(context).toFloat())
            }
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 40f.cdp(context),
        )
        setPadding(16f.cdp(context), 0, 4f.cdp(context), 0)
        field.background = null
        field.hint = "Find in page"
        field.setHintTextColor(palette.onSurfaceVariant)
        field.setTextColor(palette.onBackground)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        field.includeFontPadding = false
        field.setSingleLine(true)
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppress) return
                onQuery(s?.toString().orEmpty())
                counterBox.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onNext(); true
            } else false
        }
        addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        counterBox.orientation = LinearLayout.HORIZONTAL
        counterBox.gravity = Gravity.CENTER_VERTICAL
        counterBox.visibility = View.GONE
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        counter.setTextColor(palette.onSurfaceVariant)
        counterBox.addView(counter, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(8f.cdp(context), 0, 8f.cdp(context), 0) })
        counterBox.addView(findButtonSlot("chevronup") { onPrev() })
        counterBox.addView(findButtonSlot("chevrondown") { onNext() })
        addView(counterBox)
        addView(findButtonSlot("close") { onClose() })
    }

    private fun findButtonSlot(glyph: String, onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(48f.cdp(context), 48f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onClick() }
            addView(chromeIconView(context, glyph, palette.onSurface).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                )
            })
        }

    fun bind(query: String, matches: Pair<Int, Int>, focused: Boolean) {
        if (field.text.toString() != query) {
            suppress = true
            field.setText(query)
            suppress = false
            field.setSelection(query.length)
        }
        counter.text = if (matches.second == 0) "0/0" else "${matches.first + 1}/${matches.second}"
        counterBox.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        if (focused) {
            field.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

/** Line-icon variant: 24-unit stroked glyph like the JumpIcons set. */
fun lineIconView(context: Context, key: String, tint: Int, sizeDp: Int = 21): View =
    GlyphView(context, ReferenceIcons.paths.getValue(key), tint, viewport = 24f, strokeWidthDp = 1.55f * sizeDp / 24f).apply {
        layoutParams = FrameLayout.LayoutParams(sizeDp.cdp(context), sizeDp.cdp(context))
    }

/**
 * Web context menu: scrim, bottom card with the rounded top, host header,
 * and the action rows for links/images.
 */
class ContextMenuView(
    context: Context,
    private val palette: ViewPalette,
    rowGroups: List<List<Pair<String, String>>>,
    private val onAction: (Int, Int) -> Unit,
    private val onDismissMenu: () -> Unit,
) : LinearLayout(context) {

    private val header = TextView(context)

    init {
        orientation = LinearLayout.VERTICAL
        background = filledRoundTop(20f.cdp(context), palette.surface)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + 20f.cdp(context), 20f.cdp(context).toFloat())
            }
        }
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        header.setTextColor(palette.onSurfaceVariant)
        header.maxLines = 1
        header.ellipsize = android.text.TextUtils.TruncateAt.END
        header.setPadding(20f.cdp(context), 16f.cdp(context), 20f.cdp(context), 8f.cdp(context))
        addView(header)
        rowGroups.forEachIndexed { gi, rows ->
            rows.forEachIndexed { ri, row ->
                addView(contextRow(row.second, row.first) { onAction(gi, ri) })
            }
        }
        addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 10f.cdp(context),
        ))
    }

    fun bind(headerText: String) {
        header.text = headerText
    }

    private fun contextRow(key: String, label: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onClick() }
            addView(lineIconView(context, key, palette.onBackground))
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(palette.onBackground)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { leftMargin = 18f.cdp(context) }
            })
            setPadding(20f.cdp(context), 13f.cdp(context), 20f.cdp(context), 13f.cdp(context))
        }
}

/** Tools dialog rows inside the JumpAlertDialog framing, on Views. */
class ToolsDialogView(
    context: Context,
    private val palette: ViewPalette,
    rows: List<Pair<String, String>>,
    private val onRow: (Int) -> Unit,
    private val onDismissTools: () -> Unit,
) : LinearLayout(context) {

    init {
        orientation = LinearLayout.VERTICAL
        background = filledRound(16f.cdp(context), palette.surface)
        clipChildren = false
        val title = TextView(context).apply {
            text = "Tools"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(palette.onBackground)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(24f.cdp(context), 20f.cdp(context), 24f.cdp(context), 8f.cdp(context))
        }
        addView(title)
        rows.forEachIndexed { i, row ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onRow(i) }
                addView(lineIconView(context, row.first, palette.onBackground))
                addView(TextView(context).apply {
                    text = row.second
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(palette.onBackground)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = 18f.cdp(context) }
                })
                setPadding(24f.cdp(context), 11f.cdp(context), 24f.cdp(context), 11f.cdp(context))
            })
        }
        addView(View(context), LinearLayout.LayoutParams(1, 6f.cdp(context)))
        addView(TextView(context).apply {
            text = "Close"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.primary)
            gravity = Gravity.CENTER
            isClickable = true
            pressFeedback(18f.cdp(context))
            setPadding(16f.cdp(context), 12f.cdp(context), 16f.cdp(context), 12f.cdp(context))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.END; setPadding(0, 0, 8f.cdp(context), 8f.cdp(context)) }
            gravity = Gravity.END
            setOnClickListener { onDismissTools() }
        })
    }
}
