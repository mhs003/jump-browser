package com.jump.browser.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import com.jump.browser.data.DownloadState
import com.jump.browser.data.DownloadTask
import kotlin.math.roundToInt

/**
 * The downloads screen without the Compose framework.
 *
 * Everything the composable version rendered is rebuilt with platform
 * views: title bar, search field, filter chips, the grouped list with
 * thumbnails and progress strips, edit mode with its select circle, and
 * the two bottom bars. Icons are drawn from Jump's own traced path data
 * through androidx's SVG path parser, so no icon library or artwork is
 * added from anywhere.
 */
class DownloadsViewHost(
    context: Context,
    private val palette: Palette,
    private val onBack: () -> Unit,
    private val onOpen: (DownloadTask) -> Unit,
    private val onDelete: (Long) -> Unit,
) : LinearLayout(context) {

    /** Chrome colors mirroring JumpTheme's light and dark schemes. */
    class Palette(
        val background: Int,
        val onBackground: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outlineVariant: Int,
        val primary: Int,
    ) {
        companion object {
            fun forChrome(chromeDark: Boolean): Palette = if (chromeDark) Palette(
                background = 0xFF1C1B1D.toInt(),
                onBackground = 0xBEFFFFFF.toInt(),
                onSurface = 0xFF9F9F9F.toInt(),
                onSurfaceVariant = 0x99FFFFFF.toInt(),
                outlineVariant = 0xFF333336.toInt(),
                primary = 0xFF617AC1.toInt(),
            ) else Palette(
                background = 0xFFFFFFFF.toInt(),
                onBackground = 0xDE000000.toInt(),
                onSurface = 0xFF232323.toInt(),
                onSurfaceVariant = 0x8A000000.toInt(),
                outlineVariant = 0xFFE4E4E4.toInt(),
                primary = 0xFF6F8DE1.toInt(),
            )
        }
    }

    private val density = context.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())

    private fun Float.dp(): Int = (this * density + 0.5f).roundToInt()

    private var tasks: List<DownloadTask> = emptyList()
    private var query = ""
    private var filter = 0
    private var editing = false
    private val selected = HashSet<Long>()

    private val filters = listOf("All", "Documents", "Archives", "APK", "Images", "Videos")

    private val chipsRow: LinearLayout
    private val contentFrame: FrameLayout
    private val listScroll: ScrollView
    private val listHost: LinearLayout
    private val emptyView: TextView
    private val barHost: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)

        // Title bar: 48dp row, back glyph at the left, bold title.
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48f.dp())
        }
        titleRow.addView(iconButton(ReferenceIcons.BackPath, 22f.dp(), palette.onSurface) { onBack() })
        titleRow.addView(TextView(context).apply {
            text = "Downloads"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(palette.onBackground)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(titleRow)
        addView(divider())

        // Search field: 44dp pill, filters live.
        val searchBox = FrameLayout(context).apply {
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, 44f.dp())
            lp.leftMargin = 14f.dp(); lp.topMargin = 12f.dp()
            lp.rightMargin = 14f.dp(); lp.bottomMargin = 12f.dp()
            layoutParams = lp
            background = filledRound(22f.dp(), 0xFFF2F2F2.toInt())
        }
        searchBox.addView(EditText(context).apply {
            background = null
            hint = "Search"
            setHintTextColor(palette.onSurfaceVariant)
            setTextColor(palette.onBackground)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isSingleLine = true
            val pad = 16f.dp()
            setPadding(pad, 0, pad, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL,
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    rebuildList()
                }
            })
        })
        addView(searchBox)

        // Filter chips.
        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        chipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = 16f.dp(); lp.rightMargin = 16f.dp(); lp.bottomMargin = 4f.dp()
            layoutParams = lp
        }
        chipsScroll.addView(chipsRow)
        addView(chipsScroll)

        // List area.
        contentFrame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        emptyView = TextView(context).apply {
            text = "¯\\_(ツ)_/¯"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(0xFF808080.toInt())
            gravity = Gravity.CENTER
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            lp.bottomMargin = 20f.dp()
            layoutParams = lp
        }
        listScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        listHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        listScroll.addView(listHost)
        contentFrame.addView(emptyView)
        contentFrame.addView(listScroll)
        addView(contentFrame)

        addView(divider())

        // Bottom action bar: New / Edit, or Select all / Delete / Done.
        barHost = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48f.dp())
            val pad = 16f.dp()
            setPadding(pad, 0, pad, 0)
        }
        addView(barHost)

        rebuildChips()
        rebuildList()
        rebuildBar()
    }

    /** Data push from the composing host: swap the list and refresh. */
    fun submitTasks(updated: List<DownloadTask>) {
        tasks = updated
        rebuildList()
        rebuildBar()
    }

    // ------------------------------------------------------------ building

    private fun rebuildChips() {
        chipsRow.removeAllViews()
        filters.forEachIndexed { index, label ->
            val active = filter == index
            val chip = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (active) palette.primary else palette.onBackground)
                gravity = Gravity.CENTER
                val hpad = 14f.dp()
                setPadding(hpad, 0, hpad, 0)
                background = strokedRound(
                    11f.dp(), 1f.dp(),
                    if (active) palette.primary else 0xFFD3D3D3.toInt(),
                )
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 22f.dp()).apply {
                    if (index > 0) leftMargin = 10f.dp()
                }
                setOnClickListener { filter = index; rebuildChips(); rebuildList() }
            }
            chipsRow.addView(chip)
        }
    }

    private fun rebuildList() {
        val filtered = tasks.filter { matchesFilter(it) && matchesQuery(it) }
        if (filtered.isEmpty()) {
            contentFrame.removeAllViews()
            contentFrame.addView(emptyView)
            return
        }
        contentFrame.removeAllViews()
        contentFrame.addView(listScroll)
        listHost.removeAllViews()
        val grouped = filtered.groupBy {
            java.time.Instant.ofEpochMilli(it.createdAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }.toSortedMap(compareByDescending { it })

        val dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.getDefault())
        grouped.forEach { (day, dayTasks) ->
            listHost.addView(TextView(context).apply {
                text = day.format(dayFormat)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(palette.onSurfaceVariant)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                lp.leftMargin = 16f.dp(); lp.topMargin = 16f.dp(); lp.bottomMargin = 4f.dp()
                layoutParams = lp
            })
            dayTasks.forEach { task -> listHost.addView(recordRow(task)) }
        }
        listHost.addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 12f.dp())
        })
    }

    private fun matchesQuery(task: DownloadTask): Boolean =
        query.isBlank() || task.title.contains(query, true) || task.url.contains(query, true)

    private fun matchesFilter(task: DownloadTask): Boolean = when (filter) {
        1 -> task.isDownloadDocument()
        2 -> task.isDownloadArchive()
        3 -> task.isDownloadApk()
        4 -> task.isDownloadImage()
        5 -> task.isDownloadVideo()
        else -> true
    }

    private fun recordRow(task: DownloadTask): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val hpad = 16f.dp(); val vpad = 12f.dp()
            setPadding(hpad, vpad, hpad, vpad)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (editing) {
                    if (task.id in selected) selected.remove(task.id) else selected.add(task.id)
                    rebuildList(); rebuildBar()
                } else if (task.state == DownloadState.COMPLETE) onOpen(task)
            }
        }
        row.addView(recordIcon(task))
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8f.dp(), 0, 0, 0)
        }
        textCol.addView(TextView(context).apply {
            text = task.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(palette.onBackground)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(context).apply {
            text = when (task.state) {
                DownloadState.COMPLETE -> task.sizeText
                DownloadState.FAILED -> "Failed"
                DownloadState.RUNNING -> task.sizeText.ifBlank { "Downloading…" }
                DownloadState.PENDING -> "Queued"
                DownloadState.PAUSED -> "Paused"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.onSurfaceVariant)
        })
        if (task.state == DownloadState.RUNNING || task.state == DownloadState.PENDING) {
            textCol.addView(ThinProgress(context, palette.outlineVariant, palette.primary).apply {
                fraction = task.fraction
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 2f.dp(),
                ).apply { topMargin = 4f.dp() }
            })
        }
        row.addView(textCol)
        if (editing) {
            row.addView(CircleCheck(context, palette, task.id in selected).apply {
                layoutParams = LayoutParams(20f.dp(), 20f.dp()).apply { leftMargin = 8f.dp() }
            })
        }
        return row
    }

    private fun rebuildBar() {
        barHost.removeAllViews()
        fun barText(label: String, enabled: Boolean = true, tint: Int = palette.onBackground, action: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (enabled) tint else palette.onSurfaceVariant)
                val vpad = 8f.dp()
                setPadding(0, vpad, 0, vpad)
                isClickable = enabled
                if (enabled) setOnClickListener { action() }
            }

        if (editing) {
            barHost.addView(barText("Select all") {
                tasks.filter { matchesFilter(it) && matchesQuery(it) }.forEach { selected.add(it.id) }
                rebuildList(); rebuildBar()
            })
            barHost.addView(spacer())
            barHost.addView(barText("Delete", selected.isNotEmpty()) {
                selected.toList().forEach(onDelete)
                selected.clear()
                editing = false
                rebuildList(); rebuildBar()
            })
            barHost.addView(spacer(28f.dp()))
            barHost.addView(barText("Done") {
                editing = false; selected.clear(); rebuildList(); rebuildBar()
            })
        } else {
            barHost.addView(barText("New") {
                JumpToast.show(context, "Start a download from a web page")
            })
            barHost.addView(spacer())
            barHost.addView(barText("Edit", tasks.isNotEmpty()) {
                editing = true; rebuildList(); rebuildBar()
            })
        }
    }

    // ------------------------------------------------------------ widgets

    private fun divider(): View = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(palette.outlineVariant)
    }

    private fun spacer(width: Int = 0): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1, 1f)
    }

    private fun iconButton(pathData: String, sizePx: Int, tint: Int, action: () -> Unit): View =
        FrameLayout(context).apply {
            layoutParams = LayoutParams(56f.dp(), 56f.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(GlyphView(context, pathData, tint).apply {
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
            })
        }

    private fun recordIcon(task: DownloadTask): FrameLayout {
        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(30f.dp(), 30f.dp())
        }
        fun showGlyph() {
            frame.removeAllViews()
            frame.addView(GlyphView(context, ReferenceIcons.DownloadPath, palette.onSurface).apply {
                layoutParams = FrameLayout.LayoutParams(22f.dp(), 22f.dp(), Gravity.CENTER)
            })
        }
        showGlyph()
        if (task.localUri != null && task.state == DownloadState.COMPLETE) {
            Thread {
                val bitmap: android.graphics.Bitmap? = runCatching {
                    when {
                        task.isDownloadImage() -> {
                            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                            context.contentResolver.openInputStream(Uri.parse(task.localUri))?.use {
                                BitmapFactory.decodeStream(it, null, options)
                            }
                        }
                        task.isDownloadApk() -> {
                            val path = java.io.File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                task.title,
                            ).absolutePath
                            val info = context.packageManager.getPackageArchiveInfo(path, 0) ?: return@runCatching null
                            val appInfo = info.applicationInfo ?: return@runCatching null
                            appInfo.sourceDir = path
                            appInfo.publicSourceDir = path
                            context.packageManager.getApplicationIcon(appInfo).toBitmap()
                        }
                        else -> null
                    }
                }.getOrNull()
                if (bitmap != null) main.post {
                    frame.removeAllViews()
                    frame.addView(ImageView(context).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, 6f.dp().toFloat())
                            }
                        }
                        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    })
                }
            }.start()
        }
        return frame
    }

    // ------------------------------------------------------------ drawing

    private fun filledRound(radiusPx: Int, color: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(RectF(bounds), radiusPx.toFloat(), radiusPx.toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun strokedRound(radiusPx: Int, strokePx: Int, color: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    /** Fills a traced 88-unit glyph at any size; the path is parsed once. */
    class GlyphView(context: Context, pathData: String, tint: Int) : View(context) {
        private val path: Path = PathParser.createPathFromPathData(pathData)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tint
        }

        override fun onDraw(canvas: Canvas) {
            val size = width.coerceAtMost(height).toFloat()
            canvas.save()
            canvas.scale(size / 88f, size / 88f)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    /** The edit-mode select circle: 1dp ring, primary fill and a white tick when on. */
    private class CircleCheck(
        context: Context,
        palette: Palette,
        selected: Boolean,
    ) : View(context) {
        init {
            this.isSelected = selected
        }

        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFD0D0D0.toInt()
            strokeWidth = 1f * context.resources.displayMetrics.density
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
    private class ThinProgress(context: Context, track: Int, fill: Int) : View(context) {
        var fraction: Float = 0f
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
}

// The same category predicates the composable screen used.
internal fun DownloadTask.isDownloadApk(): Boolean =
    title.endsWith(".apk", true) || mime.contains("android.package")

internal fun DownloadTask.isDownloadImage(): Boolean =
    mime.startsWith("image/") || title.substringAfterLast('.', "").lowercase() in
        setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")

internal fun DownloadTask.isDownloadVideo(): Boolean =
    mime.startsWith("video/") || title.substringAfterLast('.', "").lowercase() in
        setOf("mp4", "mkv", "webm", "avi", "mov", "flv")

internal fun DownloadTask.isDownloadArchive(): Boolean =
    title.substringAfterLast('.', "").lowercase() in
        setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

internal fun DownloadTask.isDownloadDocument(): Boolean =
    mime.startsWith("text/") || mime.contains("pdf") || mime.contains("word") ||
        mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") ||
        title.substringAfterLast('.', "").lowercase() in
        setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv")
