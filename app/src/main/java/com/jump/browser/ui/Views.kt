package com.jump.browser.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.PathParser
import kotlin.math.roundToInt

/**
 * Shared toolkit for the View-hosted screens. Only platform views live
 * here: no Compose types, no new libraries. Press feedback mirrors
 * JumpPressIndication (a 0x30808080 overlay) through foreground
 * RippleDrawables, so every button answers the finger like the Compose
 * chrome does.
 */
class ViewPalette(
    val background: Int,
    val surface: Int,
    val onBackground: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outlineVariant: Int,
    val primary: Int,
    val error: Int,
) {
    companion object {
        private const val PRESSED = 0x30808080

        fun forChrome(chromeDark: Boolean): ViewPalette = if (chromeDark) ViewPalette(
            background = 0xFF1C1B1D.toInt(),
            surface = 0xFF242326.toInt(),
            onBackground = 0xBEFFFFFF.toInt(),
            onSurface = 0xFF9F9F9F.toInt(),
            onSurfaceVariant = 0x99FFFFFF.toInt(),
            outlineVariant = 0xFF333336.toInt(),
            primary = 0xFF617AC1.toInt(),
            error = 0xFFFF7A7E.toInt(),
        ) else ViewPalette(
            background = 0xFFFFFFFF.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            onBackground = 0xDE000000.toInt(),
            onSurface = 0xFF232323.toInt(),
            onSurfaceVariant = 0x8A000000.toInt(),
            outlineVariant = 0xFFE4E4E4.toInt(),
            primary = 0xFF6F8DE1.toInt(),
            error = 0xFFD64146.toInt(),
        )

        fun pressed(): Int = PRESSED
    }
}

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).roundToInt()

fun Float.dp(context: Context): Int = (this * context.resources.displayMetrics.density + 0.5f).roundToInt()

/** A styled label; the workhorse of every view screen. */
fun Context.text(
    text: String,
    size: Float,
    color: Int,
    bold: Boolean = false,
    maxLines: Int = 1,
    truncate: Boolean = true,
): TextView = TextView(this).apply {
    setText(text)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    setTextColor(color)
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    this.maxLines = maxLines
    if (truncate && maxLines == 1) ellipsize = android.text.TextUtils.TruncateAt.END
    includeFontPadding = false
}

/** Touch feedback: a foreground ripple painting JumpPressed on press. */
fun View.pressFeedback(cornerRadiusPx: Int = 0, oval: Boolean = false) {
    val mask = if (oval) {
        ShapeDrawable(OvalShape())
    } else {
        val r = cornerRadiusPx.toFloat()
        ShapeDrawable(RoundRectShape(floatArrayOf(r, r, r, r, r, r, r, r), null, null))
    }
    foreground = RippleDrawable(android.content.res.ColorStateList.valueOf(ViewPalette.pressed()), null, mask)
}

fun filledRound(radiusPx: Int, color: Int): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(RectF(bounds), radiusPx.toFloat(), radiusPx.toFloat(), paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

fun strokedRound(radiusPx: Int, strokePx: Int, color: Int): Drawable = object : Drawable() {    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx.toFloat()
        this.color = color
    }
    override fun draw(canvas: Canvas) {
        val half = strokePx / 2f
        val rect = RectF(bounds)
        rect.inset(half, half)
        canvas.drawRoundRect(rect, radiusPx.toFloat(), radiusPx.toFloat(), paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/** Fills an oval; the bookmark favicon's background. */
fun filledOval(color: Int): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    override fun draw(canvas: Canvas) {
        canvas.drawOval(RectF(bounds), paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/**
 * Draws a glyph at any size; the path is parsed once. Traced reference icons
 * fill an 88-unit box; the line icons use a 24-unit box with a stroke.
 */
class GlyphView(
    context: Context,
    pathData: String,
    tint: Int,
    private val viewport: Float = 88f,
    private val strokeWidthDp: Float = 0f,
) : View(context) {
    private val path: Path = PathParser.createPathFromPathData(pathData)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = if (strokeWidthDp > 0f) Paint.Style.STROKE else Paint.Style.FILL
        if (strokeWidthDp > 0f) {
            strokeWidth = strokeWidthDp * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.coerceAtMost(height).toFloat()
        canvas.save()
        canvas.scale(size / viewport, size / viewport)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

/** The 48-dp title row both library scaffolds share. */
fun Context.libraryTitleRow(
    palette: ViewPalette,
    title: String,
    onBack: () -> Unit,
    actions: (LinearLayout.() -> Unit)? = null,
): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48f),
        )
    }
    row.addView(FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(56f), dp(56f))
        isClickable = true
        isFocusable = true
        pressFeedback(oval = true)
        setOnClickListener { onBack() }
        addView(GlyphView(this@libraryTitleRow, ReferenceIcons.paths.getValue("back"), palette.onSurface).apply {
            layoutParams = FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
        })
    })
    row.addView(text(title, 16f, palette.onBackground, bold = true).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    })
    actions?.invoke(row)
    return row
}

