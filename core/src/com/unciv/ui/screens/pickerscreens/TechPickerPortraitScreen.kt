package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.PortraitWidgets
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.objectdescriptions.TechnologyDescriptions
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize

/**
 *  Phone version of [TechPickerScreen]: a list instead of the tree. Researchable technologies first (name, turns,
 *  what they unlock), then the ones that need more, then the researched ones folded away.
 *  Tapping a card selects it (and the path to it); the green button starts the research.
 */
class TechPickerPortraitScreen(
    private val civInfo: Civilization,
    select: Technology? = null
) : BaseScreen(), RecreateOnResize {

    private val ruleset = civInfo.gameInfo.ruleset
    private val civTech = civInfo.tech
    private val freeTechPick = civTech.freeTechs != 0
    private val researchable = ruleset.technologies.keys.filter { civTech.canBeResearched(it) }.toHashSet()
    private var selected: Technology? = select
    private var path: List<Technology> = emptyList()
    private var showResearched = false

    private val panelColor = Color(0.03f, 0.05f, 0.24f, 0.96f)
    private val rowColor = Color(0.2f, 0.3f, 0.5f, 0.45f)
    private val rowDim = Color(0.15f, 0.17f, 0.24f, 0.8f)
    private val selectedColor = Color(0.22f, 0.6f, 0.86f, 0.6f)
    private val currentColor = Color(0.12f, 0.5f, 0.22f, 0.35f)
    private val primaryColor = Color(0.12f, 0.5f, 0.22f, 1f)
    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)

    private val listTable = Table()
    private val bottomTable = Table()

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
        if (selected == null && !freeTechPick) selected = civTech.currentTechnology()
        updatePath()
        rebuild()
    }

    private fun bg(part: String, color: Color) = skinStrings.getUiBackground(
        "TechPicker/Portrait/$part", skinStrings.roundedEdgeRectangleSmallShape, color)

    private fun updatePath() {
        val tech = selected
        path = if (tech == null || civTech.isResearched(tech.name) && !tech.isContinuallyResearchable()) emptyList()
            else civTech.getRequiredTechsToDestination(tech)
    }

    private fun buildHeader(): Table {
        val header = Table()
        header.background = bg("Header", panelColor)
        header.pad(10f, 12f, 10f, 12f)
        header.add(PortraitWidgets.backButton { game.popScreen() }).padRight(12f)
        val titles = Table()
        titles.add((if (freeTechPick) "Pick a free tech" else "Research").toLabel(fontSize = 22, alignment = Align.left)).left().row()
        val science = civInfo.stats.statsForNextTurn.science.toInt()
        val current = civTech.currentTechnologyName()
        val subtitle = "{Science}: +$science${Fonts.science}" + (if (current != null) "  ·  {Current research}: {$current}" else "")
        titles.add(subtitle.toLabel(fontSize = 15, fontColor = muted, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left()
        header.add(titles).growX().left()
        // The classic tree stays one tap away (and comes back by itself when the phone turns to landscape)
        val treeButton = Table()
        treeButton.background = bg("Tree", rowColor)
        treeButton.touchable = Touchable.enabled
        treeButton.pad(6f, 12f, 6f, 12f)
        treeButton.add("Tree".toLabel(fontSize = 15))
        treeButton.onClick { game.replaceCurrentScreen { TechPickerScreen(civInfo, selected) } }
        header.add(treeButton).padLeft(8f)
        return header
    }

    private fun rebuild() {
        listTable.clear()
        val all = ruleset.technologies.values.sortedWith(compareBy({ it.column?.columnNumber ?: 0 }, { it.row }))
        val now = all.filter { it.name in researchable }
        val later = all.filter { it.name !in researchable && !civTech.isResearched(it.name) }
        val done = all.filter { civTech.isResearched(it.name) && !it.isContinuallyResearchable() }

        listTable.add("Available now".toLabel(fontSize = 16, fontColor = muted, alignment = Align.left)).left().padTop(10f).row()
        for (tech in now) listTable.add(buildCard(tech, available = true)).row()

        if (later.isNotEmpty()) {
            listTable.add("Needs other technologies first".toLabel(fontSize = 16, fontColor = muted, alignment = Align.left)).left().padTop(14f).row()
            for (tech in later) listTable.add(buildCard(tech, available = false)).row()
        }

        if (done.isNotEmpty()) {
            val toggle = Table()
            toggle.touchable = Touchable.enabled
            toggle.add("{Researched}: [${done.size}]".toLabel(fontSize = 16, fontColor = muted, alignment = Align.left)).expandX().left()
            toggle.add(ImageGetter.getImage("OtherIcons/BackArrow").apply { rotation = if (showResearched) -90f else 90f; setOrigin(Align.center) }
                .let { val h = Table(); h.add(it).size(14f); h })
            toggle.onClick { showResearched = !showResearched; rebuild() }
            listTable.add(toggle).padTop(14f).row()
            if (showResearched) for (tech in done) listTable.add(buildCard(tech, available = false, researched = true)).row()
        }
        rebuildBottom()
    }

    private fun buildCard(tech: Technology, available: Boolean, researched: Boolean = false): Table {
        val card = Table()
        card.touchable = Touchable.enabled
        card.pad(8f, 10f, 8f, 10f)
        val isSelected = tech == selected
        val onPath = path.contains(tech) && !isSelected
        val isCurrent = tech.name == civTech.currentTechnologyName()
        card.background = bg("Card", when {
            isSelected -> selectedColor
            onPath -> Color(0.22f, 0.6f, 0.86f, 0.35f)
            isCurrent -> currentColor
            available -> rowColor
            else -> rowDim
        })
        card.add(ImageGetter.getTechIconPortrait(tech.name, 40f)).padRight(10f).top()

        val texts = Table()
        val title = Table()
        title.add(tech.name.toLabel(fontSize = 18, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left()
        if (!researched) title.add("${civTech.turnsToTech(tech.name)}${Fonts.turn}".toLabel(fontSize = 15, fontColor = muted)).padLeft(8f).top()
        if (isCurrent) title.add("Current research".toLabel(fontSize = 13, fontColor = Color(0.4f, 0.86f, 0.45f, 1f))).padLeft(8f).top()
        texts.add(title).growX().row()

        // What it unlocks: the same icons as the tree buttons, then names on one wrapped line
        val icons = Table()
        icons.defaults().padRight(4f)
        var count = 0
        for (icon in TechnologyDescriptions.getTechEnabledIcons(tech, civInfo, 26f)) { icons.add(icon); count++ }
        if (count > 0) texts.add(icons).left().padTop(4f).row()

        if (!available && !researched) {
            val missing = tech.prerequisites.filter { !civTech.isResearched(it) }
            if (missing.isNotEmpty())
                texts.add(("{Requires}: " + missing.joinToString(", ") { it.tr() }).toLabel(fontSize = 13, fontColor = Color(1f, 0.75f, 0.5f, 1f), alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left().padTop(2f).row()
        }
        if (isSelected) {
            val description = tech.getDescription(civInfo)
            if (description.isNotEmpty())
                texts.add(description.toLabel(fontSize = 14, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
        }
        card.add(texts).growX()
        if (researched) card.add(ImageGetter.getImage("OtherIcons/Checkmark").apply { color = Color(0.4f, 0.86f, 0.45f, 1f) }).size(20f).padLeft(8f)

        card.onClick {
            selected = if (isSelected) null else tech
            updatePath()
            rebuild()
        }
        return card
    }

    private fun rebuildBottom() {
        bottomTable.clear()
        val tech = selected
        val canPick = tech != null && GUI.isAllowedChangeState() && when {
            freeTechPick -> tech.name in researchable
            else -> path.isNotEmpty() && path.all { t -> t.uniqueObjects.none { u -> u.type == com.unciv.models.ruleset.unique.UniqueType.OnlyAvailable && !u.conditionalsApply(civInfo.state) || u.type == com.unciv.models.ruleset.unique.UniqueType.Unavailable && u.conditionalsApply(civInfo.state) } }
        }
        val button = Table()
        button.pad(12f)
        button.background = bg("Primary", if (canPick) primaryColor else rowDim)
        val text = when {
            tech == null -> if (freeTechPick) "Pick a free tech" else "Pick a tech"
            freeTechPick && canPick -> "Pick [${tech.name}] as free tech"
            canPick -> {
                val progress = path.sumOf { civTech.researchOfTech(it.name) } + civTech.getOverflowScience()
                val cost = path.sumOf { civTech.costOfTech(it.name) }
                "Research [${path.first().name}]".tr() + (if (path.size > 1) "  (${path.size})" else "") + "  ($progress/$cost)"
            }
            else -> "Unavailable"
        }
        button.add(text.toLabel(fontSize = 19, fontColor = if (canPick) Color.WHITE else muted, hideIcons = true).apply { wrap = true; setAlignment(Align.center) }).growX()
        if (canPick) {
            button.touchable = Touchable.enabled
            button.onClick(UncivSound.Paper) { confirm() }
        }
        bottomTable.add(button).growX().minHeight(56f)
    }

    private fun confirm() {
        val tech = selected ?: return
        Gdx.input.inputProcessor = null
        if (freeTechPick) {
            if (tech.name !in researchable) return
            civTech.getFreeTechnology(tech.name)
        } else {
            civTech.techsToResearch = ArrayList(path.map { it.name })
        }
        civTech.updateResearchProgress()
        game.settings.addCompletedTutorialTask("Pick technology")
        game.popScreen()
    }

    override fun getCivilopediaRuleset() = ruleset
    /** On a rotation the factory decides again: landscape gets the tree back */
    override fun recreate(): BaseScreen = TechPickerScreen.create(civInfo, selected)
}
