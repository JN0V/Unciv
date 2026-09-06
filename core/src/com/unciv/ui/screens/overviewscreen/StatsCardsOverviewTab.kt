package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.CivView
import kotlin.math.roundToInt

/** Phone layout of the Stats overview: one card per stat, stacked; each card lists where the stat comes from and the total. */
class StatsCardsOverviewTab(
    viewingPlayer: CivView,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    private val cardColor = Color(0.2f, 0.3f, 0.5f, 0.55f)
    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        overviewScreen.game.settings.addCompletedTutorialTask("See your stats breakdown")
        super.activated(index, caption, pager)
    }

    init {
        top()
        defaults().growX().pad(4f, 8f, 4f, 8f)
        val statMap = viewingPlayer.getStatMapForNextTurn()

        val happiness = viewingPlayer.getHappinessBreakdown()
        add(card(ImageGetter.getStatIcon("Happiness"), "Happiness", happiness.values.sum(), happiness.entries.map { it.key to it.value })).row()

        for (stat in listOf(Stat.Gold, Stat.Science, Stat.Culture, Stat.Faith)) {
            if (stat == Stat.Faith && !viewingPlayer.isReligionEnabled()) continue
            val rows = statMap.map { (source, stats) -> source to stats[stat] }
            add(card(ImageGetter.getStatIcon(stat.name), stat.name, rows.sumOf { it.second.toDouble() }.toFloat(), rows)).row()
        }

        // Great people: current / needed, and per turn
        val greatPeople = Table()
        greatPeople.defaults().left().pad(2f)
        val points = viewingPlayer.getGreatPersonPointsCounter()
        val perTurn = viewingPlayer.getGreatPersonPointsForNextTurn()
        for ((person, current) in points) {
            greatPeople.add(person.toLabel(fontSize = 15, hideIcons = true)).left().growX()
            greatPeople.add("$current/${viewingPlayer.getPointsRequiredForGreatPerson(person)}".toLabel(fontSize = 15)).right().padLeft(8f)
            greatPeople.add("+${perTurn[person]}".toLabel(fontSize = 15, fontColor = muted)).right().padLeft(8f).row()
        }
        for ((unit, current) in viewingPlayer.getGreatGeneralPointsCounter()) {
            greatPeople.add(unit.toLabel(fontSize = 15, hideIcons = true)).left().growX()
            greatPeople.add("$current/${viewingPlayer.getPointsForNextGreatGeneralCounter()[unit]}".toLabel(fontSize = 15)).right().padLeft(8f).row()
        }
        if (greatPeople.hasChildren())
            add(card(ImageGetter.getStatIcon("Specialist").apply { color = Color.ROYAL }, "Great person points", null, emptyList(), greatPeople)).row()

        val score = viewingPlayer.calculateScoreBreakdown()
        add(card(ImageGetter.getImage("OtherIcons/Score").apply { color = Color.FIREBRICK }, "Score", score.values.sum().toFloat(), score.map { it.key to it.value.toFloat() })).row()
    }

    private fun card(icon: Actor, title: String, total: Float?, rows: List<Pair<String, Float>>, body: Table? = null): Table {
        val card = Table()
        card.background = BaseScreen.skinStrings.getUiBackground("OverviewScreen/Portrait/StatCard", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, cardColor)
        card.pad(10f, 12f, 10f, 12f)
        val header = Table()
        header.add(icon).size(24f).padRight(8f)
        header.add(title.toLabel(fontSize = 19, alignment = Align.left, hideIcons = true)).expandX().left()
        if (total != null) header.add(formatValue(total).toLabel(fontSize = 19)).padLeft(8f)
        card.add(header).growX().row()
        val list = body ?: Table().also { table ->
            table.defaults().pad(2f)
            for ((label, value) in rows) {
                val rounded = value.roundToInt()
                if (rounded == 0) continue
                table.add(label.toLabel(fontSize = 15, fontColor = muted, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left()
                table.add(formatValue(value).toLabel(fontSize = 15, fontColor = if (rounded < 0) Color(1f, 0.6f, 0.6f, 1f) else Color.WHITE)).right().padLeft(8f).row()
            }
        }
        if (list.hasChildren()) card.add(list).growX().padTop(6f).row()
        return card
    }

    private fun formatValue(value: Float): String {
        val rounded = value.roundToInt()
        return (if (rounded > 0) "+" else "") + rounded.tr()
    }
}
