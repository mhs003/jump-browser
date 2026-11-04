package com.jump.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jump.browser.data.Bookmark
import com.jump.browser.ui.theme.JumpAccent
import com.jump.browser.data.HistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bookmarks / history / saved-pages tabs without the Compose framework.
 * A faithful port of RecordsScreen: tab labels in the title row, the
 * search pill, flat bookmark rows with circle favicons, history grouped
 * under day captions, edit mode with the select circle, the four bottom
 * bar variants and the two dialogs (add/edit bookmark, clear history).
 */
class RecordsViewHost(
    context: Context,
    private val palette: ViewPalette,
    initialTab: Int,
    private val onBack: () -> Unit,
    private val onOpen: (String) -> Unit,
    private val onDeleteBookmark: (Long) -> Unit,
    private val onEditBookmark: (Bookmark) -> Unit,
    private val onAddBookmark: (String, String) -> Unit,
    private val onDeleteHistory: (Long) -> Unit,
    private val onClearHistory: () -> Unit,
) : LinearLayout(context) {

    private fun Float.dp(): Int = context.dp(this)

    private var tab = initialTab.coerceIn(0, 2)
    private var query = ""
    private var editing = false
    private val selected = HashSet<Long>()

    private var bookmarks: List<Bookmark> = emptyList()
    private var history: List<HistoryEntry> = emptyList()

    private val labels = listOf("Bookmarks", "History", "Saved pages")
    private val tabsRow: LinearLayout
    private val searchPill: FrameLayout
    private val frame: FrameLayout
    private val listHost: LinearLayout
    private val emptyView: TextView
    private val listScroll: ScrollView
    private val barHost: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.background)

        // Title row doubles as the tab bar.
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 48f.dp(),
            )
        }
        row.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(56f.dp(), 56f.dp())
            isClickable = true
            isFocusable = true
            pressFeedback(oval = true)
            setOnClickListener { onBack() }
            addView(GlyphView(context, ReferenceIcons.paths.getValue("back"), palette.onSurface).apply {
                layoutParams = FrameLayout.LayoutParams(22f.dp(), 22f.dp(), Gravity.CENTER)
            })
        })
        tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setPadding(14f.dp(), 0, 0, 0)
        }
        row.addView(tabsRow)
        addView(row)
        addView(context.dividerLine(palette))
        searchPill = context.searchPill(palette) { query = it; rebuildList() }
        addView(searchPill)

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
        frame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        // Both children live in the frame; only visibility flips below.
        frame.addView(listScroll)
        frame.addView(emptyView)
        emptyView.visibility = GONE
        addView(frame)

        addView(context.dividerLine(palette))
        barHost = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48f.dp())
            val pad = 16f.dp()
            setPadding(pad, 0, pad, 0)
        }
        addView(barHost)

        rebuildTabs()
        rebuildBar()
        rebuildList()
    }

    /** Data push from the composing host; re-renders the visible tab. */
    fun submit(bookmarks: List<Bookmark>, history: List<HistoryEntry>) {
        this.bookmarks = bookmarks
        this.history = history
        android.util.Log.d("VH", "submit bm=${bookmarks.size} h=${history.size} tab=$tab editing=$editing")
        rebuildList()
        post {
            android.util.Log.d("VH", "geom: scroll=${listScroll.visibility} w=${listScroll.width} h=${listScroll.height} hostKids=${listHost.childCount} hostH=${listHost.height} frame=${frame.width}x${frame.height} empty=${emptyView.visibility}")
        }
        rebuildBar()
    }

    private fun resetMode() {
        editing = false
        selected.clear()
    }

    // ------------------------------------------------------------ building

    private fun rebuildTabs() {
        tabsRow.removeAllViews()
        labels.forEachIndexed { index, label ->
            tabsRow.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (tab == index) palette.onBackground else palette.onSurfaceVariant)
                maxLines = 1
                val vpad = 4f.dp()
                setPadding(0, vpad, 0, vpad)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) leftMargin = 24f.dp() }
                isClickable = true
                isFocusable = true
                pressFeedback()
                setOnClickListener {
                    tab = index
                    query = ""
                    (searchPill.getChildAt(0) as EditText).setText("")
                    resetMode()
                    rebuildTabs(); rebuildList(); rebuildBar()
                }
            })
        }
    }

    private fun filteredBookmarks() = if (query.isBlank()) bookmarks else bookmarks.filter {
        it.title.contains(query, true) || it.url.contains(query, true)
    }

    private fun filteredHistory() = if (query.isBlank()) history else history.filter {
        it.title.contains(query, true) || it.url.contains(query, true)
    }

    private fun hasRows(): Boolean = when (tab) {
        0 -> filteredBookmarks().isNotEmpty()
        1 -> filteredHistory().isNotEmpty()
        else -> false // the saved-pages tab is a placeholder, as in the original
    }

    fun rebuildList() {
        // Children live in the frame permanently; only visibility flips.
        // Add/remove of retained views races Compose's attachment callbacks.
        if (!hasRows()) {
            emptyView.visibility = VISIBLE
            listScroll.visibility = GONE
            listHost.removeAllViews()
            return
        }
        emptyView.visibility = GONE
        listScroll.visibility = VISIBLE
        listHost.removeAllViews()
        val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

        when (tab) {
            0 -> filteredBookmarks().forEach { bookmark ->
                listHost.addView(recordRow(
                    title = bookmark.title.ifBlank { bookmark.url },
                    subtitle = bookmark.url,
                    icon = { bookmarkIcon(bookmark) },
                    editing = editing,
                    selected = bookmark.id in selected,
                    onClick = {
                        if (editing) {
                            if (bookmark.id in selected) selected.remove(bookmark.id)
                            else selected.add(bookmark.id)
                            rebuildList(); rebuildBar()
                        } else onOpen(bookmark.url)
                    },
                    onLongClick = { onEditBookmark(bookmark) },
                ))
            }
            1 -> {
                val grouped = filteredHistory().groupBy {
                    Instant.ofEpochMilli(it.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                }.toSortedMap(compareByDescending { it })
                grouped.forEach { (day, entries) ->
                    listHost.addView(
                        context.text(day.format(dayFormat), 12f, palette.onSurfaceVariant).apply {
                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            lp.leftMargin = 16f.dp(); lp.topMargin = 16f.dp(); lp.bottomMargin = 4f.dp()
                            layoutParams = lp
                        },
                    )
                    entries.forEach { entry ->
                        listHost.addView(recordRow(
                            title = entry.title.ifBlank { entry.url },
                            subtitle = entry.url,
                            icon = { historyIcon() },
                            editing = editing,
                            selected = entry.id in selected,
                            onClick = {
                                if (editing) {
                                    if (entry.id in selected) selected.remove(entry.id)
                                    else selected.add(entry.id)
                                    rebuildList(); rebuildBar()
                                } else onOpen(entry.url)
                            },
                            onLongClick = null,
                        ))
                    }
                }
            }
            2 -> Unit
        }
        listHost.addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 12f.dp())
        })
    }

    private fun recordRow(
        title: String,
        subtitle: String,
        icon: () -> View,
        editing: Boolean,
        selected: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val hpad = 16f.dp(); val vpad = 12f.dp()
        setPadding(hpad, vpad, hpad, vpad)
        isClickable = true
        isFocusable = true
        isLongClickable = onLongClick != null
        pressFeedback()
        setOnClickListener { onClick() }
        onLongClick?.let { setOnLongClickListener { it(); true } }
        addView(icon())
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8f.dp(), 0, 0, 0)
            addView(context.text(title, 16f, palette.onBackground))
            addView(context.text(subtitle, 13f, palette.onSurfaceVariant))
        })
        if (editing) {
            addView(CircleCheckView(context, palette, selected).apply {
                layoutParams = LayoutParams(20f.dp(), 20f.dp()).apply { leftMargin = 8f.dp() }
            })
        }
    }

    /** Circle favicon: bitmap cover, else the first letter in accent. */
    private fun bookmarkIcon(bookmark: Bookmark): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(30f.dp(), 30f.dp())
            background = filledOval(0xFFE8EDFC.toInt())
        }
        val icon = ImageCache.loadBitmap(bookmark.iconPath, minEdge = 30)
        if (icon != null) {
            frame.addView(ImageView28(context, icon))
        } else {
            frame.addView(TextView(context).apply {
                text = bookmark.title.trim().take(1).uppercase().ifBlank { "•" }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0xFF6F8DE1.toInt())
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            })
        }
        return frame
    }

    private fun historyIcon(): View = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(30f.dp(), 30f.dp())
        gravity = Gravity.CENTER
        addView(GlyphView(context, ReferenceIcons.paths.getValue("clock"), palette.onSurface).apply {
            layoutParams = LayoutParams(22f.dp(), 22f.dp())
        })
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
                when (tab) {
                    0 -> filteredBookmarks().forEach { selected.add(it.id) }
                    1 -> filteredHistory().forEach { selected.add(it.id) }
                    else -> Unit
                }
                rebuildList(); rebuildBar()
            })
            barHost.addView(context.spacer())
            barHost.addView(barText("Delete", selected.isNotEmpty()) {
                when (tab) {
                    0 -> selected.toList().forEach(onDeleteBookmark)
                    1 -> selected.toList().forEach(onDeleteHistory)
                    else -> Unit
                }
                resetMode()
                rebuildList(); rebuildBar()
            })
            barHost.addView(context.spacer(28f.dp()))
            barHost.addView(barText("Done") {
                resetMode(); rebuildList(); rebuildBar()
            })
        } else {
            barHost.addView(barText(if (tab == 1) "Tabs" else "More") {
                when {
                    tab == 1 -> JumpToast.show(context, "Use the toolbar to switch tabs")
                    tab == 0 -> showAddDialog()
                    else -> JumpToast.show(context, "No saved pages")
                }
            })
            barHost.addView(context.spacer())
            if (tab == 1) {
                barHost.addView(barText(
                    "Delete all",
                    history.isNotEmpty(),
                ) {
                    context.alertDialog(
                        palette,
                        title = "Clear history?",
                        message = "All browsing history will be permanently deleted.",
                        confirm = "Clear",
                        dismiss = "Cancel",
                        onConfirm = { onClearHistory(); rebuildList(); rebuildBar() },
                    ).show()
                })
                barHost.addView(context.spacer(28f.dp()))
            }
            barHost.addView(barText("Edit", hasRows()) {
                editing = true; selected.clear(); rebuildList(); rebuildBar()
            })
        }
    }

    /** The add-bookmark card; editing existing ones stays on the browser side. */
    private fun showAddDialog() {
        var title = ""
        var url = ""
        context.alertDialog(
            palette,
            title = "Add bookmark",
            confirm = "Save",
            dismiss = "Cancel",
            body = { column ->
                column.addView(context.textField(palette, "Title") { title = it })
                column.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 16f.dp(),
                    )
                })
                column.addView(context.textField(palette, "URL", uri = true) { url = it })
            },
            onConfirm = {
                if (url.isNotBlank()) onAddBookmark(title.trim(), url.trim())
                rebuildList(); rebuildBar()
            },
        ).show()
    }
}



/** Circle-cropped favicon bitmap. */
private class ImageView28(context: Context, bitmap: Bitmap) : android.widget.ImageView(context) {
    init {
        setImageBitmap(bitmap)
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        layoutParams = LinearLayout.LayoutParams(30, 30)
    }
}
