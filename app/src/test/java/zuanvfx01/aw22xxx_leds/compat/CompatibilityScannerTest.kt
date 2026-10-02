package zuanvfx01.aw22xxx_leds.compat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import zuanvfx01.aw22xxx_leds.compat.CompatibilityScanner.CapabilityState
import zuanvfx01.aw22xxx_leds.compat.CompatibilityScanner.Failure

/**
 * Runs on a plain JVM: no phone, no android.jar. A temp folder plays the role of /sys,
 * and [FakeSysfsAccess] can pretend a path is blocked by SELinux (EACCES).
 */
class CompatibilityScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var paths: ScanPaths
    private lateinit var ledClass: File

    private val privApp = device("u:r:priv_app:s0:c512,c768")
    private val untrusted = device("u:r:untrusted_app:s0:c150,c256,c512,c768")

    @Before
    fun setUp() {
        root = tmp.newFolder("fakeroot")
        paths = ScanPaths.under(root.absolutePath)
        ledClass = File(paths.ledClass).apply { mkdirs() }
    }

    // ---- fixtures ------------------------------------------------------------------------

    private fun device(domain: String) = DeviceInfo(
        manufacturer = "TEST",
        model = "Unit-1",
        androidVersion = "16 (API 36)",
        kernel = "5.10.0-test",
        appDomain = domain,
        apkPath = "/system/priv-app/AwinicLeds/AwinicLeds.apk",
        systemFlag = true,
    )

    private val fullFiles = mapOf(
        "brightness" to "0\n",
        "max_brightness" to "255\n",
        "hwen" to "1\n",
        "rgb" to "0x0 0xff0000\n0x1 0x00ff00\n",
        "cfg" to "1\n",
        "effect" to "0x0\n",
        "frq" to "0x0\n",
        "reg" to "0x00 0x01\n",
        "imax" to "0x1\n",
        "trigger" to "[none] timer\n",
        "task0" to "0\n",
        "task1" to "0\n",
    )

    private fun ledNode(name: String, files: Map<String, String> = fullFiles): File {
        val dir = File(ledClass, name).apply { mkdirs() }
        files.forEach { (file, content) -> File(dir, file).writeText(content) }
        return dir
    }

    private fun scan(
        fake: SysfsAccess = FakeSysfsAccess(),
        device: DeviceInfo = privApp,
    ) = CompatibilityScanner.scan(access = fake, device = device, paths = paths)

    // ---- tests ---------------------------------------------------------------------------

    @Test
    fun fullNode_isCompatible() {
        ledNode("aw22xxx_led")

        val result = scan()

        assertTrue(result.compatible)
        assertFalse(result.partial)
        assertEquals("aw22xxx_led", result.selectedNode)
        assertEquals(Failure.NONE, result.failure)
        assertTrue(result.accessOk)
        listOf("brightness", "rgb", "cfg", "effect", "hwen").forEach {
            assertEquals("$it should be AVAILABLE", CapabilityState.AVAILABLE, result.state(it))
        }
    }

    @Test
    fun nodeMissingRgb_isNotCompatible_andReportsIncompleteInterface() {
        ledNode("aw22xxx_led", fullFiles - "rgb")

        val result = scan()

        assertFalse(result.compatible)
        assertTrue(result.driverFound) // known node name is driver evidence
        assertEquals("aw22xxx_led", result.selectedNode)
        assertEquals(CapabilityState.UNAVAILABLE, result.state("rgb"))
        assertEquals(Failure.INTERFACE_INCOMPLETE, result.failure)
    }

    @Test
    fun nodeDeniedByPermission_isAccessDenied_notMissingDriver() {
        val node = ledNode("aw22xxx_led")
        val fake = FakeSysfsAccess(denied = setOf(node.absolutePath))

        val result = scan(fake)

        assertFalse(result.compatible)
        assertEquals(Failure.ACCESS_DENIED, result.failure)
        assertFalse(result.accessOk)
        assertTrue(result.driverFound) // the node exists; it must not be reported as "no driver"
        assertEquals(CapabilityState.RESTRICTED, result.state("rgb"))
        assertTrue(result.details.any { it.contains("EACCES") })
    }

    @Test
    fun nodeDenied_asNormalApp_isNotPrivileged() {
        val node = ledNode("aw22xxx_led")
        val fake = FakeSysfsAccess(denied = setOf(node.absolutePath))

        val result = scan(fake, untrusted)

        assertFalse(result.compatible)
        assertEquals(Failure.NOT_PRIVILEGED, result.failure)
    }

    @Test
    fun emptyLedClass_isNoDriver() {
        val result = scan()

        assertFalse(result.compatible)
        assertFalse(result.driverFound)
        assertEquals(null, result.selectedNode)
        assertEquals(Failure.NO_DRIVER, result.failure)
    }

    @Test
    fun listingBlocked_stillFindsKnownNodeDirectly() {
        ledNode("aw22xxx_led")
        val fake = FakeSysfsAccess(listingDenied = setOf(ledClass.absolutePath))

        val result = scan(fake)

        assertTrue(result.compatible)
        assertEquals("aw22xxx_led", result.selectedNode)
        assertTrue(result.accessOk)
    }

    @Test
    fun oemNodeName_isMatchedByPattern() {
        ledNode("awinic_logo")

        val result = scan()

        assertTrue(result.compatible)
        assertEquals("awinic_logo", result.selectedNode)
    }

    @Test
    fun bestNode_winsWhenSeveralExist() {
        ledNode("aw22xxx", fullFiles - "rgb" - "cfg")
        ledNode("aw22xxx_led")

        val result = scan()

        assertEquals("aw22xxx_led", result.selectedNode)
        assertTrue(result.compatible)
    }
}

