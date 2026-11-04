package com.jump.browser.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jump.browser.browser.NetworkEntry
import com.jump.browser.browser.SniffedResource
import java.io.File

/**
 * The resource sniffer, network log and saved pages screens without the
 * Compose framework: title-row scaffolds, filter chips, live lists and
 * their text actions, all with JumpPressed feedback.
 */
class SnifferViewHost(
    context: Context,
    private val palette: ViewPalette,
    private val onBack: () -> Unit,
    private val onOpen: (String) -> Unit,
    private val onDownload: (String) -> Unit,
    private val onCopy: (String) -> Unit,
) : LinearLayout(context) {

    private fun Float.dp(): Int = context.dp(this)

    private val kinds = listOf("All", "Images", "Media", "Scripts", "Styles", "Other")
    private var filter = "All"
    private var resources: List<SniffedResource> = emptyList()

    private val chipsRow: LinearLayout
    private val listHost: LinearLayout
    private val frame: FrameLayout
    private val listScroll: ScrollView
    private val emptyView: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        addView(context.libraryTitleRow(palette, "Resource sniffer", onBack))
        addView(context.dividerLine(palette))

        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        chipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            lp.leftMargin = 12f.dp(); lp.rightMargin = 12f.dp()
            lp.topMargin = 8f.dp(); lp.bottomMargin = 8f.dp()
            layoutParams = lp
        }
        chipsScroll.addView(chipsRow)
        addView(chipsScroll)

        emptyView = TextView(context).apply {
            text = "No resources captured yet"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurfaceVariant)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
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
        frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        frame.addView(listScroll)
        frame.addView(emptyView)
        emptyView.visibility = GONE
        addView(frame)

        rebuildChips()
        rebuildList()
    }

    /** Data push from the composing host. */
    fun submit(resources: List<SniffedResource>) {
        this.resources = resources
        rebuildList()
    }

    private fun matches(resource: SniffedResource): Boolean {
        val m = resource.mime.lowercase()
        return when (filter) {
            "Images" -> m.startsWith("image/")
            "Media" -> m.startsWith("video/") || m.startsWith("audio/")
            "Scripts" -> m.contains("javascript")
            "Styles" -> m.contains("css")
            "Other" -> !(m.startsWith("image/") || m.startsWith("video/") || m.startsWith("audio/") ||
                m.contains("javascript") || m.contains("css"))
            else -> true
        }
    }

    private fun rebuildChips() {
        chipsRow.removeAllViews()
        kinds.forEachIndexed { index, kind ->
            val selected = kind == filter
            val chip = TextView(context).apply {
                text = kind
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(palette.onBackground)
                val hpad = 14f.dp(); val vpad = 6f.dp()
                setPadding(hpad, vpad, hpad, vpad)
                background = if (selected) {
                    filledRound(
                        16f.dp(),
                        (palette.onBackground and 0x00FFFFFF) or 0x14000000,
                    )
                } else {
                    strokedRound(
                        16f.dp(), 1f.dp(),
                        (palette.outlineVariant and 0x00FFFFFF) or 0x80000000.toInt(),
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) leftMargin = 8f.dp() }
                isClickable = true
                isFocusable = true
                pressFeedback(16f.dp())
                setOnClickListener { filter = kind; rebuildChips(); rebuildList() }
            }
            chipsRow.addView(chip)
        }
    }

    fun rebuildList() {
        val shown = resources.filter { matches(it) }
        if (shown.isEmpty()) {
            frame.removeAllViews(); frame.addView(emptyView)
            emptyView.visibility = VISIBLE
            listScroll.visibility = GONE
            return
        }
        emptyView.visibility = GONE
        listScroll.visibility = VISIBLE
        listHost.removeAllViews()
        shown.forEachIndexed { index, resource ->
            listHost.addView(resourceRow(resource))
            if (index < shown.size - 1) listHost.addView(context.dividerLine(palette))
        }
    }

    private fun resourceRow(resource: SniffedResource): View {
        fun action(label: String, action: () -> Unit): TextView = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.primary)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { action() }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val hpad = 16f.dp(); val vpad = 10f.dp()
            setPadding(hpad, vpad, hpad, vpad)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onOpen(resource.url) }
            addView(
                context.text(resource.url.substringAfterLast('/').ifBlank { resource.url }, 14f, palette.onBackground),
            )
            addView(TextView(context).apply {
                text = "${resource.mime.ifBlank { "unknown" }}  ·  ${resource.url}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(palette.onSurfaceVariant)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                lp.topMargin = 2f.dp()
                layoutParams = lp
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 6f.dp()
                layoutParams = lp
                val first = action("Open") { onOpen(resource.url) }
                first.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = 18f.dp() }
                addView(first)
                val second = action("Download") { onDownload(resource.url) }
                second.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = 18f.dp() }
                addView(second)
                addView(action("Copy link") { onCopy(resource.url) })
            })
        }
    }
}

