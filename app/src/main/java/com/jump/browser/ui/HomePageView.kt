package com.jump.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jump.browser.data.Bookmark
import java.security.MessageDigest

/**
 * Homepage on platform views: logo, search outline pill, shortcut tiles in
 * rows of six, and the vertical pull-to-search gesture (drag down past 90%
 * of the 98-dp max and release to open search), all faithful to the
 * composable version.
 */
class HomePageView(
    context: Context,
    private val palette: ViewPalette,
    bookmarks: List<Bookmark>,
    private val backgroundPath: String = "",
    private val backgroundOpacity: Int = 100,
    private val onSearchClick: () -> Unit,
    private val onLogoClick: () -> Unit,
    private val onBookmarkClick: (Bookmark) -> Unit,
    private val logoPath: String,
    private val searchBarEnabled: Boolean,
    private val favoriteColorEnabled: Boolean,
) : FrameLayout(context) {

    private fun Float.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

    private val maxPull = 98f.dp().toFloat()
    private var pull = 0f
    private val content: LinearLayout

    init {
        setBackgroundColor(palette.background)
        ImageCache.loadBitmap(backgroundPath, minEdge = 720)?.let { backgroundBitmap ->
            addView(ImageView(context).apply {
                setImageBitmap(backgroundBitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = backgroundOpacity / 100f
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            })
        }
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
            )
        }
        addView(content)

        content.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 135f.dp())
        })

        // Logo: custom bitmap or the drawn ribbon mark.
        val logo = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(99f.dp(), 72f.dp())
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onLogoClick() }
        }
        val bm = ImageCache.loadBitmap(logoPath, minEdge = 320)
        if (bm != null) {
            logo.addView(ImageView(context).apply {
                setImageBitmap(bm)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 15f.dp().toFloat())
                    }
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            })
        } else {
            logo.addView(RibbonLogoView(context))
        }
        content.addView(logo)

        if (searchBarEnabled) {
            content.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(1, 22f.dp())
            })
            val pill = FrameLayout(context).apply {
                val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 46f.dp())
                lp.width = (context.resources.displayMetrics.widthPixels * 0.9f).toInt().coerceAtMost(540f.dp())
                layoutParams = lp
            }
            pill.addView(object : View(context) {
                private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f.dp().toFloat()
                    color = palette.outlineVariant
                }
                override fun onDraw(canvas: Canvas) {
                    canvas.drawRoundRect(
                        RectF(0f, 0f, width.toFloat(), height.toFloat()),
                        13f.dp().toFloat(), 13f.dp().toFloat(), stroke,
                    )
                }
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_UP) onSearchClick()
                    return true
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            content.addView(pill)
        }

        refresh(bookmarks)
    }

    private fun addTiles() = Unit

    fun refresh(bookmarks: List<Bookmark>) {
        // Rebuild tiles below the search pill.
        content.removeViews(4, content.childCount - 4)
        if (bookmarks.isEmpty()) return
        content.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 8f.dp())
        })
        val density = resources.displayMetrics.density
        bookmarks.take(18).chunked(6).forEach { rowItems ->
            content.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.width = (resources.displayMetrics.widthPixels * 0.9f).toInt().coerceAtMost((540f * density).toInt())
                layoutParams = lp
                rowItems.forEach { bookmark ->
                    addView(tile(bookmark))
                    addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(18f.dp(), 1)
                    })
                }
                repeat(6 - rowItems.size) {
                    addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(72f.dp(), 1)
                    })
                }
            })
        }
    }

    private fun tile(bookmark: Bookmark): View {
        val icon = ImageCache.loadBitmap(bookmark.iconPath, minEdge = 54)
        val tileColor = favoriteColor(bookmark.url)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(54f.dp(), ViewGroup.LayoutParams.WRAP_CONTENT)
            val vpad = 4f.dp()
            setPadding(0, vpad, 0, vpad)
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onBookmarkClick(bookmark) }
        }
        column.addView(FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(54f.dp(), 54f.dp())
            if (favoriteColorEnabled) {
                background = filledRound(15f.dp(), tileColor)
            }
            if (icon != null) {
                addView(ImageView(context).apply {
                    setImageBitmap(icon)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, 15f.dp().toFloat())
                        }
                    }
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                })
            } else if (bookmark.title.isNotBlank()) {
                addView(TextView(context).apply {
                    text = bookmark.title.trim().take(1).uppercase()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                })
            }
        })
        column.addView(TextView(context).apply {
            text = bookmark.title.ifBlank { bookmark.url }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(palette.onBackground)
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(54f.dp(), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        return column
    }

    private fun favoriteColor(value: String): Int {
        val digest = runCatching {
            MessageDigest.getInstance("MD5").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.getOrElse { value.hashCode().toString(16) }
        val r = digest.take(3).toIntOrNull(16)?.rem(128)?.plus(90) ?: 120
        val g = digest.substring(digest.length / 2 - 3, digest.length / 2).toIntOrNull(16)?.rem(128)?.plus(90) ?: 140
        val b = digest.takeLast(3).toIntOrNull(16)?.rem(128)?.plus(90) ?: 180
        return Color.rgb(r, g, b)
    }

    /** Pull-to-search: track vertical drags on the whole page. */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pull = 0f
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - (if (pull > 0f) lastY else event.y)
                if (dy > 0 || pull > 0f) {
                    pull = (pull + dy).coerceIn(0f, maxPull)
                    content.translationY = pull
                    content.alpha = 1f - (pull / maxPull) * 0.9f
                }
                if (pull > 0f) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pull > maxPull * 0.9f) {
                    pull = 0f
                    onSearchClick()
                } else if (pull > 0f) {
                    pull = 0f
                    content.animate().translationY(0f).alpha(1f).setDuration(140).start()
                }
            }
        }
        lastY = event.y
        return false
    }

    private var lastY = 0f

    /** The homepage ribbon mark, drawn from the 124x90 geometry. */
    class RibbonLogoView(context: Context) : View(context) {
        private val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00A5F6.toInt() }
        private val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3E00.toInt() }
        private val amber = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB700.toInt() }

        override fun onDraw(canvas: Canvas) {
            val sx = width / 124f
            val sy = height / 90f
            fun x(v: Float) = v * sx
            fun y(v: Float) = v * sy

            val bluePath = Path().apply {
                moveTo(x(21.7384f), y(26.6739f))
                cubicTo(x(21.9323f), y(25.0574f), x(23.0867f), y(23.7198f), x(24.6574f), y(23.2914f))
                lineTo(x(36.033f), y(20.189f))
                cubicTo(x(37.3053f), y(19.842f), x(38.5592f), y(20.7998f), x(38.5592f), y(22.1185f))
                lineTo(x(38.5592f), y(40f))
                lineTo(x(17.5f), y(62f))
                close()
            }
            val orangePath = Path().apply {
                moveTo(x(67f), y(7.5f))
                lineTo(x(70.5f), y(30.5f))
                lineTo(x(38.7886f), y(68.3644f))
                cubicTo(x(37.7137f), y(69.6478f), x(35.9532f), y(70.1262f), x(34.3766f), y(69.5631f))
                lineTo(x(18.8417f), y(64.0149f))
                cubicTo(x(17.4933f), y(63.5333f), x(17.0729f), y(61.8324f), x(18.0417f), y(60.7782f))
                close()
            }
            val amberPath = Path().apply {
                moveTo(x(66.7214f), y(8.2943f))
                cubicTo(x(66.61f), y(7.64321f), x(67.1493f), y(7.06493f), x(67.8066f), y(7.13066f))
                lineTo(x(85.1987f), y(8.86987f))
                cubicTo(x(85.9892f), y(8.94892f), x(86.658f), y(9.4884f), x(86.9026f), y(10.2443f))
                lineTo(x(107.813f), y(74.8778f))
                cubicTo(x(108.18f), y(76.0106f), x(107.482f), y(77.2107f), x(106.316f), y(77.4519f))
                lineTo(x(83.555f), y(82.161f))
                cubicTo(x(81.3385f), y(82.6196f), x(79.1834f), y(81.1494f), x(78.8018f), y(78.9184f))
                close()
            }
            canvas.drawPath(bluePath, blue)
            canvas.drawPath(orangePath, orange)
            canvas.drawPath(amberPath, amber)
        }
    }
}
