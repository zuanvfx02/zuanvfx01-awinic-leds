package zuanvfx01.aw22xxx_leds.compat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompatibilityReportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun report_containsKeyFacts_forSupportMessages() {
        val root = tmp.newFolder("r")
        val paths = ScanPaths.under(root.absolutePath)
        val node = File(paths.ledClass, "aw22xxx_led").apply { mkdirs() }
        listOf("brightness", "rgb", "cfg", "effect").forEach { File(node, it).writeText("0") }

        val result = CompatibilityScanner.scan(
            access = object : SysfsAccess {
                override fun stat(path: String) =
                    if (File(path).exists()) SysfsAccess.Stat.AVAILABLE else SysfsAccess.Stat.MISSING
                override fun errno(path: String) = if (File(path).exists()) "OK" else "ENOENT"
                override fun isDirectory(path: String) = File(path).isDirectory
                override fun read(path: String) =
                    if (File(path).isFile) SysfsAccess.Read.Ok(File(path).readText()) else SysfsAccess.Read.Missing
                override fun list(path: String) = File(path).list()?.toList()
                override fun canonicalName(path: String) = SysfsAccess.Read.Missing
            },
            device = DeviceInfo("TEST", "Unit-1", "16 (API 36)", "5.10", "u:r:priv_app:s0", "/system/priv-app/X/X.apk", true),
            paths = paths,
        )

        val text = result.toReportText("9.9.9")
        assertTrue(text.contains("App version : 9.9.9"))
        assertTrue(text.contains("TEST Unit-1"))
        assertTrue(text.contains("Compatible  : true"))
        assertTrue(text.contains("Node        : aw22xxx_led"))
        assertTrue(text.contains("priv_app"))
        assertTrue(text.contains("rgb"))
    }
}
