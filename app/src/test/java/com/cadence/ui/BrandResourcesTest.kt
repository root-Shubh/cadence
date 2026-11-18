package com.cadence.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the brand green: the primary colour in [LightPalette] must match the
 * `brand_primary` value used by the launcher icon and splash resources.
 * Unit tests run with the module directory as the working directory.
 */
class BrandResourcesTest {

    private val colorsXml = File("src/main/res/values/colors.xml")

    private fun colorHex(name: String): String {
        assertTrue("colors.xml not found at ${colorsXml.absolutePath}", colorsXml.isFile)
        val match = Regex("""<color name="$name">#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})</color>""")
            .find(colorsXml.readText())
        assertTrue("colour '$name' missing from colors.xml", match != null)
        return match!!.groupValues[1].takeLast(6).uppercase()
    }

    @Test
    fun brandPrimaryMatchesPalette() {
        val paletteHex = "%06X".format(LightPalette.primary and 0xFFFFFFL)
        assertEquals(paletteHex, colorHex("brand_primary"))
    }

    @Test
    fun brandPrimaryIsThemeGreen() {
        assertEquals("1B7F6B", colorHex("brand_primary"))
    }
}
