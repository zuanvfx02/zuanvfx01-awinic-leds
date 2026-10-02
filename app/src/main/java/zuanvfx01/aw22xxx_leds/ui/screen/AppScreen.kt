package zuanvfx01.aw22xxx_leds.ui.screen

import zuanvfx01.aw22xxx_leds.ui.utils.appText
import zuanvfx01.aw22xxx_leds.ui.utils.appString
import zuanvfx01.aw22xxx_leds.ui.utils.GitHubUpdateDialog
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import zuanvfx01.aw22xxx_leds.Application
import zuanvfx01.aw22xxx_leds.bridge.SysFsBridge
import zuanvfx01.aw22xxx_leds.compat.CompatibilityScanner
import zuanvfx01.aw22xxx_leds.ui.model.LedsViewModel
import zuanvfx01.aw22xxx_leds.ui.navigation.AppDestination
import zuanvfx01.aw22xxx_leds.ui.theme.AppTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import zuanvfx01.aw22xxx_leds.ui.glass.AdaptiveFloatingNavBar
import zuanvfx01.aw22xxx_leds.ui.glass.BlurEdge
import zuanvfx01.aw22xxx_leds.ui.glass.ProgressiveBlurLayer
import zuanvfx01.aw22xxx_leds.ui.glass.GlassDialogHost
import zuanvfx01.aw22xxx_leds.ui.glass.GlassDialogHostState
import zuanvfx01.aw22xxx_leds.ui.glass.GlassSupport
import zuanvfx01.aw22xxx_leds.ui.glass.LocalGlassDialogHost
import zuanvfx01.aw22xxx_leds.ui.widgets.FloatingNavBarDefaults
import zuanvfx01.aw22xxx_leds.ui.widgets.FloatingNavItem
import zuanvfx01.aw22xxx_leds.ui.widgets.LocalFloatingBarPadding

private enum class AppInnerRoute { Splash, Intro, Compatibility, Main }

private object CompatibilityCache {
    private const val PREFS = "compatibility_cache"
    private const val KEY_SCHEMA = "schema"
    private const val SCHEMA_VERSION = 3
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_COMPATIBLE = "compatible"
    private const val KEY_ACCESS = "access"
    private const val KEY_NODE = "node"
    private const val KEY_NODES = "nodes"
    private const val KEY_DRIVERS = "drivers"
    private const val KEY_MATCHED = "matched"
    private const val KEY_DETAILS = "details"
    private const val KEY_INTERFACES = "interfaces"
    private const val KEY_CAPABILITIES = "capabilities"
    private const val KEY_PARTIAL = "partial"
    private const val KEY_DRIVER_FOUND = "driver_found"
    private const val KEY_DRIVER_STATE = "driver_state"
    private const val SEP = "\u001F"

