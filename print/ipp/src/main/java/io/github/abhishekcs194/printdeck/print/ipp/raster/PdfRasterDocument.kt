package io.github.abhishekcs194.printdeck.print.ipp.raster

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.hp.jipp.pdl.ColorSpace
import com.hp.jipp.pdl.RenderableDocument
import com.hp.jipp.pdl.RenderablePage
import java.io.Closeable
import java.io.File

/**
 * Presents a PDF as something a raster encoder can consume.
 *
 * Necessary because most consumer inkjets accept no PDF at all — the Canon this
 * was built against takes only `image/pwg-raster`, `image/urf` and JPEG. Sending
 * a job directly therefore means rasterising on the device, which is the same
 * work a desktop print driver does, just without the desktop.
 *
 * Rendering is done a swath at a time rather than a page at a time. An A4 page
 * at 300dpi is about 8.7 million pixels — 35MB as a bitmap, and four times that
 * at 600dpi. Holding one of those per page would be enough to end the process on
 * a mid-range phone, so each horizontal band is rendered, converted and
 * discarded.
 */
class PdfRasterDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    override val dpi: Int,
) : RenderableDocument(), Closeable {

    // PdfRenderer permits exactly one open page at a time, so the currently open
    // one is tracked and swapped rather than opened per swath.
    private var openIndex = -1
    private var openPage: PdfRenderer.Page? = null

    override fun iterator(): Iterator<RenderablePage> =
        (0 until renderer.pageCount).asSequence().map { index -> pageAt(index) }.iterator()

    private fun pageAt(index: Int): RenderablePage {
        val page = openPage(index)
        val scale = dpi.toFloat() / POINTS_PER_INCH
        val widthPixels = (page.width * scale).toInt().coerceAtLeast(1)
        val heightPixels = (page.height * scale).toInt().coerceAtLeast(1)

        return object : RenderablePage(widthPixels, heightPixels) {
            override fun render(
                yOffset: Int,
                swathHeight: Int,
                colorSpace: ColorSpace,
                byteArray: ByteArray,
            ) {
                renderSwath(index, yOffset, swathHeight, widthPixels, scale, colorSpace, byteArray)
            }
        }
    }

    private fun openPage(index: Int): PdfRenderer.Page {
        openPage?.takeIf { openIndex == index }?.let { return it }
        openPage?.close()
        return renderer.openPage(index).also {
            openPage = it
            openIndex = index
        }
    }

    @Suppress("LongParameterList")
    private fun renderSwath(
        index: Int,
        yOffset: Int,
        swathHeight: Int,
        widthPixels: Int,
        scale: Float,
        colorSpace: ColorSpace,
        target: ByteArray,
    ) {
        val page = openPage(index)
        val bitmap = Bitmap.createBitmap(widthPixels, swathHeight, Bitmap.Config.ARGB_8888)
        try {
            // Paper is white. A PDF page has no background of its own, so without
            // this the untouched areas stay transparent and convert to black.
            bitmap.eraseColor(Color.WHITE)

            val transform = Matrix().apply {
                setScale(scale, scale)
                postTranslate(0f, -yOffset.toFloat())
            }
            page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            bitmap.toRaster(colorSpace, target)
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        openPage?.close()
        openPage = null
        renderer.close()
        descriptor.close()
    }

    companion object {
        private const val POINTS_PER_INCH = 72f

        fun open(file: File, dpi: Int): PdfRasterDocument {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfRasterDocument(descriptor, PdfRenderer(descriptor), dpi)
        }
    }
}

/**
 * Packs a bitmap into the byte layout the encoder expects.
 *
 * Alpha is dropped rather than blended: the bitmap was cleared to white before
 * rendering, so anything still transparent is paper.
 */
internal fun Bitmap.toRaster(colorSpace: ColorSpace, target: ByteArray) {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    var out = 0
    when (colorSpace) {
        ColorSpace.Grayscale -> for (pixel in pixels) {
            target[out++] = luminance(pixel)
        }

        ColorSpace.Rgb -> for (pixel in pixels) {
            target[out++] = (pixel shr RED_SHIFT).toByte()
            target[out++] = (pixel shr GREEN_SHIFT).toByte()
            target[out++] = pixel.toByte()
        }

        ColorSpace.Rgba -> for (pixel in pixels) {
            target[out++] = (pixel shr RED_SHIFT).toByte()
            target[out++] = (pixel shr GREEN_SHIFT).toByte()
            target[out++] = pixel.toByte()
            target[out++] = (pixel shr ALPHA_SHIFT).toByte()
        }
    }
}

/**
 * Rec. 601 luma. Averaging the channels instead is the common shortcut and it
 * makes reds and blues print far lighter than the eye expects, because the eye
 * is nothing like equally sensitive to the three.
 */
private fun luminance(pixel: Int): Byte {
    val red = (pixel shr RED_SHIFT) and BYTE_MASK
    val green = (pixel shr GREEN_SHIFT) and BYTE_MASK
    val blue = pixel and BYTE_MASK
    return ((red * RED_WEIGHT + green * GREEN_WEIGHT + blue * BLUE_WEIGHT) / WEIGHT_TOTAL).toByte()
}

private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE_MASK = 0xFF

// Integer weights, to keep the inner loop off floating point.
private const val RED_WEIGHT = 299
private const val GREEN_WEIGHT = 587
private const val BLUE_WEIGHT = 114
private const val WEIGHT_TOTAL = 1000
