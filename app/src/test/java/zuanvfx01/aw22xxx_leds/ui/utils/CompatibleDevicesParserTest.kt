package zuanvfx01.aw22xxx_leds.ui.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompatibleDevicesParserTest {

    @Test
    fun validJson_isParsed() {
        val list = CompatibleDevicesParser.parse(
            """{"brands":[{"name":"ASUS","models":["ROG Phone 8 Pro"," ROG Phone 7 "]}]}"""
        )
        assertEquals(listOf(DeviceBrand("ASUS", listOf("ROG Phone 8 Pro", "ROG Phone 7"))), list)
    }

    @Test
    fun brandWithoutModels_orBlankName_isSkipped() {
        val list = CompatibleDevicesParser.parse(
            """{"brands":[{"name":"A","models":[]},{"name":" ","models":["x"]},{"name":"B","models":["y"]}]}"""
        )
        assertEquals(listOf(DeviceBrand("B", listOf("y"))), list)
    }

    @Test
    fun malformedOrEmpty_returnsNull() {
        assertNull(CompatibleDevicesParser.parse("not json"))
        assertNull(CompatibleDevicesParser.parse("""{"brands":[]}"""))
        assertNull(CompatibleDevicesParser.parse("""{"nope":1}"""))
    }

    /** Guards the file that ships in the APK and is fetched from GitHub. */
    @Test
    fun bundledAsset_isValid() {
        val file = File("src/main/assets/compatible_devices.json")
        assertNotNull("asset missing: ${file.absolutePath}", file.takeIf { it.isFile })
        val list = CompatibleDevicesParser.parse(file.readText())
        assertNotNull(list)
        assertEquals(5, list!!.size)
    }
}
