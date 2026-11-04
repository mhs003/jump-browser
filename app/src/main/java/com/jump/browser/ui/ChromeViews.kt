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
import android.widget.LinearLayout
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
        orientation = HORIZONTAL
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
        orientation = HORIZONTAL
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
        orientation = HORIZONTAL
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
                orientation = HORIZONTAL
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
            orientation = HORIZONTAL
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
