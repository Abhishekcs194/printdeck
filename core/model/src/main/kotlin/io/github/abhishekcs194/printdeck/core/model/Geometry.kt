package io.github.abhishekcs194.printdeck.core.model

/**
 * Everything in this file is expressed in **PDF user-space points** (1/72 inch),
 * with the origin at the bottom-left of the page and Y increasing upwards.
 *
 * That is PDF's native convention, not Android's. Converting once at the render
 * boundary is far less error-prone than flipping Y in the middle of imposition
 * maths, so the geometry stays in PDF space all the way through and only
 * :pdf:engine's preview path converts.
 */

private const val POINTS_PER_INCH = 72.0
private const val MM_PER_INCH = 25.4

fun Double.mmToPoints(): Double = this / MM_PER_INCH * POINTS_PER_INCH
fun Double.pointsToMm(): Double = this / POINTS_PER_INCH * MM_PER_INCH
fun Double.inchesToPoints(): Double = this * POINTS_PER_INCH

/** A width/height pair in points. */
data class SizePt(val width: Double, val height: Double) {
    init {
        require(width > 0 && height > 0) { "Size must be positive, was ${width}x$height" }
    }

    val isLandscape: Boolean get() = width > height
    val aspect: Double get() = width / height

    /** Same paper, turned 90°. */
    fun swapped(): SizePt = SizePt(height, width)

    /** This size rotated to the given orientation, preserving its dimensions. */
    fun oriented(landscape: Boolean): SizePt =
        if (landscape == isLandscape) this else swapped()
}

/** An axis-aligned rectangle in points. [x]/[y] are the bottom-left corner. */
data class RectPt(val x: Double, val y: Double, val width: Double, val height: Double) {
    val right: Double get() = x + width
    val top: Double get() = y + height
    val size: SizePt get() = SizePt(width, height)
    val centerX: Double get() = x + width / 2
    val centerY: Double get() = y + height / 2

    companion object {
        fun ofSize(size: SizePt): RectPt = RectPt(0.0, 0.0, size.width, size.height)
    }
}

/** Per-edge margins in points. */
data class Margins(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
) {
    companion object {
        val None = Margins()
        fun uniform(points: Double) = Margins(points, points, points, points)
        fun uniformMm(mm: Double) = uniform(mm.mmToPoints())
    }
}

/**
 * A 2-D affine transform in PDF matrix form `[a b c d e f]`, which maps
 * `(x, y) -> (a*x + c*y + e, b*x + d*y + f)`.
 *
 * This is exactly the argument list of PDF's `cm` operator, so a [AffineTransform]
 * hands straight to PdfBox with no conversion — which is the point of matching
 * the convention rather than inventing our own.
 */
data class AffineTransform(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
) {
    /**
     * Concatenation in application order: `a.then(b)` applies [this] first and
     * [next] second. Row-vector convention, matching PDF.
     */
    fun then(next: AffineTransform): AffineTransform = AffineTransform(
        a = a * next.a + b * next.c,
        b = a * next.b + b * next.d,
        c = c * next.a + d * next.c,
        d = c * next.b + d * next.d,
        e = e * next.a + f * next.c + next.e,
        f = e * next.b + f * next.d + next.f,
    )

    fun apply(x: Double, y: Double): Pair<Double, Double> =
        (a * x + c * y + e) to (b * x + d * y + f)

    companion object {
        val Identity = AffineTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

        fun translate(dx: Double, dy: Double) = AffineTransform(1.0, 0.0, 0.0, 1.0, dx, dy)

        fun scale(sx: Double, sy: Double = sx) = AffineTransform(sx, 0.0, 0.0, sy, 0.0, 0.0)

        /** Rotation about the origin. Only right angles are ever needed here. */
        fun rotate(quarterTurns: QuarterTurn): AffineTransform = when (quarterTurns) {
            QuarterTurn.NONE -> Identity
            QuarterTurn.CW_90 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, 0.0)
            QuarterTurn.HALF -> AffineTransform(-1.0, 0.0, 0.0, -1.0, 0.0, 0.0)
            QuarterTurn.CCW_90 -> AffineTransform(0.0, 1.0, -1.0, 0.0, 0.0, 0.0)
        }
    }
}

/** Right-angle rotation. Arbitrary angles are not useful for imposition. */
enum class QuarterTurn(val degrees: Int) {
    NONE(0),
    CW_90(90),
    HALF(180),
    CCW_90(270),
    ;

    /** True when this turn swaps a page's width and height. */
    val swapsAxes: Boolean get() = this == CW_90 || this == CCW_90
}