    private fun prefs(context: android.content.Context) = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun load(context: android.content.Context): CompatibilityScanner.Result? {
        val p = prefs(context)
        if (p.getInt(KEY_SCHEMA, 0) != SCHEMA_VERSION) return null
        if (p.getString(KEY_FINGERPRINT, null) != CompatibilityScanner.deviceFingerprint()) return null
        val node = p.getString(KEY_NODE, null)?.takeIf { it.isNotBlank() }
        val interfaces = p.getString(KEY_INTERFACES, "").orEmpty().split(SEP).filter { it.isNotBlank() }
            .associate { entry ->
                val parts = entry.split('=', limit = 2)
                parts[0] to (parts.getOrNull(1) == "1")
            }
        val capabilities = p.getString(KEY_CAPABILITIES, "").orEmpty().split(SEP).filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 3)
                if (parts.size < 2) null else {
                    val state = runCatching { CompatibilityScanner.CapabilityState.valueOf(parts[1]) }.getOrNull()
                        ?: CompatibilityScanner.CapabilityState.UNAVAILABLE
                    parts[0] to CompatibilityScanner.Capability(parts[0], state, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
                }
            }.toMap()
        return CompatibilityScanner.Result(
            compatible = p.getBoolean(KEY_COMPATIBLE, false),
            partial = p.getBoolean(KEY_PARTIAL, false),
            driverFound = p.getBoolean(KEY_DRIVER_FOUND, false),
            driverState = runCatching { CompatibilityScanner.CapabilityState.valueOf(p.getString(KEY_DRIVER_STATE, CompatibilityScanner.CapabilityState.UNAVAILABLE.name)!!) }.getOrDefault(CompatibilityScanner.CapabilityState.UNAVAILABLE),
            accessOk = p.getBoolean(KEY_ACCESS, false),
            ledNodes = p.getString(KEY_NODES, "").orEmpty().split(SEP).filter { it.isNotBlank() },
            driverNames = p.getString(KEY_DRIVERS, "").orEmpty().split(SEP).filter { it.isNotBlank() },
            matchedNodes = p.getString(KEY_MATCHED, "").orEmpty().split(SEP).filter { it.isNotBlank() },
            selectedNode = node,
            details = p.getString(KEY_DETAILS, "").orEmpty().split(SEP).filter { it.isNotBlank() },
            interfaceStatus = interfaces,
            capabilities = capabilities.ifEmpty {
                interfaces.mapValues { (name, ok) -> CompatibilityScanner.Capability(name, if (ok) CompatibilityScanner.CapabilityState.AVAILABLE else CompatibilityScanner.CapabilityState.UNAVAILABLE) }
            }
        )
    }

    fun save(context: android.content.Context, result: CompatibilityScanner.Result) {
        prefs(context).edit()
            .putInt(KEY_SCHEMA, SCHEMA_VERSION)
            .putString(KEY_FINGERPRINT, CompatibilityScanner.deviceFingerprint())
            .putBoolean(KEY_COMPATIBLE, result.compatible)
            .putBoolean(KEY_PARTIAL, result.partial)
            .putBoolean(KEY_DRIVER_FOUND, result.driverFound)
            .putString(KEY_DRIVER_STATE, result.driverState.name)
            .putBoolean(KEY_ACCESS, result.accessOk)
            .putString(KEY_NODE, result.selectedNode.orEmpty())
            .putString(KEY_NODES, result.ledNodes.joinToString(SEP))
            .putString(KEY_DRIVERS, result.driverNames.joinToString(SEP))
            .putString(KEY_MATCHED, result.matchedNodes.joinToString(SEP))
            .putString(KEY_DETAILS, result.details.joinToString(SEP))
            .putString(KEY_INTERFACES, result.interfaceStatus.entries.joinToString(SEP) { "${it.key}=${if (it.value) 1 else 0}" })
            .putString(KEY_CAPABILITIES, result.capabilities.values.joinToString(SEP) { "${it.name}=${it.state.name}=${it.path.orEmpty()}" })
            .apply()
    }

    fun clear(context: android.content.Context) = prefs(context).edit().clear().apply()
}

@Composable
private fun AppRouter() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf<AppInnerRoute?>(null) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CompatibilityScanner.Result?>(null) }

    fun runCheck(force: Boolean = false) {
        scope.launch {
            if (!force) {
                // Only a PREVIOUS SUCCESS may be reused, and only if it still works right now.
                // A cached failure must never stick: the module may have been (re)flashed or the
                // device rebooted since, and the old code froze the "driver not found" screen forever.
                val cached = CompatibilityCache.load(context)
                if (cached != null && cached.compatible && cached.selectedNode != null) {
                    val stillReadable = withContext(Dispatchers.IO) {
                        runCatching {
                            val f = java.io.File("/sys/class/leds/${cached.selectedNode}/brightness")
                            f.canRead() && f.readText().isNotBlank()
                        }.getOrDefault(false)
                    }
                    if (stillReadable) {
                        result = cached
                        SysFsBridge.configureLedDirectory("/sys/class/leds/${cached.selectedNode}")
                        SysFsBridge.configureCapabilities(cached.capabilities)
                        route = AppInnerRoute.Main
                        return@launch
                    }
                }
            }
            checking = true
            route = AppInnerRoute.Compatibility
            val scanned = withContext(Dispatchers.IO) { CompatibilityScanner.scan() }
            result = scanned
            if (scanned.compatible) CompatibilityCache.save(context, scanned) else CompatibilityCache.clear(context)
            scanned.selectedNode?.let { SysFsBridge.configureLedDirectory("/sys/class/leds/$it") }
            SysFsBridge.configureCapabilities(scanned.capabilities)
            checking = false
            if (scanned.compatible) {
                route = AppInnerRoute.Main
            }
        }
    }

    LaunchedEffect(Unit) {
        // A deliberately short splash gives the app a proper launch identity without
        // delaying the compatibility check or the first-run walkthrough unnecessarily.
        route = AppInnerRoute.Splash
        delay(850)
        val settings = Application.INSTANCE.settings.data.first()
        if (settings.completedIntro) runCheck() else route = AppInnerRoute.Intro
    }

    Surface(Modifier.fillMaxSize()) {
        when (route) {
            AppInnerRoute.Splash -> SplashScreen()
            AppInnerRoute.Intro -> IntroScreen(
                onComplete = {
                    scope.launch {
                        Application.INSTANCE.settings.updateData { it.toBuilder().setCompletedIntro(true).build() }
                        runCheck(force = true)
                    }
                }
            )
            AppInnerRoute.Compatibility -> CompatibilityCheckScreen(
                checking = checking,
                result = result,
                onRetry = { runCheck(force = true) },
                onContinue = {
                    result?.selectedNode?.let { SysFsBridge.configureLedDirectory("/sys/class/leds/$it") }
                    result?.capabilities?.let { SysFsBridge.configureCapabilities(it) }
                    route = AppInnerRoute.Main
                }
            )
            AppInnerRoute.Main -> MainShell()
            null -> CompatibilityCheckScreen(
                checking = true,
                result = null,
                onRetry = { runCheck(force = true) },
                onContinue = {}
            )
        }
    }
}