/** File-system fake over real temp files, with optional "blocked by SELinux" paths. */
private class FakeSysfsAccess(
    private val denied: Set<String> = emptySet(),
    private val listingDenied: Set<String> = emptySet(),
) : SysfsAccess {

    private fun isDenied(path: String) = denied.any { path == it || path.startsWith("$it/") }

    override fun stat(path: String): SysfsAccess.Stat = when {
        isDenied(path) -> SysfsAccess.Stat.RESTRICTED
        File(path).exists() -> SysfsAccess.Stat.AVAILABLE
        else -> SysfsAccess.Stat.MISSING
    }

    override fun errno(path: String): String = when (stat(path)) {
        SysfsAccess.Stat.AVAILABLE -> "OK"
        SysfsAccess.Stat.MISSING -> "ENOENT"
        SysfsAccess.Stat.RESTRICTED -> "EACCES"
    }

    override fun isDirectory(path: String): Boolean = !isDenied(path) && File(path).isDirectory

    override fun read(path: String): SysfsAccess.Read = when (stat(path)) {
        SysfsAccess.Stat.MISSING -> SysfsAccess.Read.Missing
        SysfsAccess.Stat.RESTRICTED -> SysfsAccess.Read.Denied("Permission denied")
        SysfsAccess.Stat.AVAILABLE ->
            if (File(path).isFile) SysfsAccess.Read.Ok(File(path).readText())
            else SysfsAccess.Read.Failed("EISDIR")
    }

    override fun list(path: String): List<String>? {
        if (isDenied(path) || path in listingDenied) return null
        return File(path).list()?.toList()
    }

    override fun canonicalName(path: String): SysfsAccess.Read = when (stat(path)) {
        SysfsAccess.Stat.MISSING -> SysfsAccess.Read.Missing
        SysfsAccess.Stat.RESTRICTED -> SysfsAccess.Read.Denied("Permission denied")
        SysfsAccess.Stat.AVAILABLE -> SysfsAccess.Read.Ok(File(path).canonicalFile.name)
    }
}
