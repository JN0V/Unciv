package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.ui.screens.GameStartScreen
import com.unciv.UncivGame
import com.unciv.models.TutorialTrigger
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.skins.SkinStrings
import com.unciv.ui.components.extensions.isNarrowerThan4to3
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.DispatcherVetoer
import com.unciv.ui.components.input.KeyShortcutDispatcher
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.input.installShortcutDispatcher
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.crashhandling.CrashScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.activePopup
import com.unciv.ui.popups.options.OptionsPopup
import com.unciv.ui.popups.options.OptionsPopupPages
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.logic.files.ScenarioProgress
import com.unciv.ui.screens.worldscreen.WorldScreen

// Both `this is CrashScreen` and `this::createPopupBasedDispatcherVetoer` are flagged.
// First - not a leak; second - passes out a pure function
@Suppress("LeakingThis")

abstract class BaseScreen : Screen {

    val game: UncivGame = UncivGame.Current
    val stage: Stage

    protected val tutorialController by lazy { TutorialController(this) }

    /**
     * Keyboard shortcuts global to the screen. While this is public and can be modified,
     * you most likely should use [keyShortcuts] on the appropriate [Actor] instead.
     */
    val globalShortcuts = KeyShortcutDispatcher()

    init {
        val screenSize = game.settings.screenSize
        // Portrait phone layout: 30% fewer virtual units across the screen, so text and icons render ~40% larger
        // (Small = 600 -> 420 units wide, which is the scale the mobile mockups were drawn at)
        val screenIsPortrait = Gdx.graphics.height > Gdx.graphics.width
        val height = if (game.settings.usePortraitLayout(screenIsPortrait)) screenSize.virtualHeight * 0.7f
            else screenSize.virtualHeight

        /** The ExtendViewport sets the _minimum_(!) world size - the actual world size will be larger, fitted to screen/window aspect ratio. */
        stage = UncivStage(ExtendViewport(height, height))
        applySafeInset(Gdx.graphics.width, Gdx.graphics.height)

        if (enableSceneDebug.active && this !is CrashScreen && this !is GameStartScreen)
            stage.setSceneDebugMode()

        @Suppress("LeakingThis")
        stage.installShortcutDispatcher(globalShortcuts, this::createDispatcherVetoer)
    }

    /** Hook allowing derived Screens to supply a key shortcut vetoer that can exclude parts of the
     *  Stage Actor hierarchy from the search. Only called if no [Popup] is active.
     *  @see installShortcutDispatcher
     */
    open fun getShortcutDispatcherVetoer(): DispatcherVetoer? = null