class NetworkViewHost(
    context: Context,
    private val palette: ViewPalette,
    private val onBack: () -> Unit,
    private val onClear: () -> Unit,
    private val onOpen: (String) -> Unit,
) : LinearLayout(context) {

    private fun Float.dp(): Int = context.dp(this)

    private var entries: List<NetworkEntry> = emptyList()

    private val frame: FrameLayout
    private val listScroll: ScrollView
    private val listHost: LinearLayout
    private val emptyView: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        addView(context.libraryTitleRow(palette, "Network log", onBack) {
            addView(TextView(context).apply {
                text = "Clear"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(palette.primary)
                val hpad = 16f.dp(); val vpad = 12f.dp()
                setPadding(hpad, vpad, hpad, vpad)
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener { onClear() }
            })
        })
        addView(context.dividerLine(palette))
        emptyView = TextView(context).apply {
            text = "No requests recorded"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurfaceVariant)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
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
        frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        frame.addView(listScroll)
        frame.addView(emptyView)
        emptyView.visibility = GONE
        addView(frame)
        rebuild()
    }

    fun submit(entries: List<NetworkEntry>) {
        this.entries = entries
        rebuild()
    }

    private fun rebuild() {
        if (entries.isEmpty()) {
            frame.removeAllViews(); frame.addView(emptyView)
            emptyView.visibility = VISIBLE
            listScroll.visibility = GONE
            return
        }
        emptyView.visibility = GONE
        listScroll.visibility = VISIBLE
        listHost.removeAllViews()
        entries.forEachIndexed { index, entry ->
            listHost.addView(entryRow(entry))
            if (index < entries.size - 1) listHost.addView(context.dividerLine(palette))
        }
    }

    private fun entryRow(entry: NetworkEntry): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val hpad = 16f.dp(); val vpad = 9f.dp()
        setPadding(hpad, vpad, hpad, vpad)
        isClickable = true
        isFocusable = true
        pressFeedback()
        setOnClickListener { onOpen(entry.url) }

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(context.text(
                if (entry.blocked) "BLOCKED" else "${entry.status}", 12f,
                if (entry.blocked) palette.error else palette.primary,
            ))
            addView(context.spacer().apply {
                layoutParams = LinearLayout.LayoutParams(8f.dp(), 1)
            })
            addView(TextView(context).apply {
                text = entry.mime.ifBlank { "unknown" }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(palette.onSurfaceVariant)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(
                context.text(entry.time, 11f, palette.onSurfaceVariant),
            )
        })
        addView(TextView(context).apply {
            text = entry.url
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.onBackground)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = 2f.dp()
            layoutParams = lp
        })
        addView(
            context.text("${entry.durationMs} ms", 11f, palette.onSurfaceVariant),
        )
    }
}

class SavedViewHost(
    context: Context,
    private val palette: ViewPalette,
    private val onBack: () -> Unit,
    private val onOpen: (File) -> Unit,
    private val onDelete: (File) -> Unit,
) : LinearLayout(context) {

    private fun Float.dp(): Int = context.dp(this)

    private var pages: List<File> = emptyList()

    private val frame: FrameLayout
    private val listScroll: ScrollView
    private val listHost: LinearLayout
    private val emptyView: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)
        addView(context.libraryTitleRow(palette, "Saved pages", onBack))
        addView(context.dividerLine(palette))
        emptyView = TextView(context).apply {
            text = "No saved pages"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurfaceVariant)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
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
        frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        frame.addView(listScroll)
        frame.addView(emptyView)
        emptyView.visibility = GONE
        addView(frame)
        rebuild()
    }

    fun submit(pages: List<File>) {
        this.pages = pages
        rebuild()
    }

    private fun rebuild() {
        if (pages.isEmpty()) {
            frame.removeAllViews(); frame.addView(emptyView)
            emptyView.visibility = VISIBLE
            listScroll.visibility = GONE
            return
        }
        emptyView.visibility = GONE
        listScroll.visibility = VISIBLE
        listHost.removeAllViews()
        pages.forEachIndexed { index, file ->
            listHost.addView(row(file))
            if (index < pages.size - 1) listHost.addView(context.dividerLine(palette))
        }
    }

    private fun row(file: File): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(16f.dp(), 6f.dp(), 4f.dp(), 6f.dp())
        isClickable = true
        isFocusable = true
        pressFeedback()
        setOnClickListener { onOpen(file) }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(context.text(file.nameWithoutExtension.replace('_', ' '), 15f, palette.onBackground))
            addView(
                context.text("${file.length() / 1024} KB", 11f, palette.onSurfaceVariant),
            )
        })
        addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(56f.dp(), 56f.dp())
            isClickable = true
            isFocusable = true
            pressFeedback(oval = true)
            setOnClickListener { onDelete(file) }
            addView(GlyphView(context, ReferenceIcons.paths.getValue("cleardata"), palette.onSurface).apply {
                layoutParams = FrameLayout.LayoutParams(20f.dp(), 20f.dp(), Gravity.CENTER)
            })
        })
    }
}
