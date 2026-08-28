package io.github.abhishekcs194.printdeck.core.design

import androidx.annotation.DrawableRes
import io.github.abhishekcs194.printdeck.core.design.R

/**
 * The icon set, named once.
 *
 * Phosphor, bold weight, bundled as vector drawables rather than pulled from an
 * icon font — the app needs about twenty glyphs and a font would ship thousands.
 * Referencing them through here rather than by resource id keeps call sites
 * readable and makes a swap a single-file edit.
 */
object PrintDeckIcons {
    @DrawableRes val FilePdf = R.drawable.ph_file_pdf
    @DrawableRes val Images = R.drawable.ph_images
    @DrawableRes val FolderOpen = R.drawable.ph_folder_open
    @DrawableRes val Plus = R.drawable.ph_plus
    @DrawableRes val CaretLeft = R.drawable.ph_caret_left
    @DrawableRes val CaretRight = R.drawable.ph_caret_right
    @DrawableRes val CaretDown = R.drawable.ph_caret_down
    @DrawableRes val Printer = R.drawable.ph_printer
    @DrawableRes val Trash = R.drawable.ph_trash
    @DrawableRes val Rotate = R.drawable.ph_arrow_clockwise
    @DrawableRes val DotsThree = R.drawable.ph_dots_three
    @DrawableRes val Gear = R.drawable.ph_gear
    @DrawableRes val Check = R.drawable.ph_check
    @DrawableRes val ArrowLeft = R.drawable.ph_arrow_left
    @DrawableRes val NUp = R.drawable.ph_squares_four
    @DrawableRes val Booklet = R.drawable.ph_book_open
    @DrawableRes val Split = R.drawable.ph_scissors
    @DrawableRes val Poster = R.drawable.ph_grid_four
    @DrawableRes val Search = R.drawable.ph_magnifying_glass
    @DrawableRes val Warning = R.drawable.ph_warning
    @DrawableRes val Sliders = R.drawable.ph_sliders_horizontal
    @DrawableRes val Close = R.drawable.ph_x
}
