package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.ui.components.extensions.getTurnsToConstructionString
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup.Companion.addContextMenu
import com.unciv.ui.popups.CityScreenConstructionMenu
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 *  Phone layout of the [CityScreen]: header (back, name, growth, city paging), a stats row,
 *  the city map in the upper part, then tabs (Build / Tiles / Citizens / Info) over a scrollable panel.
 *
 *  Reuses the classic widgets where they are self-contained ([CityScreenTileTable], [CitizenManagementTable],
 *  [SpecialistAllocationTable], [CityStatsTable]) and rebuilds the construction lists as full-width rows:
 *  one tap on an available construction adds it to the queue, long press opens the classic context menu.
 */
class CityScreenPortrait(
    private val cityScreen: CityScreen,
    private val constructionsTable: CityConstructionsTable,
    private val cityStatsTable: CityStatsTable,
    private val tileTable: CityScreenTileTable,
    private val razeCityButtonHolder: Table
) : Table() {
    enum class Tab(val title: String) { Build("Build"), Tiles("Tiles"), Citizens("Citizens"), Info("Info") }

    private val cityView get() = cityScreen.cityView
    private val screenStage get() = cityScreen.stage

    private val header = Table()
    private val statsRow = Table()
    private val tabsRow = Table()
    private val content = Table()
    private val contentScroll = AutoScrollPane(content)
    private val buyButtonFactory = BuyButtonFactory(cityScreen)

    var activeTab = if (cityScreen.isSpying) Tab.Tiles else Tab.Build
        private set
    /** Y (stage coordinates) of the map area's bottom edge, so the [CityScreen] can size its map pane */
    val mapBottom: Float get() = screenStage.height - headerHeight - mapHeight
    private val headerHeight = 132f
    private val mapHeight = (screenStage.height * 0.34f).coerceAtLeast(260f)

    private val panelColor = Color(0.03f, 0.05f, 0.24f, 0.96f)
    private val rowColor = Color(0.2f, 0.3f, 0.5f, 0.55f)
    private val rowColorDim = Color(0.15f, 0.17f, 0.24f, 0.8f)
    private val accentGreen = colorFromRGB(31, 126, 55)

    init {
        setFillParent(true)
        touchable = Touchable.childrenOnly  // the map area in the middle must receive touches
        top()

        header.background = bg("Header", panelColor)
        header.pad(6f, 8f, 6f, 8f)
        statsRow.background = bg("StatsRow", panelColor)
        statsRow.pad(4f, 8f, 6f, 8f)
        tabsRow.background = bg("Tabs", panelColor)
        tabsRow.pad(4f)
        tabsRow.defaults().growX().uniformX().minHeight(44f).pad(2f)
        content.top().pad(6f, 8f, 8f, 8f)
        content.defaults().growX().pad(2f)
        contentScroll.setScrollingDisabled(true, false)

        add(header).growX().row()
        add(statsRow).growX().row()
        add().height(mapHeight).row()
        add(tabsRow).growX().row()
        val contentTable = Table()
        contentTable.background = bg("Content", panelColor)
        contentTable.add(contentScroll).grow()
        add(contentTable).grow().row()
    }

    private fun bg(part: String, color: Color) = BaseScreen.skinStrings.getUiBackground(
        "CityScreen/Portrait/$part", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, color)

    fun update() {
        updateHeader()
        updateStats()
        updateTabs()
        updateContent()
    }

    // ---------------------------------------------------------------- header + stats

    private fun updateHeader() {
        header.clear()
        val civView = cityView.owningCiv()

        val backButton = Button(BaseScreen.skin)
        backButton.add(ImageGetter.getImage("OtherIcons/BackArrow")).size(22f).pad(9f)
        backButton.onClick { cityScreen.exit() }
        header.add(backButton).size(44f).padRight(8f)

        val nameTable = Table()
        val nameRow = Table()
        if (cityView.isCapital()) nameRow.add(ImageGetter.getImage("OtherIcons/Star").apply { color = Color.LIGHT_GRAY }).size(18f).padRight(4f)
        if (cityView.isPuppet()) nameRow.add(ImageGetter.getImage("OtherIcons/Puppet").apply { color = Color.LIGHT_GRAY }).size(18f).padRight(4f)
        if (cityView.isBeingRazed()) nameRow.add(ImageGetter.getImage("OtherIcons/Fire")).size(18f).padRight(4f)
        val nameLabel = cityView.name.toLabel(fontSize = 22, hideIcons = true)
        nameLabel.setEllipsis("…")
        nameRow.add(nameLabel).minWidth(0f).left()
        nameTable.add(nameRow).left().row()

        val subtitle = buildString {
            append("{Population}: ".tr()).append(cityView.getPopulationCount().tr())
            val growth = when {
                cityView.isStarving() -> "[${cityView.getNumTurnsToStarvation()}] turns to lose population"
                cityView.isGrowing() -> "[${cityView.getNumTurnsToNewPopulation()}] turns to new population"
                else -> "Stopped population growth"
            }.tr()
            append(" · ").append(growth)
        }
        nameTable.add(subtitle.toLabel(fontSize = 14, fontColor = Color.LIGHT_GRAY).apply { setEllipsis("…") }).minWidth(0f).left()
        header.add(nameTable).growX().minWidth(0f)

        if (cityScreen.viewableCities.size > 1) {
            for ((delta, rotation) in listOf(-1 to 0f, 1 to 180f)) {
                val button = Button(BaseScreen.skin)
                val image = ImageGetter.getImage("OtherIcons/BackArrow")
                image.setSize(20f, 20f); image.setOrigin(Align.center); image.rotation = rotation
                image.color = civView.getInnerColor()
                button.add(image).size(20f).pad(10f)
                button.onClick { cityScreen.page(delta) }
                header.add(button).size(44f).padLeft(6f)
            }
        }
    }

    private fun updateStats() {
        statsRow.clear()
        val stats = cityView.getCurrentCityStats()
        val shown = listOf(Stat.Food, Stat.Production, Stat.Gold, Stat.Science, Stat.Culture) +
            (if (cityView.viewingCiv().isReligionEnabled()) listOf(Stat.Faith) else emptyList()) + Stat.Happiness
        for (stat in shown) {
            val cell = Table()
            cell.add(ImageGetter.getStatIcon(stat.name)).size(18f).padRight(3f)
            val value = stats[stat]
            val text = if (stat == Stat.Food) (if (value >= 0) "+" else "") + value.toInt().tr() else value.toInt().tr()
            cell.add(text.toLabel(fontSize = 16, fontColor = if (stat == Stat.Food && value < 0) Color.RED else Color.WHITE))
            cell.touchable = Touchable.enabled
            cell.onClick { selectTab(Tab.Info) }
            statsRow.add(cell).expandX()
        }
    }

    // ---------------------------------------------------------------- tabs

    private fun updateTabs() {
        tabsRow.clear()
        val tabs = if (cityScreen.isSpying) listOf(Tab.Tiles, Tab.Info) else Tab.entries
        for (tab in tabs) {
            val button = Button(BaseScreen.skin)
            button.add(tab.title.toLabel(fontSize = 15))
            if (tab == activeTab) button.style = tabStyle(button.style, colorFromRGB(57, 152, 219))
            else button.style = tabStyle(button.style, Color(0.2f, 0.3f, 0.5f, 0.4f))
            button.onClick { selectTab(tab) }
            tabsRow.add(button)
        }
    }

    private fun tabStyle(base: Button.ButtonStyle, tint: Color): Button.ButtonStyle {
        val drawable = BaseScreen.skinStrings.getUiBackground("CityScreen/Portrait/Tab", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, tint)
        return Button.ButtonStyle(base).apply { up = drawable; down = drawable; over = drawable; checked = drawable }
    }

    fun selectTab(tab: Tab) {
        if (activeTab == tab) return
        activeTab = tab
        updateTabs()
        updateContent()
    }

    /** Called by [CityScreen] when a map tile was tapped: bring its info into view */
    fun onTileSelected() {
        if (cityScreen.selectedTile != null && activeTab != Tab.Tiles) selectTab(Tab.Tiles)
    }

    // ---------------------------------------------------------------- content

    private fun updateContent() {
        content.clear()
        when (activeTab) {
            Tab.Build -> updateBuildContent()
            Tab.Tiles -> updateTilesContent()
            Tab.Citizens -> updateCitizensContent()
            Tab.Info -> updateInfoContent()
        }
        content.pack()
        contentScroll.layout()
    }

    private fun sectionLabel(text: String) = text.toLabel(fontSize = 14, fontColor = Color.LIGHT_GRAY).apply { setAlignment(Align.left) }

    private fun updateBuildContent() {
        val queue = cityView.constructions.constructionQueue
        content.add(sectionLabel("{Construction queue}: ".tr() + queue.size.tr())).left().padTop(4f).row()
        if (queue.isEmpty())
            content.add("Pick a construction".toLabel(fontColor = Color.LIGHT_GRAY)).padBottom(6f).row()
        for ((index, name) in queue.withIndex()) {
            content.add(queueRow(index, name)).row()
            if (index == constructionsTable.selectedQueueEntry) {
                val buyRow = Table()
                buyRow.defaults().pad(2f).growX().minHeight(44f)
                for (button in buyButtonFactory.getBuyButtons(cityScreen.selectedConstruction)) buyRow.add(button)
                if (buyRow.hasChildren()) content.add(buyRow).padBottom(4f).row()
            }
        }

        val dtos = constructionsTable.getConstructionButtonDTOs()
        fun category(c: IConstruction) = when {
            c is BaseUnit -> "Units"
            c is Building && (c.isWonder || c.isNationalWonder) -> "Wonders"
            c is Building -> "Buildings"
            else -> "Other"
        }
        for (cat in listOf("Units", "Buildings", "Wonders", "Other")) {
            val entries = dtos.filter { category(it.construction) == cat }
            if (entries.isEmpty()) continue
            content.add(sectionLabel(cat.tr())).left().padTop(10f).row()
            for (dto in entries) content.add(availableRow(dto)).row()
        }
    }

    private fun rowTable(dim: Boolean = false): Table {
        val row = Table()
        row.background = BaseScreen.skinStrings.getUiBackground("CityScreen/Portrait/Row", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, if (dim) rowColorDim else rowColor)
        row.pad(6f, 8f, 6f, 8f)
        row.touchable = Touchable.enabled
        row.defaults().minHeight(44f)
        return row
    }

    private fun queueRow(index: Int, name: String): Table {
        val constructions = cityView.constructions
        val construction = constructions.getConstruction(name)
        val selected = index == constructionsTable.selectedQueueEntry
        val row = rowTable()
        if (selected) row.background = BaseScreen.skinStrings.getUiBackground("CityScreen/Portrait/RowSelected", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, Color(0.12f, 0.5f, 0.22f, 0.6f))
        row.add(ImageGetter.getConstructionPortrait(name, 40f)).padRight(12f)
        val text = Table().left()
        text.add(name.tr(hideIcons = true).toLabel(fontSize = 17).apply { setEllipsis("…") }).minWidth(0f).left().row()
        val turns = if (name in PerpetualConstruction.perpetualConstructionsMap) com.unciv.ui.components.fonts.Fonts.infinity.toString()
            else constructions.getTurnsToConstructionString(construction, constructions.isFirstConstructionOfItsKind(index, name))
        text.add((if (index == 0) "{Current construction}".tr() + " · " else "") .plus(turns).toLabel(fontSize = 13, fontColor = Color.LIGHT_GRAY).apply { setEllipsis("…") }).minWidth(0f).left()
        row.add(text).growX().minWidth(0f).padRight(26f)
        if (cityScreen.canCityBeChanged()) row.add(constructionsTable.getRemoveFromQueueButton(index)).size(44f)
        row.onClick {
            if (constructionsTable.selectedQueueEntry == index) {
                constructionsTable.selectedQueueEntry = -1
                cityScreen.clearSelection()
            } else {
                constructionsTable.selectedQueueEntry = index
                cityScreen.selectConstructionFromQueue(index)
            }
            cityScreen.updateAsync()
        }
        if (cityScreen.canCityBeChanged()) row.addContextMenu {
            CityScreenConstructionMenu(screenStage, row, cityView, construction) { cityView.tryReassignPopulation(); cityScreen.updateAsync() }
        }
        return row
    }

    private fun availableRow(dto: ConstructionButtonDTO): Table {
        val construction = dto.construction
        val blocked = dto.rejectionReason != null
        val row = rowTable(dim = blocked)
        row.add(ImageGetter.getConstructionPortrait(construction.name, 40f).apply { if (blocked) color.a = 0.5f }).padRight(12f)
        val text = Table().left()
        val stats = if (construction is Building) " " + Stat.entries.filter { cityView.isStatRelated(it, construction) }.joinToString("") { it.character.toString() } else ""
        text.add((construction.name.tr(hideIcons = true) + stats).toLabel(fontSize = 17, fontColor = if (blocked) Color.LIGHT_GRAY else Color.WHITE, hideIcons = true).apply { setEllipsis("…") }).minWidth(0f).left().row()
        val subtitle = if (blocked && dto.rejectionReason != null) dto.rejectionReason.errorMessage.tr() else dto.buttonText
        text.add(subtitle.toLabel(fontSize = 13, fontColor = if (blocked) colorFromRGB(255, 138, 128) else Color.LIGHT_GRAY).apply { wrap = true })
            .width(screenStage.width - 170f).left()
        row.add(text).growX().minWidth(0f)
        val trailing = if (blocked) ImageGetter.getImage("OtherIcons/LockSmall").apply { color = Color.LIGHT_GRAY }
            else ImageGetter.getImage("OtherIcons/New").apply { color = accentGreen }
        row.add(trailing).size(24f).padLeft(6f).padRight(26f)  // room for the long-press indicator in the corner

        row.onClick {
            if (blocked) {
                ToastPopup(dto.rejectionReason!!.errorMessage, cityScreen)
            } else if (constructionsTable.cannotAddConstructionToQueue(construction)) {
                ToastPopup("Construction queue is full", cityScreen)
            } else {
                constructionsTable.addConstructionToQueue(construction)
                cityScreen.updateAsync()
            }
        }
        if (cityScreen.canCityBeChanged()) row.addContextMenu {
            CityScreenConstructionMenu(screenStage, row, cityView, construction) { cityView.tryReassignPopulation(); cityScreen.updateAsync() }
        }
        return row
    }

    private fun updateTilesContent() {
        val tile = cityScreen.selectedTile
        if (tile == null) {
            content.add("Tap a tile on the map to see what it yields and to work it".toLabel(fontColor = Color.LIGHT_GRAY).apply { wrap = true; setAlignment(Align.center) })
                .width(screenStage.width - 32f).padTop(20f).row()
            return
        }
        tileTable.update(tile)
        tileTable.background = null  // the classic white frame clashes with the dark panel
        content.add(tileTable).center().row()
    }

    private fun updateCitizensContent() {
        val citizens = CitizenManagementTable(cityScreen)
        citizens.update()
        content.add(citizens).center().padTop(6f).row()
        if (!cityView.getMaxSpecialists().isEmpty()) {
            content.add(sectionLabel("Specialists".tr())).left().padTop(12f).row()
            val specialists = SpecialistAllocationTable(cityScreen)
            specialists.update()
            content.add(specialists).center().row()
        }
    }

    private fun updateInfoContent() {
        fun line(text: String) = content.add(text.toLabel(fontSize = 16).apply { wrap = true }).width(screenStage.width - 32f).left().row()

        line("{Unassigned population}: ".tr() + cityView.getFreePopulation().tr() + "/" + cityView.getPopulationCount().tr())
        val stats = cityView.getCurrentCityStats()
        val expansion = if (stats.culture > 0 && cityView.hasChoosableTiles()) {
            val remaining = cityView.getCultureToNextTile() - cityView.getCultureStored()
            "[${kotlin.math.ceil(remaining / stats.culture).toInt().coerceAtLeast(1)}] turns to expansion".tr()
        } else "Stopped expansion".tr()
        line(expansion + " (${cityView.getCultureStored()}${com.unciv.ui.components.fonts.Fonts.culture}/${cityView.getCultureToNextTile()}${com.unciv.ui.components.fonts.Fonts.culture})")
        val growth = when {
            cityView.isStarving() -> "[${cityView.getNumTurnsToStarvation()}] turns to lose population"
            cityView.isGrowing() -> "[${cityView.getNumTurnsToNewPopulation()}] turns to new population"
            else -> "Stopped population growth"
        }.tr()
        line(growth + " (${cityView.getFoodStored()}${com.unciv.ui.components.fonts.Fonts.food}/${cityView.getFoodToNextPopulation()}${com.unciv.ui.components.fonts.Fonts.food})")

        val statsButton = Button(BaseScreen.skin)
        statsButton.add("Stats".toLabel(fontSize = 16)).pad(6f, 14f, 6f, 14f)
        statsButton.onClick { DetailedStatsPopup(cityScreen).open() }
        content.add(statsButton).left().padTop(6f).row()

        val buildings = cityView.getBuiltBuildings().toList()
        content.add(sectionLabel("{Buildings}: ".tr() + buildings.size.tr())).left().padTop(12f).row()
        for (building in buildings.sortedBy { it.name.tr() }) {
            val row = rowTable()
            row.add(ImageGetter.getConstructionPortrait(building.name, 32f)).padRight(12f)
            val text = Table().left()
            text.add(building.name.tr(hideIcons = true).toLabel(fontSize = 16)).left().row()
            val effect = building.getShortDescription()
            if (effect.isNotEmpty())
                text.add(effect.toLabel(fontSize = 13, fontColor = Color.LIGHT_GRAY).apply { wrap = true }).width(screenStage.width - 130f).left()
            row.add(text).growX().minWidth(0f)
            row.onClick { cityScreen.openCivilopedia(building.makeLink()) }
            content.add(row).row()
        }
        if (razeCityButtonHolder.hasChildren()) {
            content.add(sectionLabel("Other".tr())).left().padTop(12f).row()
            content.add(razeCityButtonHolder).center().padBottom(8f).row()
        }
    }
}
