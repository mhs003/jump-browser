package com.jump.browser.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * QR scanner on platform Views: camera preview through [QrPreview] on a
 * TextureView, the corner-frame overlay, the black bar with the back button,
 * the grant screen and the paste-a-link escape hatches.
 */
class QrScannerView(
    context: Context,
    private val onBack: () -> Unit,
    private val onResult: (String) -> Unit,
) : FrameLayout(context) {

    private val handled = java.util.concurrent.atomic.AtomicBoolean(false)
    private var preview: QrPreview? = null
    private var texture: TextureView? = null
    private var permissionRequested = false

    init {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        build(granted)
                    }

    private fun build(granted: Boolean) {
        removeAllViews()
        if (granted) {
            val cam = TextureView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                )
            }
            texture = cam
            cam.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    t: android.graphics.SurfaceTexture, w: Int, h: Int,
                ) {
                    preview?.attach(t)
                }

                override fun onSurfaceTextureSizeChanged(
                    t: android.graphics.SurfaceTexture, w: Int, h: Int,
                ) = Unit

                override fun onSurfaceTextureDestroyed(t: android.graphics.SurfaceTexture): Boolean {
                    preview?.closeCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(t: android.graphics.SurfaceTexture) = Unit
            }
            addView(cam)
            addView(QrFrameCornerView(context))
            addView(pasteLinkRow())
            // created after the surface exists: attach immediately
            val next = QrPreview(context) { value ->
                if (handled.compareAndSet(false, true)) onResult(value)
            }
            preview = next
            cam.surfaceTexture?.let { next.attach(it) }
        } else {
            addView(grantScreen())
        }
        addView(topBar())
    }

    private fun topBar(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
        )
        addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, statusBarInset()))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientScrim()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48f.cdp(context),
            )
            addView(chromeSlotWhite("back") { onBack() }.apply {
                layoutParams = LinearLayout.LayoutParams(56f.cdp(context), 48f.cdp(context))
            })
            addView(TextView(context).apply {
                text = "Scan QR code"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            })
        })
    }

    private fun gradientScrim(): android.graphics.drawable.Drawable =
        android.graphics.drawable.ColorDrawable(0x59000000)

    private fun chromeSlotWhite(glyph: String, onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { onClick() }
            addView(chromeIconView(context, glyph, Color.WHITE).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                )
            })
        }

    private fun grantScreen(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
        )
        val d = 28f.cdp(context)
        setPadding(d, d, d, d)
        addView(chromeIconView(context, "qrscan", Color.WHITE, sizeDp = 58))
        addView(View(context), LinearLayout.LayoutParams(1, 18f.cdp(context)))
        addView(TextView(context).apply {
            text = "Camera access is needed to scan QR codes"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
        })
        addView(View(context), LinearLayout.LayoutParams(1, 18f.cdp(context)))
        addView(TextView(context).apply {
            text = "Allow camera"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 40f.cdp(context),
            )
            layoutParams = lp
            background = filledRound(20f.cdp(context), palettePrimary())
            isClickable = true
            isFocusable = true
            pressFeedback(20f.cdp(context))
            setPadding(24f.cdp(context), 0, 24f.cdp(context), 0)
            setOnClickListener { requestCamera() }
        })
        addView(View(context), LinearLayout.LayoutParams(1, 8f.cdp(context)))
        addView(TextView(context).apply {
            text = "Paste a link instead"
            setTextColor(0xFF9AAEEF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            lp.setMargins(0, 8f.cdp(context), 0, 0)
            layoutParams = lp
            setPadding(16f.cdp(context), 12f.cdp(context), 16f.cdp(context), 12f.cdp(context))
            isClickable = true
            isFocusable = true
            pressFeedback()
            setOnClickListener { pasteLink(context) { onResult(it); onBack() } }
        })
    }

    private fun pasteLinkRow(): View = TextView(context).apply {
        text = "Paste a link instead"
        setTextColor(0xFFCCD6FF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        setPadding(16f.cdp(context), 12f.cdp(context), 16f.cdp(context), 12f.cdp(context))
        isClickable = true
        isFocusable = true
        pressFeedback()
        (layoutParams as LayoutParams).bottomMargin = 72f.cdp(context)
        setOnClickListener { pasteLink(context) { onResult(it); onBack() } }
    }

    private fun palettePrimary(): Int = 0xFF6F8DE1.toInt()

    private fun statusBarInset(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun requestCamera() {
        val activity = context as? Activity ?: return
        permissionRequested = true
        runCatching {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), 4104)
        }
    }

    /** Result hook from the Activity's permission callback. */
    fun onPermissionResult(requestCode: Int, granted: Boolean) {
        if (requestCode == 4104 && granted) build(true)
    }

    companion object {
        /** The Activity forwards its permission results to the live scanner. */
        @Volatile var live: QrScannerView? = null
            private set

        fun onPermissionsResult(requestCode: Int, results: IntArray) {
            live?.onPermissionResult(
                requestCode,
                results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        live = this
        if (!permissionRequested && context is Activity) {
            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (!ok) {
                permissionRequested = true
                requestCamera()
            }
        }
    }

    /** Corner frame: 240dp square, 3dp strokes, 40dp arms. */
    class QrFrameCornerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFB9C8FF.toInt()
        }

        init {
            layoutParams = LayoutParams(
                (resources.displayMetrics.density * 240 + 0.5f).toInt(),
                (resources.displayMetrics.density * 240 + 0.5f).toInt(),
                Gravity.CENTER,
            )
        }

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            paint.strokeWidth = 3f * d
            val len = 40f * d
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawLine(0f, 0f, len, 0f, paint)
            canvas.drawLine(0f, 0f, 0f, len, paint)
            canvas.drawLine(w, 0f, w - len, 0f, paint)
            canvas.drawLine(w, 0f, w, len, paint)
            canvas.drawLine(0f, h, len, h, paint)
            canvas.drawLine(0f, h, 0f, h - len, paint)
            canvas.drawLine(w, h, w - len, h, paint)
            canvas.drawLine(w, h, w, h - len, paint)
        }
    }
}
