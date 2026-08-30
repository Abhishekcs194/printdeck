package io.github.abhishekcs194.printdeck.print.ipp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every case here is a real string observed coming back from a Canon TR4600.
 * This is string surgery on another library's debug formatting, so it is exactly
 * the kind of thing that breaks quietly when that library is upgraded.
 */
class IppValueReadabilityTest {

    @Test
    fun `strips the type suffix from names and text`() {
        assertThat("\"Canon TR4600 series\" (text)".readable()).isEqualTo("Canon TR4600 series")
        assertThat("\"TR4600 series\" (name)".readable()).isEqualTo("TR4600 series")
        assertThat("\"Color\" (name)".readable()).isEqualTo("Color")
    }

    @Test
    fun `strips the numeric code from enums`() {
        assertThat("idle(3)".readable()).isEqualTo("idle")
        assertThat("draft(3)".readable()).isEqualTo("draft")
        assertThat("high(5)".readable()).isEqualTo("high")
    }

    @Test
    fun `keeps values that contain brackets meaningfully`() {
        // A resolution reads "600x600 dpi(3)" - the code goes, the units stay.
        assertThat("600x600 dpi(3)".readable()).isEqualTo("600x600 dpi")
    }

    @Test
    fun `leaves plain keywords untouched`() {
        listOf(
            "two-sided-long-edge",
            "application/octet-stream",
            "iso_a4_210x297mm",
            "com.canon.mtglossy",
            "marker-supply-low-warning",
        ).forEach { assertThat(it.readable()).isEqualTo(it) }
    }

    @Test
    fun `handles empty and odd input without throwing`() {
        assertThat("".readable()).isEmpty()
        assertThat("   ".readable()).isEmpty()
        assertThat("(3)".readable()).isEmpty()
    }
}
