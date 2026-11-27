package dev.lunikodevelopment.ludoform.menu

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.data.library.RommDao
import dev.lunikodevelopment.ludoform.model.TitleInfo
import dev.lunikodevelopment.ludoform.romm.GameExtras
import dev.lunikodevelopment.ludoform.romm.GameExtrasStore
import dev.lunikodevelopment.ludoform.romm.RommSettings
import dev.lunikodevelopment.ludoform.theme.LudoformTheme
import dev.lunikodevelopment.ludoform.transfer.SaveSyncWorker
import dev.lunikodevelopment.ludoform.ui.ThemeStore
import dev.lunikodevelopment.ludoform.ui.paletteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MenuEntryPoint {
    fun libraryDao(): LibraryDao

    fun rommDao(): RommDao
}

/** The game the menu is open over, as far as Ludoform knows (the open play session). */
data class MenuGame(val id: String?, val title: String, val startedAt: Long?)

/**
 * The in-game menu. Off until the player turns it on under Accessibility. It filters key events only to find the
 * menu pair held for about a second; single presses still reach the game. While the menu is open it takes the keys
 * and draws on the top screen as an accessibility overlay, with the game running underneath.
 */
class InGameMenuService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var detector: HoldDetector
    private var overlay: View? = null
    private var owner: OverlayOwner? = null

    var focused by mutableStateOf(MenuItem.RESUME)
    var abilities by mutableStateOf(MenuAbilities())
    var game by mutableStateOf(MenuGame(null, "", null))
    var message by mutableStateOf<String?>(null)

    /** What fills the menu card: the tiles, the achievements list or the manual. */
    var panel by mutableStateOf(MenuPanel.TILES)
    var extras by mutableStateOf<GameExtras?>(null)

    /** The focused achievement row, and the manual page shown. */
    var achievementIndex by mutableStateOf(0)
    var manualPage by mutableStateOf(0)
    var manualPages by mutableStateOf(0)
    var manualBitmap by mutableStateOf<Bitmap?>(null)
    private var manual: ManualReader? = null

    /** Message for the next time the menu opens, from the recording, which runs while the menu is closed. */
    private var pendingMessage: String? = null

    private val checkHold = Runnable { if (detector.due(SystemClock.uptimeMillis()) && !paused) toggle() }

    override fun onServiceConnected() {
        detector = HoldDetector(MenuButtons(this).pair)
        running = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = null
        close()
        scope.cancel()
        super.onDestroy()
    }

    /** Called when Quickstart records a new pair. */
    fun reloadButtons() {
        detector = HoldDetector(MenuButtons(this).pair)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val open = overlay != null
        if (detector.isMenuKey(code)) {
            val pressed = event.action == KeyEvent.ACTION_DOWN
            if (!pressed || event.repeatCount == 0) {
                val at = detector.key(code, pressed, event.eventTime)
                handler.removeCallbacks(checkHold)
                if (at != null) handler.postAtTime(checkHold, at)
            }
            // Single presses reach the game; with the menu open the pair only closes it.
            return open
        }
        if (!open) return false
        if (event.action == KeyEvent.ACTION_DOWN) onMenuKey(code)
        return true
    }

    private fun onMenuKey(code: Int) {
        when (panel) {
            MenuPanel.TILES -> Unit
            MenuPanel.ACHIEVEMENTS -> {
                val count = extras?.achievements?.size ?: 0
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> achievementIndex = (achievementIndex - 1).coerceAtLeast(0)
                    KeyEvent.KEYCODE_DPAD_DOWN -> achievementIndex = (achievementIndex + 1).coerceAtMost((count - 1).coerceAtLeast(0))
                    KeyEvent.KEYCODE_BUTTON_L1 -> achievementIndex = (achievementIndex - 5).coerceAtLeast(0)
                    KeyEvent.KEYCODE_BUTTON_R1 -> achievementIndex = (achievementIndex + 5).coerceAtMost((count - 1).coerceAtLeast(0))
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> panel = MenuPanel.TILES
                }
                return
            }
            MenuPanel.MANUAL -> {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_L1 -> showPage(manualPage - 1)
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_R1 -> showPage(manualPage + 1)
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> closeManual()
                }
                return
            }
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> focused = InGameMenu.move(focused, -1, 0, abilities)
            KeyEvent.KEYCODE_DPAD_RIGHT -> focused = InGameMenu.move(focused, 1, 0, abilities)
            KeyEvent.KEYCODE_DPAD_UP -> focused = InGameMenu.move(focused, 0, -1, abilities)
            KeyEvent.KEYCODE_DPAD_DOWN -> focused = InGameMenu.move(focused, 0, 1, abilities)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> select(focused)
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> close()
        }
    }

    fun select(item: MenuItem) {
        if (!abilities.enabled(item)) return
        when (item) {
            MenuItem.RESUME -> close()
            MenuItem.HOME -> {
                close()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            MenuItem.SCREENSHOT -> {
                // Closed first, so the picture shows the game and not the menu.
                close()
                handler.postDelayed({ screenshot() }, SCREENSHOT_DELAY_MS)
            }
            MenuItem.SYNC_SAVES -> {
                game.id?.let { SaveSyncWorker.enqueue(this, it, now = true) }
                message = "Saves are syncing with RomM"
            }
            MenuItem.VOLUME_DOWN, MenuItem.VOLUME_UP -> {
                val audio = getSystemService(AudioManager::class.java)
                val direction = if (item == MenuItem.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                message = "Volume $volume%"
            }
            MenuItem.BRIGHTNESS_DOWN, MenuItem.BRIGHTNESS_UP -> {
                if (!Settings.System.canWrite(this)) {
                    // Android asks the player once, on its own page; the menu closes so the page can show.
                    close()
                    startSafely(
                        android.content.Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:$packageName")),
                    )
                    pendingMessage = "Allow Ludoform to change brightness, then try again."
                    return
                }
                // Manual brightness, or Android's automatic brightness would undo the change.
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                val now = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val next = (now + if (item == MenuItem.BRIGHTNESS_UP) 25 else -25).coerceIn(5, 255)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
                message = "Brightness ${next * 100 / 255}%"
            }
            MenuItem.RECORD ->
                if (RecordService.startedAt != null) {
                    RecordService.stop(this)
                    message = "Saving the recording…"
                } else {
                    // Android's consent dialog needs the screen, so the menu closes first.
                    close()
                    startSafely(android.content.Intent(this, RecordConsentActivity::class.java))
                }
            MenuItem.ACHIEVEMENTS -> {
                achievementIndex = 0
                panel = MenuPanel.ACHIEVEMENTS
            }
            MenuItem.MANUAL -> openManual()
        }
    }

    private fun startSafely(intent: android.content.Intent) {
        val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        runCatching { startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle()) }
            .onFailure { message = "Android couldn't open that page." }
    }

    private fun openManual() {
        val file = extras?.manualFile ?: return
        val reader = ManualReader.open(java.io.File(file))
        if (reader == null) {
            message = "The manual couldn't be opened."
            return
        }
        manual = reader
        manualPages = reader.pages
        panel = MenuPanel.MANUAL
        showPage(game.id?.let { ManualReader.lastPage(this, it) } ?: 0)
    }

    private fun showPage(page: Int) {
        val reader = manual ?: return
        val target = page.coerceIn(0, reader.pages - 1)
        manualPage = target
        manualBitmap = reader.render(target, MANUAL_HEIGHT)
        game.id?.let { ManualReader.savePage(this, it, target) }
    }

    private fun closeManual() {
        manual?.close()
        manual = null
        manualBitmap = null
        panel = MenuPanel.TILES
    }

    fun recordingStarted() = toast("Recording the top screen. Open the menu and choose Stop to save it.")

    fun recordingStopped(saved: Boolean) {
        val text = if (saved) "Recording saved to Movies/Ludoform." else "The recording was too short to keep."
        if (overlay != null) message = text else toast(text)
    }

    fun recordingRefused() {
        pendingMessage = "Recording needs Android's permission each time."
    }

    fun recordingFailed() = toast("The recording couldn't start on this device.")

    private fun toast(text: String) = handler.post { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show() }

    private fun toggle() = if (overlay == null) open() else close()

    private fun open() {
        focused = MenuItem.RESUME
        message = pendingMessage
        pendingMessage = null
        panel = MenuPanel.TILES
        abilities = MenuAbilities()
        scope.launch { loadGame() }
        val owner = OverlayOwner().also { this.owner = it }
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    val theme = ThemeStore.load(this@InGameMenuService)
                    LudoformTheme(chosen = menuPalette(this@InGameMenuService, paletteFor(theme))) {
                        InGameMenuOverlay(this@InGameMenuService)
                    }
                }
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        runCatching { topWindows().addView(view, params) }.onSuccess { overlay = view }.onFailure { owner.destroy() }
    }

    fun close() {
        val view = overlay ?: return
        overlay = null
        closeManual()
        runCatching { topWindows().removeView(view) }
        owner?.destroy()
        owner = null
    }

    /** The top screen is the default display on the Thor; the overlay always goes there. */
    private fun topWindows(): WindowManager = getSystemService(WindowManager::class.java)

    private suspend fun loadGame() {
        val entry = EntryPointAccessors.fromApplication(applicationContext, MenuEntryPoint::class.java)
        val found =
            withContext(Dispatchers.IO) {
                val session = entry.libraryDao().getOpenSessions().lastOrNull() ?: return@withContext null
                val row = entry.libraryDao().getGame(session.gameId)
                val onRomm = entry.rommDao().romForGame(session.gameId) != null && RommSettings(this@InGameMenuService).server() != null
                Triple(
                    MenuGame(session.gameId, row?.title?.let { TitleInfo.parse(it).title } ?: "Your game", session.startedAt),
                    onRomm,
                    Unit,
                )
            }
        if (found != null) {
            game = found.first
            val stored = found.first.id?.let { id -> withContext(Dispatchers.IO) { GameExtrasStore(this@InGameMenuService).load(id) } }
            extras = stored
            abilities =
                abilities.copy(
                    syncSaves = found.second,
                    achievements = stored?.achievements?.isNotEmpty() == true,
                    manual = stored?.manualFile != null,
                )
        } else {
            game = MenuGame(null, "", null)
            extras = null
        }
    }

    private fun screenshot() {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    result.hardwareBuffer.close()
                    if (bitmap != null) scope.launch(Dispatchers.IO) { save(this@InGameMenuService, bitmap) }
                }

                override fun onFailure(errorCode: Int) = Unit
            },
        )
    }

    companion object {
        private const val SCREENSHOT_DELAY_MS = 350L
        private const val MANUAL_HEIGHT = 900

        /** Set while Choose the menu button records a pair, so holding it there doesn't open the menu. */
        @Volatile var paused = false

        /** The running service, for Quickstart to reload the buttons; null while it is off. */
        @Volatile var running: InGameMenuService? = null
            private set

        /** Whether the player turned the menu on under Accessibility. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.endsWith(InGameMenuService::class.java.name) || it.endsWith(".menu.InGameMenuService") }
        }

        /** Saves to Pictures/Ludoform, where the gallery finds it. */
        private fun save(
            context: Context,
            bitmap: Bitmap,
        ) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Ludoform_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Ludoform")
                }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

/** What a Compose view needs to live outside an activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
