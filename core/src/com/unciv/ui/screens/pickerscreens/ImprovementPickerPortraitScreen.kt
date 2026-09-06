package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.PortraitWidgets
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import kotlin.math.roundToInt

/**
 *  Phone version of [ImprovementPickerScreen]: the tile at the top, then one row per improvement
 *  (icon, name, turns, what changes on the tile, or why it is not possible yet), a green button to build.
 */
class ImprovementPickerPortraitScreen(
    private val tile: Tile,
    private val unit: MapUnit,
    private val onAccept: () -> Unit
) : BaseScreen(), RecreateOnResize {

    private val gameInfo = tile.tileMap.gameInfo
    private val ruleset = gameInfo.ruleset
    private val civ = gameInfo.getCurrentPlayerCivilization()
    private var selected: TileImprovement? = null

    private val panelColor = Color(0.03f, 0.05f, 0.24f, 0.96f)
    private val rowColor = Color(0.2f, 0.3f, 0.5f, 0.45f)
    private val rowDim = Color(0.15f, 0.17f, 0.24f, 0.8f)
    private val selectedColor = Color(0.22f, 0.6f, 0.86f, 0.6f)
    private val inProgressColor = Color(0.12f, 0.5f, 0.22f, 0.35f)
    private val primaryColor = Color(0.12f, 0.5f, 0.22f, 1f)
    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)
    private val warn = Color(1f, 0.75f, 0.5f, 1f)

    private class Option(val improvement: TileImprovement, val problems: List<String>)

    private val listTable = Table()
    private val bottomTable = Table()
    private val options: List<Option> = buildOptions()

    init {
        val root = Table()
        root.setFillParent(true)
        root.top()
        val width = stage.width - 16f
        root.add(buildHeader()).width(width).padTop(8f).row()
        listTable.top()
        listTable.defaults().width(width).padTop(6f)
        val scroll = AutoScrollPane(listTable)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).width(width + 16f).grow().row()
        root.add(bottomTable).width(width).pad(8f).row()
        stage.addActor(root)
        globalShortcuts.add(KeyCharAndCode.BACK) { game.popScreen() }
        selected = options.firstOrNull { it.improvement.name == tile.improvementInProgress }?.improvement
        rebuild()
    }

    private fun bg(part: String, color: Color) = skinStrings.getUiBackground(
        "ImprovementPicker/Portrait/$part", skinStrings.roundedEdgeRectangleSmallShape, color)

    /** Same filter and problem analysis as the classic screen ([ImprovementPickerScreen.getProblemReport]: "remove the
     *  forest first" fallback, era cap on far-away techs); each proposed solution becomes one line of advice */
    private fun buildOptions(): List<Option> {
        val tileWithoutLastTerrain = ImprovementPickerScreen.getTileWithoutLastTerrain(tile, ruleset)
        val maxErasForward = ImprovementPickerScreen.getMaxErasForward(ruleset)
        val result = ArrayList<Option>()
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.turnsToBuild == -1 && improvement.name != Constants.cancelImprovementOrder) continue
            if (improvement.name == tile.improvement) continue
            if (!unit.canBuildImprovement(improvement)) continue
            val report = ImprovementPickerScreen.getProblemReport(tile, tileWithoutLastTerrain, improvement, unit, maxErasForward) ?: continue
            result.add(Option(improvement, report.proposedSolutions.map { (text, _) -> text.tr() }))
        }
        // Possible first, cancel last
        return result.sortedWith(compareBy({ it.improvement.name == Constants.cancelImprovementOrder }, { it.problems.isNotEmpty() }))
    }

    private fun buildHeader(): Table {
        val header = Table()
        header.background = bg("Header", panelColor)
        header.pad(10f, 12f, 10f, 12f)
        header.add(PortraitWidgets.backButton { game.popScreen() }).padRight(12f)
        val titles = Table()
        titles.add("Build an improvement".toLabel(fontSize = 22, alignment = Align.left)).left().row()
        val parts = ArrayList<String>()
        parts.add(tile.lastTerrain.name.tr())
        tile.resource?.let { parts.add(it.tr()) }
        tile.improvement?.let { parts.add("{Currently}: {$it}".tr()) }
        val owner = tile.getOwner()
        parts.add(when {
            owner == null -> "Unowned tile".tr()
            owner.isCurrentPlayer() -> tile.getCity()!!.name.tr()
            else -> "Tile owned by [${owner.civName}] - [${tile.getCity()!!.name}]".tr()
        })
        titles.add(parts.joinToString(" · ").toLabel(fontSize = 15, fontColor = muted, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left()
        header.add(titles).growX().left()
        return header
    }

    private fun rebuild() {
        listTable.clear()
        for (option in options) listTable.add(buildRow(option)).row()
        rebuildBottom()
    }

    private fun buildRow(option: Option): Table {
        val improvement = option.improvement
        val row = Table()
        row.touchable = Touchable.enabled
        row.pad(8f, 10f, 8f, 10f)
        val isSelected = improvement == selected
        val inProgress = improvement.name == tile.improvementInProgress
        val possible = option.problems.isEmpty()
        row.background = bg("Row", when {
            isSelected -> selectedColor
            inProgress -> inProgressColor
            possible -> rowColor
            else -> rowDim
        })
        row.add(ImageGetter.getImprovementPortrait(improvement.name, 36f)).padRight(10f)

        val texts = Table()
        val turns = if (inProgress) tile.turnsToImprovement else improvement.getTurnsToBuild(civ, unit)
        var title = improvement.name.tr(true)
        if (turns > 0) title += "  $turns${Fonts.turn}"
        texts.add(title.toLabel(fontSize = 17, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left().row()

        // What the tile gains: resource, stat changes, replacement
        val details = Table()
        details.defaults().padRight(6f)
        val tileResource = tile.tileResource
        if (civ.canSeeResource(tileResource) && tileResource.isImprovedBy(improvement.name)) {
            details.add(ImageGetter.getResourcePortrait(tile.resource!!, 20f))
            details.add("Provides [${tileResource.name}]".toLabel(fontSize = 14, fontColor = muted, hideIcons = true)).padRight(10f)
        }
        val stats = tile.stats.getStatDiffForImprovement(improvement, civ, tile.getCity())
        for ((stat, value) in stats) {
            val rounded = (value * 10).roundToInt() * 0.1f
            if (rounded == 0f) continue
            details.add(ImageGetter.getStatIcon(stat.name)).size(18f).padRight(2f)
            details.add((if (rounded > 0) "+" else "").plus(rounded.tr()).toLabel(fontSize = 14, fontColor = if (rounded < 0) Color.RED else Color.WHITE))
        }
        if (details.hasChildren()) texts.add(details).left().padTop(2f).row()
        if (tile.improvement != null && !improvement.isRoad() && !improvement.name.startsWith(Constants.remove) && improvement.name != Constants.cancelImprovementOrder)
            texts.add("Replaces [${tile.improvement}]".toLabel(fontSize = 14, fontColor = muted, hideIcons = true)).left().padTop(2f).row()
        if (inProgress) texts.add("Current construction".toLabel(fontSize = 14, fontColor = Color(0.4f, 0.86f, 0.45f, 1f))).left().padTop(2f).row()
        for (problem in option.problems)
            texts.add(problem.toLabel(fontSize = 14, fontColor = warn, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left().padTop(2f).row()
        if (isSelected) {
            val description = improvement.getDescription(ruleset)
            if (description.isNotEmpty())
                texts.add(description.toLabel(fontSize = 14, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
        }
        row.add(texts).growX()
        if (!possible) row.add(ImageGetter.getImage("OtherIcons/LockSmall").apply { color = muted }).size(16f).padLeft(8f)

        row.onClick {
            selected = if (isSelected) null else improvement
            rebuild()
        }
        return row
    }

    private fun rebuildBottom() {
        bottomTable.clear()
        val improvement = selected
        val option = options.firstOrNull { it.improvement == improvement }
        val possible = option != null && option.problems.isEmpty() && !tile.isMarkedForCreatesOneImprovement()
        val button = Table()
        button.pad(12f)
        button.background = bg("Primary", if (possible) primaryColor else rowDim)
        val text = when {
            improvement == null -> "Pick improvement"
            improvement.name == Constants.cancelImprovementOrder -> improvement.name
            possible -> "Build [${improvement.name}]"
            else -> "Pick improvement"
        }
        button.add(text.toLabel(fontSize = 19, fontColor = if (possible) Color.WHITE else muted, hideIcons = true).apply { wrap = true; setAlignment(Align.center) }).growX()
        if (possible) {
            button.touchable = Touchable.enabled
            button.onClick { accept(improvement!!) }
        }
        bottomTable.add(button).growX().minHeight(56f)
    }

    private fun accept(improvement: TileImprovement) {
        if (improvement.name == Constants.cancelImprovementOrder) {
            tile.stopWorkingOnImprovement()
        } else {
            if (improvement.name != tile.improvementInProgress) {
                tile.startWorkingOnImprovement(improvement, civ, unit)
                // The tutorial task is about giving the order, not waiting for the result
                game.settings.addCompletedTutorialTask("Construct an improvement")
            }
            unit.action = null
            onAccept()
        }
        game.popScreen()
    }

    override fun getCivilopediaRuleset() = ruleset
    override fun recreate(): BaseScreen = ImprovementPickerScreen.create(tile, unit, onAccept)
}
