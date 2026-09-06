package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfoPreview
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.PortraitWidgets
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.LoadingPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

/**
 *  "Discovery" screen: every scenario shipped by an installed mod, as a numbered list of cards.
 *  Tapping a card starts the scenario directly (or offers to resume the latest save of it).
 *  Scenarios are the files under `scenarios/` in a mod folder - see [com.unciv.logic.files.UncivFiles.getScenarioFiles].
 *
 *  A mod can describe its scenarios through its translation files with the keys
 *  `<scenario name> description` and `<scenario name> duration` - both optional.
 */
class ScenarioListScreen : BaseScreen(), RecreateOnResize {

    private class ScenarioEntry(val name: String, val file: FileHandle, val mod: Ruleset) {
        var gameId = ""
        var latestSave: GameInfoPreview? = null
        var latestSaveFile: FileHandle? = null
        var completed = false
        val inProgress get() = latestSave != null && !completed
    }

    private val entries: List<ScenarioEntry> = game.files.getScenarioFiles()
        .map { (file, mod) -> ScenarioEntry(file.name(), file, mod) }
        .sortedWith(compareBy({ it.mod.name }, { it.name }))
        .toList()

    private val panelColor = Color(0.03f, 0.05f, 0.24f, 0.96f)
    private val todoColor = Color(0.2f, 0.3f, 0.5f, 0.45f)
    private val currentColor = Color(0.22f, 0.6f, 0.86f, 0.25f)
    private val doneColor = Color(0.12f, 0.5f, 0.22f, 0.25f)
    private val primaryColor = Color(0.12f, 0.5f, 0.22f, 1f)
    private val badgeColor = Color(0.2f, 0.3f, 0.5f, 1f)
    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)
    private val progressColor = Color(0.55f, 0.8f, 1f, 1f)

    private val root = Table()
    private val listTable = Table()
    private val bottomTable = Table()

    init {
        val compact = game.settings.usePortraitLayout(isPortrait())
        val contentWidth = if (compact) stage.width - 16f else minOf(stage.width - 40f, 640f)

        root.setFillParent(true)
        root.top()
        root.add(buildHeader()).width(contentWidth).padTop(8f).row()
        listTable.top()
        listTable.defaults().width(contentWidth).padTop(10f)
        val scroll = AutoScrollPane(listTable)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).width(contentWidth + 16f).grow().row()
        root.add(bottomTable).width(contentWidth).pad(8f).row()
        stage.addActor(root)

        globalShortcuts.add(KeyCharAndCode.BACK) { game.popScreen() }

        rebuildList()
        scanSaves()
    }

    private fun bg(part: String, color: Color, small: Boolean = false) = skinStrings.getUiBackground(
        "ScenarioListScreen/$part",
        if (small) skinStrings.roundedEdgeRectangleSmallShape else skinStrings.roundedEdgeRectangleShape,
        color
    )

    private fun buildHeader(): Table {
        val header = Table()
        header.background = bg("Header", panelColor)
        header.pad(10f, 12f, 10f, 12f)

        header.add(PortraitWidgets.backButton { game.popScreen() }).padRight(12f)

        val titles = Table()
        titles.add("Discovery".toLabel(fontSize = 24, alignment = Align.left)).left().row()
        titles.add("Learn by playing, one scenario at a time".toLabel(fontSize = 15, fontColor = muted, alignment = Align.left)).left()
        header.add(titles).expandX().left()
        return header
    }

    private fun rebuildList() {
        listTable.clear()
        bottomTable.clear()

        if (entries.isEmpty()) {
            val empty = Table()
            empty.background = bg("Card", todoColor)
            empty.pad(16f)
            empty.add("No scenarios installed. Install a mod that provides scenarios.".toLabel(fontSize = 17).apply { wrap = true; setAlignment(Align.center) }).growX()
            listTable.add(empty).row()
            return
        }

        // The scenarios are a course: the next one is the first not completed, resumed if a save exists
        val current = entries.firstOrNull { !it.completed } ?: entries.first()
        val showModNames = entries.map { it.mod.name }.distinct().size > 1
        var lastMod: String? = null
        var number = 0
        for (entry in entries) {
            if (entry.mod.name != lastMod) {
                lastMod = entry.mod.name
                number = 0
                if (showModNames)
                    listTable.add(entry.mod.name.toLabel(fontSize = 17, fontColor = muted, alignment = Align.left)).left().padTop(14f).row()
            }
            number++
            listTable.add(buildCard(entry, number, entry === current)).row()
        }

        val primary = Table()
        primary.background = bg("Primary", primaryColor)
        primary.touchable = Touchable.enabled
        primary.pad(12f)
        val primaryText = if (current.inProgress) "Resume [${displayName(current)}]" else "Start [${displayName(current)}]"
        primary.add(ImageGetter.getImage("OtherIcons/ForwardArrow")).size(22f).padRight(10f)
        primary.add(primaryText.toLabel(fontSize = 20))
        primary.onActivation { onScenarioChosen(current) }
        bottomTable.add(primary).growX().minHeight(56f)
    }

    private fun buildCard(entry: ScenarioEntry, number: Int, isCurrent: Boolean): Table {
        val card = Table()
        card.touchable = Touchable.enabled
        card.pad(10f, 12f, 10f, 12f)
        card.background = bg("Card", when {
            entry.completed -> doneColor
            isCurrent -> currentColor
            else -> todoColor
        })

        val badge = if (entry.completed)
            ImageGetter.getImage("OtherIcons/Checkmark").apply { setSize(24f, 24f) }
                .surroundWithCircle(44f, color = primaryColor)
        else
            number.toString().toLabel(fontSize = 20).apply { setAlignment(Align.center) }
                .surroundWithCircle(44f, color = if (isCurrent) Color(0.22f, 0.6f, 0.86f, 1f) else badgeColor)
        card.add(badge).size(44f).padRight(12f)

        val texts = Table()
        texts.add(displayName(entry).toLabel(fontSize = 19, alignment = Align.left).apply { wrap = true }).growX().left().row()
        val subtitle = subtitleFor(entry)
        if (subtitle.isNotEmpty())
            texts.add(subtitle.toLabel(fontSize = 15, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
        val save = entry.latestSave
        if (save != null && !entry.completed)
            texts.add("In progress, turn [${save.turns}]".toLabel(fontSize = 15, fontColor = progressColor, alignment = Align.left)).left().padTop(2f)
        card.add(texts).growX().minHeight(44f)

        val right: Table = Table()
        when {
            entry.completed -> right.add("Finished".toLabel(fontSize = 15, fontColor = Color(0.4f, 0.86f, 0.45f, 1f)))
            else -> right.add(ImageGetter.getImage("OtherIcons/ForwardArrow").apply { color = if (isCurrent) Color.WHITE else muted }).size(20f)
        }
        card.add(right).padLeft(8f)

        card.onActivation { onScenarioChosen(entry) }
        return card
    }

    /** The file name, translated by the scenario's mod when it ships a translation (the mod is not active on the main menu) */
    private fun displayName(entry: ScenarioEntry): String =
        game.translations.getText(entry.name, game.settings.language, hashSetOf(entry.mod.name), entry.name)

    private fun subtitleFor(entry: ScenarioEntry): String {
        val language = game.settings.language
        val mods = hashSetOf(entry.mod.name)
        val description = game.translations.getText("${entry.name} description", language, mods, "")
        val duration = game.translations.getText("${entry.name} duration", language, mods, "")
        val parts = ArrayList<String>()
        if (description.isNotEmpty()) parts.add(description)
        if (duration.isNotEmpty()) parts.add(duration)
        return parts.joinToString(" · ")
    }

    /** Reads the scenario previews and every save to find, per scenario, the latest save of that game */
    private fun scanSaves() {
        Concurrency.run("ScanScenarioSaves") {
            for (entry in entries) {
                try {
                    entry.gameId = game.files.loadGamePreviewFromFile(entry.file).gameId
                } catch (_: Exception) { }
            }
            val byGameId = entries.filter { it.gameId.isNotEmpty() }.associateBy { it.gameId }
            if (byGameId.isNotEmpty()) {
                for (saveFile in game.files.getSaves()) {
                    val preview = try { game.files.loadGamePreviewFromFile(saveFile) } catch (_: Exception) { continue }
                    val entry = byGameId[preview.gameId] ?: continue
                    val latest = entry.latestSave
                    if (latest == null || preview.turns > latest.turns) {
                        entry.latestSave = preview
                        entry.latestSaveFile = saveFile
                    }
                }
            }
            for (entry in entries)
                entry.completed = entry.gameId.isNotEmpty() && entry.gameId in game.settings.wonGameIds
            launchOnGLThread { rebuildList() }
        }
    }

    private fun onScenarioChosen(entry: ScenarioEntry) {
        val saveFile = entry.latestSaveFile
        val save = entry.latestSave
        if (saveFile == null || save == null) {
            loadScenario(entry.file, freshStart = true)
            return
        }
        val popup = Popup(this)
        popup.addGoodSizedLabel(displayName(entry), 22).row()
        popup.addButton(if (entry.completed) "Continue" else "Resume at turn [${save.turns}]") {
            popup.close(); loadScenario(saveFile)
        }.row()
        popup.addButton("Restart from the beginning") { popup.close(); loadScenario(entry.file, freshStart = true) }.row()
        popup.addCloseButton()
        popup.open()
    }

    /** @param freshStart true when starting the scenario file itself (not resuming a save of it):
     *  scenarios are tutorials, so the device-wide "tutorial task completed" memory is reset so their guidance shows again */
    private fun loadScenario(file: FileHandle, freshStart: Boolean = false) {
        val loadingPopup = LoadingPopup(this)
        Concurrency.run("LoadScenario") {
            try {
                if (freshStart) {
                    game.settings.tutorialTasksCompleted.clear()
                    game.settings.showTutorials = true
                    game.isTutorialTaskCollapsed = false
                    game.settings.tutorialTasksExplained.clear()
                    game.settings.save()
                }
                val gameInfo = game.files.loadGameFromFile(file)
                game.loadGame(gameInfo, callFromLoadScreen = true)
            } catch (ex: Exception) {
                launchOnGLThread {
                    loadingPopup.close()
                    val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                    ToastPopup(message, this@ScenarioListScreen)
                }
            }
        }
    }

    override fun recreate(): BaseScreen = ScenarioListScreen()

    companion object {
        /** gameIds of the scenarios shipped by installed mods; read once per process (the previews are small, the list rarely changes) */
        private val scenarioGameIds: Set<String> by lazy {
            val files = UncivGame.Current.files
            files.getScenarioFiles().mapNotNull { (file, _) ->
                try { files.loadGamePreviewFromFile(file).gameId } catch (_: Exception) { null }
            }.toSet()
        }

        /** True when [gameId] is the id of a scenario shipped by an installed mod */
        fun isScenarioGame(gameId: String): Boolean = gameId.isNotEmpty() && gameId in scenarioGameIds
    }
}