    private fun createDispatcherVetoer(): DispatcherVetoer? {
        val activePopup = this.activePopup
            ?: return getShortcutDispatcherVetoer()
        return KeyShortcutDispatcherVeto.createPopupBasedDispatcherVetoer(activePopup)
    }

    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Screens may be built off the GL thread, where the viewport's glViewport call is lost: re-apply here
        stage.viewport.apply()
        stage.act()
        stage.draw()
        debugScreenshotIfRequested()
    }

    private var debugFramesRendered = 0
    /** Development aid: with env UNCIV_DEBUG_SCREENSHOT=<png path>, saves the current screen after a few frames and exits.
     *  Env UNCIV_DEBUG_ACTION triggers [debugAction] once before that. */
    private fun debugScreenshotIfRequested() {
        val path = System.getenv("UNCIV_DEBUG_SCREENSHOT") ?: return
        debugFramesRendered++
        Gdx.graphics.requestRendering()  // the game renders on demand; keep frames coming for the capture
        // Several actions separated by ';' run 15 frames apart (e.g. "tap:x,y;tap:x,y" to move a unit)
        val actions = System.getenv("UNCIV_DEBUG_ACTION")?.split(';') ?: emptyList()
        if (debugFramesRendered >= 20 && (debugFramesRendered - 20) % 15 == 0) {
            val index = (debugFramesRendered - 20) / 15
            if (index < actions.size) debugAction(actions[index])
        }
        val targetScreen = System.getenv("UNCIV_DEBUG_SCREEN") ?: "WorldScreen"  // which screen class to capture
        if (javaClass.simpleName != targetScreen || debugFramesRendered < 20 + 15 * actions.size + 40) return
        val pixmap = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, java.util.zip.Deflater.DEFAULT_COMPRESSION, true)
        pixmap.dispose()
        Gdx.app.exit()
    }
    protected open fun debugAction(action: String) {}

    override fun resize(width: Int, height: Int) {
        if (this !is RecreateOnResize) {
            applySafeInset(width, height)
        } else if (!viewportMatches(width, height)) {
            game.replaceCurrentScreen{ recreate() }
        }
    }

    /** True when the stage viewport already fits this screen size (accounting for the reserved cutout band) */
    fun viewportMatches(width: Int, height: Int) =
        stage.viewport.screenWidth == width && stage.viewport.screenHeight == height - topSafeInsetPixels(width, height)

    /** Pixels reserved at the top for a display cutout (portrait phone layout only); the band shows [clearColor] */
    private fun topSafeInsetPixels(width: Int, height: Int): Int =
        if (game.settings.usePortraitLayout(height > width)) com.unciv.utils.Display.cutoutInsetTop else 0

    /** Sizes the viewport so the whole stage sits below the display cutout */
    private fun applySafeInset(width: Int, height: Int) {
        val inset = topSafeInsetPixels(width, height)
        stage.viewport.update(width, height - inset, true)
        stage.viewport.setScreenPosition(0, 0)
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    /**
     * Called when this screen should release all resources.
     *
     * This is _not_ called automatically by Gdx, but by the [screenStack][UncivGame.screenStack]
     * functions in [UncivGame], e.g. [replaceCurrentScreen][UncivGame.replaceCurrentScreen].
     */
    override fun dispose() {
        // FYI - This is a method of Gdx [Screen], not of Gdx [Disposable], but the one below _is_.
        stage.dispose()
    }

    fun displayTutorial(tutorial: TutorialTrigger, test: (() -> Boolean)? = null) {
        if (!game.settings.showTutorials) return
        if (game.settings.tutorialsShown.contains(tutorial.name)) return
        if (this is WorldScreen && this.autoPlay.isAutoPlaying()) return
        // Phone layout, in a scenario: its briefing and task card replace the three generic welcome popups
        if (game.settings.usePortraitLayout(isPortrait()) && this is WorldScreen && ScenarioProgress.isScenarioGame(gameInfo.gameId)
                && tutorial in setOf(TutorialTrigger.Introduction, TutorialTrigger.NewGame, TutorialTrigger.SlowStart)) return
        if (test != null && !test()) return
        tutorialController.showTutorial(tutorial)
    }

    companion object {
        var enableSceneDebug = SceneDebugMode.None

        /** Colour to use for empty sections of the screen.
         *  Gets overwritten by SkinConfig.clearColor after starting Unciv */
        var clearColor = Color(0f, 0f, 0.2f, 1f)

        lateinit var skin: Skin
        lateinit var skinStrings: SkinStrings

        fun setSkin() {
            Fonts.resetFont()
            skinStrings = SkinStrings()
            skin = Skin().apply {
                add("default-clear", clearColor, Color::class.java)
                add("Nativefont", Fonts.font, BitmapFont::class.java)
                add("RoundedEdgeRectangle", skinStrings.getUiBackground("", skinStrings.roundedEdgeRectangleShape), Drawable::class.java)
                add("Rectangle", ImageGetter.getDrawable(""), Drawable::class.java)
                add("Circle", ImageGetter.getCircleDrawable().apply { setMinSize(20f, 20f) }, Drawable::class.java)
                add("Scrollbar", ImageGetter.getDrawable("").apply { setMinSize(10f, 10f) }, Drawable::class.java)
                add("RectangleWithOutline",
                    skinStrings.getUiBackground("", skinStrings.rectangleWithOutlineShape), Drawable::class.java)
                add("Select-box", skinStrings.getUiBackground("", skinStrings.selectBoxShape), Drawable::class.java)
                add("Select-box-pressed", skinStrings.getUiBackground("", skinStrings.selectBoxPressedShape), Drawable::class.java)
                add("Checkbox", skinStrings.getUiBackground("", skinStrings.checkboxShape), Drawable::class.java)
                add("Checkbox-pressed", skinStrings.getUiBackground("", skinStrings.checkboxPressedShape), Drawable::class.java)
                load(Gdx.files.internal("Skin.json"))
            }
            skin.get(TextButton.TextButtonStyle::class.java).font = Fonts.font
            skin.get(CheckBox.CheckBoxStyle::class.java).apply {
                font = Fonts.font
                fontColor = Color.WHITE
            }
            skin.get(Label.LabelStyle::class.java).apply {
                font = Fonts.font
                fontColor = Color.WHITE
            }
            skin.get(TextField.TextFieldStyle::class.java).font = Fonts.font
            skin.get(SelectBox.SelectBoxStyle::class.java).apply {
                font = Fonts.font
                listStyle.font = Fonts.font
            }
            clearColor = skinStrings.skinConfig.clearColor
        }
    }

    /** @return `true` if the screen is higher than it is wide */
    fun isPortrait() = stage.viewport.screenHeight > stage.viewport.screenWidth
    /** @return `true` if the screen is higher than it is wide _and_ resolution is at most 1050x700 */
    fun isCrampedPortrait() = isPortrait() &&
            game.settings.screenSize.virtualHeight <= 700
    /** @return `true` if the screen is narrower than 4:3 landscape */
    fun isNarrowerThan4to3() = stage.isNarrowerThan4to3()

    open fun openOptionsPopup(startingPage: OptionsPopupPages = OptionsPopup.defaultPage, withDebug: Boolean = false, onClose: () -> Unit = {}) {
        OptionsPopup(this, startingPage, withDebug, onClose).open(force = true)
    }

    /**
     *  Determine a Ruleset for Civilopedia to use (remember: it is supposed to work without a running game loaded)
     *
     *  - `open` as some important screens are supposed to provide directly.
     *  - The default implementation searches using the [screenStack][UncivGame.screenStack] for a source of a Ruleset and returns Civ_V_GnK when that fails.
     *  - Care must be taken in [PickerScreen][com.unciv.ui.screens.pickerscreens.PickerScreen] derivates - they will default to the searching implementation, but often could do the task more efficiently.
     */
    open fun getCivilopediaRuleset(): Ruleset {
        if (game.worldScreen != null) return game.worldScreen!!.gameInfo.ruleset
        val mainMenuScreen = game.getScreensOfType(MainMenuScreen::class).firstOrNull()
        if (mainMenuScreen != null) return mainMenuScreen.getCivilopediaRuleset()
        return RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
    }

    /** Opens Civilopedia
     *
     *  It's an open method of BaseScreen because especially MainMenuScreen has cleanup things to do first.
     *  @see getCivilopediaRuleset
     */
    open fun openCivilopedia(link: String = "") = openCivilopedia(getCivilopediaRuleset(), link)

    /** Helper for the [openCivilopedia] (link: String) overload to use
     *  - Note: At the time of wrinting, this was the ***only*** CivilopediaScreen constructor call outside itself
     */
    fun openCivilopedia(ruleset: Ruleset, link: String = "") {
        game.pushScreen{ CivilopediaScreen(ruleset, link = link) }
    }
}

interface RecreateOnResize {
    fun recreate(): BaseScreen
}
