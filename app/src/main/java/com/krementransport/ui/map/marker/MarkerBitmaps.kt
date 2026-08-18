package com.krementransport.ui.map.marker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.core.graphics.withRotation
import com.krementransport.domain.model.TransitKind
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Markers are pre-rendered bitmaps, not composables.
 *
 * This is a performance contract, not a style choice: a busy selection puts ~150 vehicles on
 * screen, and `MarkerComposable` allocates and measures a composition per marker per frame. A
 * bitmap descriptor is uploaded once and reused by every marker that shares a key.
 *
 * The cache is bounded **by bytes**, not by entry count — a 40 dp marker is ~57 KB at xxhdpi, so
 * counting entries would let it grow into tens of megabytes on a dense map.
 *
 * Heading is quantised into [HeadingBuckets] steps. That is what makes the cache finite: the fin
 * has to be baked into the bitmap because the Maps SDK's own `rotation` would spin the route
 * number with it — the 1.4 app rotated the whole sprite and left half the numbers upside down.
 */
class MarkerBitmaps(private val density: Float) {

    private val cache = object : LruCache<String, BitmapDescriptor>(MaxBytes) {
        override fun sizeOf(key: String, value: BitmapDescriptor): Int = sizes[key] ?: DefaultEntryBytes
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: BitmapDescriptor,
            newValue: BitmapDescriptor?,
        ) {
            sizes.remove(key)
        }
    }
    private val sizes = HashMap<String, Int>()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun px(dp: Float): Float = dp * density

    // MARK: - Vehicles

    fun vehicle(
        badge: String,
        kind: TransitKind,
        tint: Int,
        headingDegrees: Double,
        offline: Boolean,
        labelled: Boolean,
    ): BitmapDescriptor {
        val bucket = headingBucket(headingDegrees)
        val key = "v|$badge|$tint|${kind.ordinal}|$bucket|$offline|$labelled"
        cache.get(key)?.let { return it }

        val bitmap = if (labelled) {
            drawLabelledVehicle(badge, kind, tint, bucket * HeadingStep, offline)
        } else {
            drawVehicleDot(tint, offline)
        }
        return store(key, bitmap)
    }

    private fun drawLabelledVehicle(
        badge: String,
        kind: TransitKind,
        tint: Int,
        headingDegrees: Float,
        offline: Boolean,
    ): Bitmap {
        val badgeHeight = px(BadgeHeightDp)
        textPaint.textSize = px(BadgeFontDp)
        val textWidth = textPaint.measureText(badge)
        val badgeWidth = max(textWidth + px(BadgeHPaddingDp) * 2, badgeHeight * 1.3f)

        // The fin has to clear the badge outline at *every* heading, and the badge is much wider
        // than it is tall — a fixed orbit radius would float above a "1" and sit inside a "15Б".
        // Orbit along an ellipse circumscribing the badge instead.
        val orbit = ellipseRadius(badgeWidth / 2f, badgeHeight / 2f, headingDegrees) +
            px(FinHeightDp) / 2f + px(FinGapDp)
        val finReach = orbit + px(FinHeightDp) / 2f
        val size = max(badgeWidth, finReach * 2) + px(2f)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val alpha = if (offline) 128 else 255
        val fill = if (offline) OfflineGrey else tint

        // Fin first, rotated about the centre, so the badge always paints over it upright.
        canvas.withRotation(headingDegrees, center, center) {
            val finHalf = px(FinWidthDp) / 2f
            val finTop = center - orbit - px(FinHeightDp) / 2f
            val finBottom = finTop + px(FinHeightDp)
            val fin = Path().apply {
                moveTo(center, finTop)
                lineTo(center + finHalf, finBottom)
                lineTo(center - finHalf, finBottom)
                close()
            }
            fillPaint.color = fill
            fillPaint.alpha = alpha
            drawPath(fin, fillPaint)
            strokePaint.color = Color.WHITE
            strokePaint.alpha = alpha
            strokePaint.strokeWidth = px(1f)
            drawPath(fin, strokePaint)
        }

        // Badge, upright.
        val rect = RectF(
            center - badgeWidth / 2f,
            center - badgeHeight / 2f,
            center + badgeWidth / 2f,
            center + badgeHeight / 2f,
        )
        val radius = when (kind) {
            TransitKind.Bus -> badgeHeight / 2f
            TransitKind.Trolleybus -> badgeHeight * 0.2f
        }
        fillPaint.color = fill
        fillPaint.alpha = alpha
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.alpha = (alpha * 0.35f).roundToInt()
        strokePaint.strokeWidth = px(0.5f)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        textPaint.color = contrastingLabel(fill)
        textPaint.alpha = alpha
        val baseline = center - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(badge, center, baseline, textPaint)

        return bitmap
    }

    private fun drawVehicleDot(tint: Int, offline: Boolean): Bitmap {
        val diameter = px(DotDiameterDp)
        val ring = px(1.5f)
        val size = diameter + ring * 2 + px(2f)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val alpha = if (offline) 128 else 255

        fillPaint.color = if (offline) OfflineGrey else tint
        fillPaint.alpha = alpha
        canvas.drawCircle(center, center, diameter / 2f, fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.alpha = alpha
        strokePaint.strokeWidth = ring
        canvas.drawCircle(center, center, diameter / 2f, strokePaint)
        return bitmap
    }

    // MARK: - Stops

    /**
     * Stops are intentionally quiet — a ring, not a pin — so live vehicles stay the foreground
     * layer and a zoomed-in map does not turn into a wall of pins.
     */
    fun stop(fillColor: Int, ringColor: Int, selected: Boolean): BitmapDescriptor {
        val key = "s|$fillColor|$ringColor|$selected"
        cache.get(key)?.let { return it }

        val scale = if (selected) 1.55f else 1f
        val diameter = px(StopDiameterDp) * scale
        val ring = px(StopRingDp) * scale
        val size = diameter + ring + px(2f)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        fillPaint.color = fillColor
        fillPaint.alpha = 255
        canvas.drawCircle(center, center, diameter / 2f, fillPaint)
        strokePaint.color = ringColor
        strokePaint.alpha = 255
        strokePaint.strokeWidth = ring
        canvas.drawCircle(center, center, diameter / 2f - ring / 2f, strokePaint)

        return store(key, bitmap)
    }

    // MARK: - Plumbing

    private fun store(key: String, bitmap: Bitmap): BitmapDescriptor {
        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        sizes[key] = bitmap.byteCount
        cache.put(key, descriptor)
        return descriptor
    }

    private fun createBitmap(width: Float, height: Float): Bitmap = Bitmap.createBitmap(
        max(1, width.roundToInt()),
        max(1, height.roundToInt()),
        Bitmap.Config.ARGB_8888,
    )

    private fun headingBucket(degrees: Double): Int {
        val normalised = ((degrees % 360.0) + 360.0) % 360.0
        return ((normalised / HeadingStep).roundToInt()) % HeadingBuckets
    }

    private companion object {
        /** 22.5° steps. Finer buckets are not visible on a 11 dp fin but do multiply the cache. */
        const val HeadingBuckets = 16
        const val HeadingStep = 360f / HeadingBuckets

        const val BadgeHeightDp = 22f
        const val BadgeFontDp = 11f
        const val BadgeHPaddingDp = 6f
        const val FinWidthDp = 11f
        const val FinHeightDp = 8f
        /** Breathing room between the badge outline and the base of the fin. */
        const val FinGapDp = 1.5f
        const val DotDiameterDp = 11f
        const val StopDiameterDp = 13f
        const val StopRingDp = 3.5f

        const val MaxBytes = 8 * 1024 * 1024
        const val DefaultEntryBytes = 64 * 1024
        const val OfflineGrey = 0xFF8E8E93.toInt()

        /**
         * Distance from the centre of an axis-aligned ellipse to its edge along [degrees],
         * measured clockwise from straight up — the same frame the canvas rotation uses.
         */
        fun ellipseRadius(semiWidth: Float, semiHeight: Float, degrees: Float): Float {
            val radians = Math.toRadians(degrees.toDouble())
            val dx = sin(radians) / semiWidth
            val dy = cos(radians) / semiHeight
            return (1.0 / kotlin.math.sqrt(dx * dx + dy * dy)).toFloat()
        }

        fun contrastingLabel(color: Int): Int {
            val luminance = 0.299f * Color.red(color) + 0.587f * Color.green(color) +
                0.114f * Color.blue(color)
            return if (luminance > 0.62f * 255f) Color.BLACK else Color.WHITE
        }
    }
}
