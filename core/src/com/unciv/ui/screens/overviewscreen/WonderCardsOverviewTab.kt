package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.CivView

/** Phone layout of the Wonders overview: grouped cards (era / natural wonders) with image, name, status and place. */
class WonderCardsOverviewTab(
    viewingPlayer: CivView,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)
    private val green = Color(0.4f, 0.86f, 0.45f, 1f)

    init {
        top()
        defaults().growX().pad(4f, 8f, 4f, 8f)
        val wonders = WonderInfo().collectInfo(viewingPlayer.getCiv())
        var lastGroup = ""
        for (wonder in wonders) {
            if (wonder.status == WonderInfo.WonderStatus.Hidden) continue
            if (wonder.groupName != lastGroup) {
                lastGroup = wonder.groupName
                add(lastGroup.toLabel(fontSize = 16, fontColor = wonder.groupColor, alignment = Align.left)).left().padTop(10f).row()
            }
            add(card(wonder)).row()
        }
        if (!hasChildren()) add("Nothing to show yet".toLabel(fontColor = muted)).pad(20f).row()
    }

    private fun card(wonder: WonderInfo.WonderInfo): Table {
        val card = Table()
        val owned = wonder.status == WonderInfo.WonderStatus.Owned
        card.background = BaseScreen.skinStrings.getUiBackground("OverviewScreen/Portrait/WonderCard", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
            if (owned) Color(0.12f, 0.5f, 0.22f, 0.35f) else Color(0.2f, 0.3f, 0.5f, 0.45f))
        card.pad(8f, 12f, 8f, 12f)
        card.touchable = Touchable.enabled
        wonder.getImage()?.let { card.add(it).size(40f).padRight(10f) }
        val texts = Table()
        texts.add(wonder.getNameColumn().toLabel(fontSize = 18, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left().row()
        val status = wonder.getStatusColumn()
        val place = wonder.getLocationColumn()
        val line = listOf(status, place).filter { it.isNotEmpty() }.joinToString(" · ") { it.tr() }
        if (line.isNotEmpty())
            texts.add(line.toLabel(fontSize = 14, fontColor = if (owned) green else muted, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left().padTop(2f).row()
        card.add(texts).growX()
        card.onClick {
            if (wonder.location != null && wonder.status > WonderInfo.WonderStatus.NotFound) {
                val worldScreen = UncivGame.Current.resetToWorldScreen()
                worldScreen.mapHolder.setCenterPosition(wonder.location.position)
            } else overviewScreen.openCivilopedia(wonder.makeLink())
        }
        return card
    }

}
