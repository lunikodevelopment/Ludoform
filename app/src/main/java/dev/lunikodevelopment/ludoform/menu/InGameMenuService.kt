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
import dev.lunikodevelopment.ludoform.romm.RommSettings
import dev.lunikodevelopment.ludoform.theme.LudoformTheme
import dev.lunikodevelopment.ludoform.transfer.SaveSyncWorker
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
 * The in-game menu (D39). Off until the player turns it on under Accessibility. It filters key events only to find the
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
    var abilities by mutableStateOf(MenuAbilities(screenshot = true, syncSaves = false, brightness = false))
    var game by mutableStateOf(MenuGame(null, "", null))
    var message by mutableStateOf<String?>(null)

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
                val now = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val next = (now + if (item == MenuItem.BRIGHTNESS_UP) 25 else -25).coerceIn(5, 255)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
                message = "Brightness ${next * 100 / 255}%"
            }
            MenuItem.RECORD, MenuItem.SWAP_SCREENS, MenuItem.ACHIEVEMENTS, MenuItem.MANUAL -> Unit
        }
    }

    private fun toggle() = if (overlay == null) open() else close()

    private fun open() {
        focused = MenuItem.RESUME
        message = null
        abilities = MenuAbilities(screenshot = true, syncSaves = false, brightness = Settings.System.canWrite(this))
        scope.launch { loadGame() }
        val owner = OverlayOwner().also { this.owner = it }
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent { LudoformTheme { InGameMenuOverlay(this@InGameMenuService) } }
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
        runCatching { topWindows().removeView(view) }
        owner?.destroy()
        owner = null
    }

    /** The top screen is the default display on the Thor; the overlay always goes there (D5). */
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
            abilities = abilities.copy(syncSaves = found.second)
        } else {
            game = MenuGame(null, "", null)
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
