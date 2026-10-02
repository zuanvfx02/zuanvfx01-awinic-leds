package zuanvfx01.aw22xxx_leds.compat

/**
 * Plain-text version of the compatibility screen, meant to be pasted into a chat/issue.
 * No android.* imports, so it can be unit tested on the JVM.
 */
fun CompatibilityScanner.Result.toReportText(appVersion: String = "?"): String = buildString {
    appendLine("AWINIC LED COMPATIBILITY REPORT")
    appendLine("===============================")
    appendLine("App version : $appVersion")
    appendLine("Device      : $manufacturer $model")
    appendLine("Android     : $androidVersion")
    appendLine("Kernel      : $kernel")
    appendLine()
    appendLine("Compatible  : $compatible${if (partial) " (partial)" else ""}")
    appendLine("Failure     : $failure")
    appendLine("Driver      : ${if (driverFound) "FOUND" else "NOT FOUND"} ($driverState)")
    appendLine("Access OK   : $accessOk")
    appendLine("Node        : ${selectedNode ?: "<none>"}")
    appendLine("LED nodes   : ${ledNodes.joinToString().ifBlank { "<none visible>" }}")
    appendLine("App domain  : ${appDomain.ifBlank { "unknown" }}")
    appendLine("APK         : ${apkPath.ifBlank { "unknown" }}")
    appendLine()
    appendLine("Capabilities:")
    capabilities.values.forEach { c ->
        append("  ${c.name.padEnd(15)} ${c.state}")
        c.reason?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
        appendLine()
    }
    if (driverNames.isNotEmpty()) {
        appendLine()
        appendLine("Driver evidence: ${driverNames.joinToString()}")
    }
    if (details.isNotEmpty()) {
        appendLine()
        appendLine("Details:")
        details.forEach { appendLine("  * $it") }
    }
}.trimEnd()
