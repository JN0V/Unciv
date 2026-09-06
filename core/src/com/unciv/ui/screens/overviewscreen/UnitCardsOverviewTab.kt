package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitActionType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onClickSuppressive
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.view.CivView

/**
 *  Phone layout of the Units overview: one card per unit (icon, name, what it is doing, strength, moves, health,
 *  promotions, nearest city). Tap a card to jump to the unit on the map; a promotion badge opens the picker.
 */
class UnitCardsOverviewTab(
    viewingPlayer: CivView,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen, persistedData) {

    private val cardColor = Color(0.2f, 0.3f, 0.5f, 0.55f)
    private val civilianColor = Color(0.2f, 0.4f, 0.35f, 0.55f)
    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)
    private val green = Color(0.4f, 0.86f, 0.45f, 1f)
    private val warn = Color(1f, 0.75f, 0.5f, 1f)

    init {
        top()
        defaults().growX().pad(4f, 8f, 4f, 8f)
        val civ = viewingPlayer.getCiv()
        val units = civ.units.getCivUnits().toList()
        val military = units.filter { it.isMilitary() }.sortedBy { it.displayName().tr(hideIcons = true) }
        val civilian = units.filter { !it.isMilitary() }.sortedBy { it.displayName().tr(hideIcons = true) }

        val supply = civ.stats.getUnitSupply()
        val used = civ.units.getCivUnitsSize()
        val summary = "{Units}: [$used]".tr() + "  ·  " + "{Unit Supply}: [$supply]".tr()
        add(summary.toLabel(fontSize = 15, fontColor = if (civ.stats.getUnitSupplyDeficit() > 0) warn else muted, alignment = Align.left)).left().padTop(6f).row()
        if (civ.stats.getUnitSupplyDeficit() > 0)
            add("Your units are above supply: production is reduced".toLabel(fontSize = 14, fontColor = warn, alignment = Align.left).apply { wrap = true }).left().row()

        if (military.isNotEmpty()) {
            add("Military".toLabel(fontSize = 16, fontColor = muted, alignment = Align.left)).left().padTop(10f).row()
            for (unit in military) add(unitCard(unit, cardColor)).row()
        }
        if (civilian.isNotEmpty()) {
            add("Civilian".toLabel(fontSize = 16, fontColor = muted, alignment = Align.left)).left().padTop(10f).row()
            for (unit in civilian) add(unitCard(unit, civilianColor)).row()
        }
        if (units.isEmpty()) add("No units".toLabel(fontColor = muted)).pad(20f).row()
    }

    private fun unitCard(unit: MapUnit, color: Color): Table {
        val card = Table()
        card.background = BaseScreen.skinStrings.getUiBackground("OverviewScreen/Portrait/UnitCard", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, color)
        card.pad(8f, 12f, 8f, 12f)
        card.touchable = Touchable.enabled
        card.name = "unit-${unit.id}"

        card.add(ImageGetter.getUnitIcon(unit.baseUnit, Color.WHITE)).size(36f).padRight(10f).top()

        val texts = Table()
        // Name and what it is doing
        val title = Table()
        title.add(unit.displayName().toLabel(fontSize = 18, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left()
        val action = actionText(unit)
        if (action != null) title.add(action.toLabel(fontSize = 13, fontColor = muted, hideIcons = true)).padLeft(8f).top()
        texts.add(title).growX().row()

        // Numbers: strength, ranged, moves, health, XP
        val numbers = ArrayList<String>()
        if (unit.baseUnit.strength > 0) numbers.add("${unit.baseUnit.strength}${Fonts.strength}")
        if (unit.baseUnit.rangedStrength > 0) numbers.add("${unit.baseUnit.rangedStrength}${Fonts.rangedStrength}")
        numbers.add("${unit.getMovementString()}${Fonts.movement}")
        if (unit.health < 100) numbers.add("${unit.health}/100 HP")
        if (!unit.isCivilian()) numbers.add("XP ${unit.promotions.XP}/${unit.promotions.xpForNextPromotion()}")
        texts.add(numbers.joinToString("   ").toLabel(fontSize = 14, fontColor = if (unit.health < 100) warn else muted, alignment = Align.left)).left().padTop(2f).row()

        // Where
        val cityTile = unit.getTile().getTilesInDistance(3).firstOrNull { it.isCityCenter() }
        if (cityTile != null) {
            val here = unit.getTile() == cityTile
            val where = (if (here) "{In} " else "{Near} ").tr() + cityTile.getCity()!!.name.tr(hideIcons = true)
            texts.add(where.toLabel(fontSize = 14, fontColor = if (here) green else muted, alignment = Align.left)).left().padTop(2f).row()
        }

        // Promotions: icons, plus a badge when one can be picked
        val promotions = Table()
        promotions.defaults().padRight(4f)
        for (promotion in unit.promotions.getPromotions(sorted = true))
            promotions.add(ImageGetter.getPromotionPortrait(promotion.name, 22f))
        val canPromote = unit.promotions.canBePromoted() && viewingPlayer.getCiv().isCurrentPlayer() && GUI.isAllowedChangeState()
        if (canPromote) {
            val badge = Table()
            badge.background = BaseScreen.skinStrings.getUiBackground("OverviewScreen/Portrait/PromoteBadge", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, Color(0.12f, 0.5f, 0.22f, 1f))
            badge.pad(3f, 8f, 3f, 8f)
            badge.add("Promote".toLabel(fontSize = 13))
            badge.touchable = Touchable.enabled
            badge.onClickSuppressive {
                overviewScreen.game.pushScreen { PromotionPickerScreen(unit) { overviewScreen.select(EmpireOverviewCategories.Units, unit.id.toString()) } }
            }
            promotions.add(badge).padLeft(4f)
        }
        if (promotions.hasChildren()) texts.add(promotions).left().padTop(4f).row()

        card.add(texts).growX()
        card.add(ImageGetter.getImage("OtherIcons/ForwardArrow").apply { this.color = muted }).size(16f).padLeft(8f)
        card.onClick {
            GUI.resetToWorldScreen()
            GUI.getMap().setCenterPosition(unit.currentTile.position, forceSelectUnit = unit)
        }
        return card
    }

    private fun actionText(unit: MapUnit): String? {
        val improvement = unit.currentTile.improvementInProgress
        return when {
            unit.action == null && unit.cache.hasUniqueToBuildImprovements && improvement != null && unit.hasMovement() -> improvement
            unit.action == null -> null
            unit.isFortified() -> UnitActionType.Fortify.value
            unit.isGuarding() -> UnitActionType.Guard.value
            unit.isMoving() -> "Moving"
            unit.isAutomated() -> UnitActionType.Automate.value
            else -> unit.action
        }
    }

    override fun select(selection: String): Float? {
        val card = findActor<Table>("unit-$selection") ?: return null
        return card.y
    }
}
