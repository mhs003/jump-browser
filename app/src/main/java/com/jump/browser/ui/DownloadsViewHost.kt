package com.jump.browser.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
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
import androidx.core.graphics.drawable.toBitmap
import com.jump.browser.data.DownloadState
import com.jump.browser.data.DownloadTask

/**
 * The downloads screen without the Compose framework.
 *
 * Everything the composable version rendered is rebuilt with platform
 * views: title bar, search field, filter chips, the grouped list with
 * thumbnails and progress strips, edit mode with its select circle, and
 * the two bottom bars. Icons are drawn from Jump's own traced path data
 * through androidx's SVG path parser, so no icon library or artwork is
 * added from anywhere. Every tappable control carries the JumpPressed
 * feedback.
 */
class DownloadsViewHost(
    context: Context,
    private val palette: ViewPalette,
    private val onBack: () -> Unit,
    private val onOpen: (DownloadTask) -> Unit,
    private val onDelete: (Long) -> Unit,
) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density

    private fun Float.dp(): Int = context.dp(this)

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

        addView(context.libraryTitleRow(palette, "Downloads", onBack))
        addView(context.dividerLine(palette))
        addView(context.searchPill(palette) { query = it; rebuildList() })

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

        contentFrame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        emptyView = context.emptyMemeView()
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

        addView(context.dividerLine(palette))
        barHost = LinearLayout(context).apply {
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
                isClickable = true
                isFocusable = true
                pressFeedback(11f.dp())
                setOnClickListener { filter = index; rebuildChips(); rebuildList() }
            }
            chipsRow.addView(chip)
        }
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
            listHost.addView(
                context.text(
                    day.format(dayFormat), 12f, palette.onSurfaceVariant,
                ).apply {
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    lp.leftMargin = 16f.dp(); lp.topMargin = 16f.dp(); lp.bottomMargin = 4f.dp()
                    layoutParams = lp
                },
            )
            dayTasks.forEach { task -> listHost.addView(recordRow(task)) }
        }
        listHost.addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 12f.dp())
        })
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
            pressFeedback()
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
        textCol.addView(
            context.text(task.title, 16f, palette.onBackground),
        )
        textCol.addView(context.text(
            when (task.state) {
                DownloadState.COMPLETE -> task.sizeText
                DownloadState.FAILED -> "Failed"
                DownloadState.RUNNING -> task.sizeText.ifBlank { "Downloading…" }
                DownloadState.PENDING -> "Queued"
                DownloadState.PAUSED -> "Paused"
            },
            13f, palette.onSurfaceVariant,
        ))
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
            row.addView(CircleCheckView(context, palette, task.id in selected).apply {
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
                isFocusable = enabled
                if (enabled) {
                    pressFeedback()
                    setOnClickListener { action() }
                }
            }

        if (editing) {
            barHost.addView(barText("Select all") {
                tasks.filter { matchesFilter(it) && matchesQuery(it) }.forEach { selected.add(it.id) }
                rebuildList(); rebuildBar()
            })
            barHost.addView(context.spacer())
            barHost.addView(barText("Delete", selected.isNotEmpty()) {
                selected.toList().forEach(onDelete)
                selected.clear()
                editing = false
                rebuildList(); rebuildBar()
            })
            barHost.addView(context.spacer(28f.dp()))
            barHost.addView(barText("Done") {
                editing = false; selected.clear(); rebuildList(); rebuildBar()
            })
        } else {
            barHost.addView(barText("New") {
                JumpToast.show(context, "Start a download from a web page")
            })
            barHost.addView(context.spacer())
            barHost.addView(barText("Edit", tasks.isNotEmpty()) {
                editing = true; rebuildList(); rebuildBar()
            })
        }
    }

    // ------------------------------------------------------------ widgets

    private fun recordIcon(task: DownloadTask): FrameLayout {
        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(30f.dp(), 30f.dp())
        }
        fun showGlyph() {
            frame.removeAllViews()
            frame.addView(GlyphView(context, ReferenceIcons.paths.getValue("download"), palette.onSurface).apply {
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
                            override fun getOutline(view: View, outline: Outline) {
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

    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
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