/** The 44-dp rounded search pill with live text callback. */
fun Context.searchPill(palette: ViewPalette, onQuery: (String) -> Unit): FrameLayout {
    val box = FrameLayout(this).apply {
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(44f))
        lp.leftMargin = dp(14f); lp.topMargin = dp(12f)
        lp.rightMargin = dp(14f); lp.bottomMargin = dp(12f)
        layoutParams = lp
        background = filledRound(dp(22f), 0xFFF2F2F2.toInt())
    }
    box.addView(EditText(this).apply {
        background = null
        hint = "Search"
        setHintTextColor(palette.onSurfaceVariant)
        setTextColor(palette.onBackground)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSingleLine = true
        val pad = dp(16f)
        setPadding(pad, 0, pad, 0)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_VERTICAL,
        )
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onQuery(s?.toString().orEmpty())
        })
    })
    return box
}

/** The 0.5dp divider line (1px at common densities). */
fun Context.dividerLine(palette: ViewPalette): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(1f),
    )
    setBackgroundColor(palette.outlineVariant)
}

fun Context.spacer(widthPx: Int = 0, weight: Float = 1f): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(width, 1, weight)
}

fun Context.emptyMemeView(): TextView = TextView(this).apply {
    text = "¯\\_(ツ)_/¯"
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
    setTextColor(0xFF808080.toInt())
    gravity = Gravity.CENTER
    val lp = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
    )
    lp.bottomMargin = dp(20f)
    layoutParams = lp
}

/** Underlined text field like JumpTextField (label hive: 14sp, hint, 1dp rule). */
fun Context.textField(
    palette: ViewPalette,
    placeholder: String,
    uri: Boolean = false,
    onText: (String) -> Unit,
): LinearLayout {
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val edit = EditText(this).apply {
        background = null
        hint = placeholder
        setHintTextColor(palette.onSurfaceVariant)
        setTextColor(palette.onBackground)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        isSingleLine = true
        val vpad = dp(8f)
        setPadding(0, vpad, 0, vpad)
        inputType = if (uri) android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_URI else
            android.text.InputType.TYPE_CLASS_TEXT
        setOnClickListener { }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
        })
    }
    column.addView(edit)
    column.addView(View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1f))
        setBackgroundColor(ViewPalette.pressed())
    })
    return column
}

/** A styled alert dialog matching JumpAlertDialog's card. */
fun Context.alertDialog(
    palette: ViewPalette,
    title: String? = null,
    message: String? = null,
    body: (LinearLayout) -> Unit = {},
    confirm: String? = null,
    dismiss: String? = null,
    onDismiss: () -> Unit = {},
    onConfirm: () -> Unit = {},
): Dialog {
    val dialog = Dialog(this)
    val card = FrameLayout(this).apply {
        background = filledRound(dp(16f), palette.surface)
    }
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(20f)
        setPadding(pad, dp(18f), pad, dp(8f))
    }
    title?.let {
        column.addView(text(it, 18f, palette.onBackground, bold = true).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(10f)
            layoutParams = lp
        })
    }
    message?.let {
        column.addView(text(it, 14f, palette.onBackground).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6f)
            layoutParams = lp
        })
    }
    body(column)
    val buttons = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }
    fun dialogButton(label: String, action: () -> Unit) = TextView(this@alertDialog).apply {
        setText(label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(palette.primary)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        gravity = Gravity.CENTER
        minWidth = dp(56f)
        minHeight = dp(48f)
        val hpad = dp(8f)
        setPadding(hpad, 0, hpad, 0)
        isClickable = true
        pressFeedback(dp(18f))
        setOnClickListener { action() }
    }
    dismiss?.let { buttons.addView(dialogButton(it) { dialog.dismiss(); onDismiss() }) }
    if (confirm != null) buttons.addView(dialogButton(confirm) { dialog.dismiss(); onConfirm() })
    column.addView(buttons)
    card.addView(column)
    dialog.setContentView(card)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    dialog.setOnCancelListener { onDismiss() }
    return dialog
}

/** The edit-mode select circle: 1dp ring, primary fill and a white tick when on. */
class CircleCheckView(context: Context, palette: ViewPalette, selected: Boolean) : View(context) {
    init { isSelected = selected }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFD0D0D0.toInt()
        strokeWidth = 1f.dp(context).toFloat()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.9f / 24f * 20
    }

    override fun onDraw(canvas: Canvas) {
        val half = width / 2f
        if (isSelected) {
            canvas.drawCircle(half, half, half - 1f, fill)
            canvas.drawLines(
                floatArrayOf(
                    width * 4.8f / 24f, height * 12.5f / 24f,
                    width * 9.6f / 24f, height * 17.2f / 24f,
                    width * 9.6f / 24f, height * 17.2f / 24f,
                    width * 19.2f / 24f, height * 6.8f / 24f,
                ), tick,
            )
        } else {
            canvas.drawCircle(half, half, half - 1f, ring)
        }
    }
}

/** A 2dp progress strip like Compose's LinearProgressIndicator. */
class ThinProgress(context: Context, track: Int, fill: Int) : View(context) {
    var fraction: Float = 0f
        set(value) { field = value; invalidate() }
    private val trackPaint = Paint().apply { color = track }
    private val fillPaint = Paint().apply { color = fill }
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(bounds, trackPaint)
        if (fraction > 0f) {
            bounds.right = width * fraction
            canvas.drawRect(bounds, fillPaint)
        }
    }
}

/** Top-rounded fill: bottom sheets and dropdown cards. */
fun filledRoundTop(radiusPx: Int, color: Int): android.graphics.drawable.GradientDrawable =
    android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadii = floatArrayOf(
            radiusPx.toFloat(), radiusPx.toFloat(),
            radiusPx.toFloat(), radiusPx.toFloat(),
            0f, 0f, 0f, 0f,
        )
    }