@Composable
private fun MainShell() {
    val tabsNav = rememberNavController()
    val backStack by tabsNav.currentBackStackEntryAsState()
    val currentDestination = AppDestination.fromRoute(backStack?.destination?.route)
    val currentRoute = backStack?.destination?.route
    val ledsViewModel: LedsViewModel = viewModel()

    var tutorialVisible by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableStateOf(0) }
    var settingsTarget by remember { mutableStateOf<Rect?>(null) }
    var musicTarget by remember { mutableStateOf<Rect?>(null) }
    var musicEnableTarget by remember { mutableStateOf<Rect?>(null) }
    var musicEnableAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Intro and interactive tutorial are separate experiences. The intro can be
        // completed while the first-use coach still remains available.
        val settings = Application.INSTANCE.settings.data.first()
        tutorialVisible = !settings.completedTutorial
    }

    // The tutorial is intentionally state-driven: tapping the real Settings / Music LED
    // controls advances it. The overlay never replaces the actual app controls.
    LaunchedEffect(currentDestination, currentRoute, tutorialVisible) {
        if (!tutorialVisible) return@LaunchedEffect
        when {
            tutorialStep == 1 && currentDestination == AppDestination.Settings -> tutorialStep = 2
            tutorialStep == 2 && currentRoute == "settings_music" -> tutorialStep = 3
        }
    }

    fun finishTutorial() {
        // Completing the Music LED tutorial must not change the current nested
        // destination. Enabling Music LED should keep the user on this screen.
        tutorialVisible = false
        scope.launch {
            Application.INSTANCE.settings.updateData {
                it.toBuilder().setCompletedTutorial(true).build()
            }
        }
    }

    // Keep the last real top-level tab selected while a nested destination is being
    // pushed/popped. NavController can briefly expose a null/non-top-level destination
    // during the transaction; deriving selectedIndex directly from that transient state
    // makes the bar go selected -> unselected -> selected (visible as a double flicker).
    var selectedTopLevel by remember { mutableStateOf(AppDestination.Home) }
    LaunchedEffect(currentDestination) {
        currentDestination?.let { selectedTopLevel = it }
    }

    // Detail screens (settings_music, settings_timer, ...) belong to the Settings tab.
    // The stable selectedTopLevel state intentionally survives those nested transitions.
    val highlightedDestination = selectedTopLevel

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = if (GlassSupport.blur) FloatingNavBarDefaults.GlassHeight else FloatingNavBarDefaults.Height
    val barPadding = barHeight + FloatingNavBarDefaults.BottomMargin +
        FloatingNavBarDefaults.ContentGap + navInset
    val navItems = AppDestination.entries.map { destination ->
        FloatingNavItem(
            label = appString(destination.label),
            selectedIcon = destination.selectedIcon,
            unselectedIcon = destination.unselectedIcon,
            modifier = if (destination == AppDestination.Settings) {
                Modifier.onGloballyPositioned { settingsTarget = it.boundsInRoot() }
            } else Modifier,
        )
    }

    // Android 12+: the NavHost content is recorded as a backdrop so the nav bar can be glass.
    // Android 11-: null -> stock UI, no extra layer and no blur.
    //  - contentBackdrop : the NavHost only. The bottom progressive blur samples THIS one.
    //  - glassBackdrop   : NavHost + the bottom blur strip. The nav bar and dialogs refract THIS one,
    //                      so they see the same blurred strip the user sees.
    // Two layers are needed because a layer cannot sample itself.
    val glassBackdrop = if (GlassSupport.blur) rememberLayerBackdrop() else null
    val contentBackdrop = if (GlassSupport.blur) rememberLayerBackdrop() else null
    val glassBaseColor = MaterialTheme.colorScheme.background

    // Glass dialogs (ui/glass/GlassDialog.kt) refract this same content.
    val dialogHost = LocalGlassDialogHost.current
    DisposableEffect(dialogHost, glassBackdrop) {
        dialogHost?.backdrop = glassBackdrop
        onDispose { if (dialogHost?.backdrop === glassBackdrop) dialogHost?.backdrop = null }
    }

    Box(Modifier.fillMaxSize()) {
        // Everything the glass nav bar / dialogs should "see" is recorded into glassBackdrop.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)
        ) {
            CompositionLocalProvider(LocalFloatingBarPadding provides barPadding) {
                NavHost(
                    tabsNav,
                    startDestination = AppDestination.Home.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (contentBackdrop != null) {
                                // layerBackdrop first (outer) so the base color below is recorded too.
                                Modifier.layerBackdrop(contentBackdrop).background(glassBaseColor)
                            } else Modifier
                        ),
                ) {
                    composable(AppDestination.Home.route) {
                        HomeScreen(
                            viewModel = ledsViewModel,
                            onNavigate = { route ->
                                tabsNav.navigate(route) {
                                    // Tab destinations keep the bottom-bar behavior; detail screens
                                    // (settings_music, settings_timer, ...) just push onto Home.
                                    if (AppDestination.fromRoute(route) != null) {
                                        popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                    composable(AppDestination.Settings.route) {
                        SettingsScreen(
                            viewModel = ledsViewModel,
                            onNavigateNotification = { tabsNav.navigate("settings_notification") },
                            onNavigateCharger = { tabsNav.navigate("settings_charger") },
                            onNavigateTimer = { tabsNav.navigate("settings_timer") },
                            onNavigateMusic = { tabsNav.navigate("settings_music") },
                            onMusicTutorialTarget = { musicTarget = it }
                        )
                    }
                    composable(AppDestination.Info.route) { InfoScreen() }
                    composable("settings_notification") { NotificationSettingsScreen(ledsViewModel) { tabsNav.popBackStack() } }
                    composable("settings_timer") { TimerSettingsScreen(ledsViewModel) { tabsNav.popBackStack() } }
                    composable("settings_charger") { ChargerSettingsScreen(ledsViewModel) { tabsNav.popBackStack() } }
                    composable("settings_music") {
                        MusicLedScreen(
                            onBack = { tabsNav.popBackStack() },
                            onTutorialEnableTarget = { musicEnableTarget = it },
                            onTutorialEnableAction = { musicEnableAction = it },
                            onTutorialEnabled = { finishTutorial() }
                        )
                    }
                }
            }


            // Progressive blur behind the floating bottom navigation (mirror of the top app bar).
            if (contentBackdrop != null) {
                ProgressiveBlurLayer(
                    backdrop = contentBackdrop,
                    edge = BlurEdge.Bottom,
                    tint = glassBaseColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(barPadding + 28.dp),
                )
            }
        }

        AdaptiveFloatingNavBar(
            backdrop = glassBackdrop,
            items = navItems,
            selectedIndex = AppDestination.entries.indexOf(highlightedDestination),
            onSelect = { index ->
                tabsNav.navigate(AppDestination.entries[index].route) {
                    popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = FloatingNavBarDefaults.HorizontalMargin,
                    end = FloatingNavBarDefaults.HorizontalMargin,
                    bottom = FloatingNavBarDefaults.BottomMargin,
                ),
        )

        if (tutorialVisible) {
            InteractiveTutorial(
                step = tutorialStep,
                currentDestination = currentDestination,
                currentRoute = currentRoute,
                bottomInset = barPadding,
                settingsTarget = settingsTarget,
                musicTarget = musicTarget,
                musicEnableTarget = musicEnableTarget,
                onNext = {
                    when (tutorialStep) {
                        0 -> tutorialStep = 1
                        1 -> Unit // waits for the real Settings navigation item
                        2 -> Unit // waits for the real Music LED row
                        3 -> finishTutorial()
                    }
                },
                onSkip = ::finishTutorial,
                onSettingsClick = {
                    tabsNav.navigate(AppDestination.Settings.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onMusicClick = {
                    tabsNav.navigate("settings_music")
                },
                onEnableMusicClick = {
                    musicEnableAction?.invoke()
                }
            )
        }
    }
}

/**
 * First-use coach marks. This is deliberately an overlay rather than a separate tutorial screen:
 * users interact with the real controls and the coach advances when the expected control is used.
 */
@Composable
private fun InteractiveTutorial(
    step: Int,
    currentDestination: AppDestination?,
    currentRoute: String?,
    bottomInset: androidx.compose.ui.unit.Dp,
    settingsTarget: Rect?,
    musicTarget: Rect?,
    musicEnableTarget: Rect?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onSettingsClick: () -> Unit,
    onMusicClick: () -> Unit,
    onEnableMusicClick: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "tutorialPulse")
    val ringAlpha by pulse.animateFloat(
        0.28f, 0.90f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    val target = when (step) {
        1 -> settingsTarget
        2 -> musicTarget
        3 -> musicEnableTarget
        else -> null
    }

    val (title, body, hint, icon) = when (step) {
        0 -> Quad(
            appText("quick_tour", "Quick tour"),
            appText("quick_tour_description", "Let's take a few seconds to show you the most useful LED controls. You can skip this anytime."),
            appText("tutorial_three_steps", "3 simple steps"),
            Icons.Rounded.AutoAwesome
        )
        1 -> Quad(
            appText("open_settings", "Open Settings"),
            appText("open_settings_description", "This is where you control LED effects, automation and Music LED."),
            appText("tap_settings", "Tap Settings"),
            Icons.Rounded.Settings
        )
        2 -> Quad(
            appText("open_music_led", "Open Music LED"),
            appText("open_music_led_description", "Music LED reacts to sound and beats. Open it to see the live controls."),
            appText("tap_music_led", "Tap Music LED"),
            Icons.Rounded.AutoAwesome
        )
        else -> Quad(
            appText("enable_music_led", "Enable Music LED"),
            appText("enable_music_led_description", "Turn this switch on to start the feature. Android may ask for microphone permission."),
            appText("tap_enable_music_led", "Tap Enable Music LED"),
            Icons.Rounded.Check
        )
    }

    Box(Modifier.fillMaxSize()) {
        // The overlay itself does not consume touch events. The real app controls remain
        // clickable underneath it, which makes this a true coach mark rather than a fake demo.
        if (target != null) {
            // The coach target is a small interactive hit area. A full-screen transparent
            // overlay would swallow taps on the real UI, so only the highlighted control
            // receives the tutorial tap and forwards it to the same navigation/action.
            Surface(
                Modifier
                    .offset { IntOffset(target.left.toInt() - 7, target.top.toInt() - 7) }
                    .size(
                        width = (target.width + 14).toInt().dp,
                        height = (target.height + 14).toInt().dp
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        when (step) {
                            1 -> onSettingsClick()
                            2 -> onMusicClick()
                            3 -> onEnableMusicClick()
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha)
                ),
                tonalElevation = 0.dp
            ) {}
        }

        Surface(
            Modifier
                .align(if (step == 0) Alignment.Center else Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = if (step == 0) 24.dp else bottomInset + 8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 5.dp
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(hint, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSkip) { Text(appText("walkthrough_skip", "Skip")) }
                    if (step == 0) {
                        Button(onClick = onNext, shape = RoundedCornerShape(22.dp)) { Text(appText("tutorial_show_me", "Show me")) }
                    } else if (step == 3) {
                        TextButton(onClick = onSkip) { Text(appText("tutorial_done", "Done")) }
                    }
                }
            }
        }
    }
}

private data class Quad(
    val title: String,
    val body: String,
    val hint: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun AppScreen() {
    AppTheme {
        // Android 12+: every dialog is composed into this one overlay host (iOS Liquid Glass style).
        // Android 11-: no host -> dialogs fall back to the previous Material ones.
        val dialogHost = remember { GlassDialogHostState() }
        CompositionLocalProvider(LocalGlassDialogHost provides (if (GlassSupport.blur) dialogHost else null)) {
            Box(Modifier.fillMaxSize()) {
                AppRouter()
                GitHubUpdateDialog()
                if (GlassSupport.blur) GlassDialogHost(dialogHost)
            }
        }
    }
}
