package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.City
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.view.CivView

/**
 *  Phone layout of the Cities overview: one card per city instead of a wide sortable grid.
 *  Name, population and growth, the yields, and what is being built. Tap a card to open the city.
 */
class CityCardsOverviewTab(
    viewingPlayer: CivView,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen, persistedData) {

    private val cardColor = Color(0.2f, 0.3f, 0.5f, 0.55f)

    init {
        top()
        defaults().growX().pad(4f, 8f, 4f, 8f)
        val civ = viewingPlayer.getCiv()
        val stageWidth = overviewScreen.stage.width
        for (city in civ.cities.sortedByDescending { it.population.population }) {
            add(cityCard(city, stageWidth)).row()
        }
        if (civ.cities.isEmpty())
            add("No cities yet".toLabel(fontColor = Color.LIGHT_GRAY)).pad(20f).row()
    }

    private fun cityCard(city: City, stageWidth: Float): Table {
        val card = Table()
        card.background = BaseScreen.skinStrings.getUiBackground("OverviewScreen/Portrait/CityCard", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, cardColor)
        card.pad(10f, 12f, 10f, 12f)
        card.touchable = Touchable.enabled
        card.defaults().left()

        // Title row: capital star, name, population, chevron
        val title = Table()
        if (city.isCapital()) title.add(ImageGetter.getImage("OtherIcons/Star").apply { color = Color.LIGHT_GRAY }).size(18f).padRight(4f)
        if (city.isPuppet) title.add(ImageGetter.getImage("OtherIcons/Puppet").apply { color = Color.LIGHT_GRAY }).size(18f).padRight(4f)
        if (city.isBeingRazed) title.add(ImageGetter.getImage("OtherIcons/Fire")).size(18f).padRight(4f)
        title.add(city.name.toLabel(fontSize = 20, hideIcons = true).apply { setEllipsis("…") }).minWidth(0f).growX().left()
        val popText = "{Population}: ".tr() + city.population.population.tr()
        title.add(popText.toLabel(fontSize = 14, fontColor = Color.LIGHT_GRAY)).padLeft(8f)
        title.add(ImageGetter.getImage("OtherIcons/ForwardArrow").apply { color = Color.LIGHT_GRAY }).size(16f).padLeft(8f)
        card.add(title).growX().row()

        // Yields
        val stats = city.cityStats.currentCityStats
        val yields = Table()
        yields.defaults().padRight(12f).padTop(6f)
        val shown = listOf(Stat.Food, Stat.Production, Stat.Gold, Stat.Science, Stat.Culture) +
            (if (viewingPlayer.isReligionEnabled()) listOf(Stat.Faith) else emptyList()) + Stat.Happiness
        for (stat in shown) {
            val cell = Table()
            cell.add(ImageGetter.getStatIcon(stat.name)).size(18f).padRight(3f)
            val value = stats[stat].toInt()
            val text = if (stat == Stat.Food && value > 0) "+$value" else value.tr()
            cell.add(text.toLabel(fontSize = 15, fontColor = if (stat == Stat.Food && value < 0) Color.RED else Color.WHITE))
            yields.add(cell)
        }
        card.add(yields).left().row()

        // Growth and construction
        val growth = when {
            city.isStarving() -> "[${city.population.getNumTurnsToStarvation()}] turns to lose population"
            city.isGrowing() -> "[${city.population.getNumTurnsToNewPopulation()}] turns to new population"
            else -> "Stopped population growth"
        }.tr()
        card.add(growth.toLabel(fontSize = 14, fontColor = Color.LIGHT_GRAY)).padTop(6f).row()

        val constructionName = city.cityConstructions.currentConstructionName()
        val constructionRow = Table()
        if (constructionName.isNotEmpty()) {
            constructionRow.add(ImageGetter.getConstructionPortrait(constructionName, 28f)).padRight(8f)
            val turns = city.cityConstructions.turnsToConstruction(constructionName)
            val text = "{Current construction}: ".tr() + constructionName.tr(hideIcons = true) +
                " (" + "[$turns] turns".tr() + ")"
            constructionRow.add(text.toLabel(fontSize = 14).apply { wrap = true; setAlignment(Align.left) }).width(stageWidth - 100f).left()
        } else {
            constructionRow.add(ImageGetter.getImage("OtherIcons/ExclamationMark").apply { color = Color.ORANGE }).size(22f).padRight(8f)
            constructionRow.add("Pick a construction".toLabel(fontSize = 14, fontColor = Color.ORANGE)).left()
        }
        card.add(constructionRow).padTop(6f).left().row()

        card.onClick {
            overviewScreen.game.pushScreen { CityScreen(viewingPlayer.getCity(city)) }
        }
        return card
    }
}
