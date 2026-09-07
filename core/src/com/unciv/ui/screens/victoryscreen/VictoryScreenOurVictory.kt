package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Victory
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenOurVictory(
    private val worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val header = Table()
    private val stageWidth = worldScreen.stage.width
    // Declared before init: Kotlin initializes in source order and init builds the columns
    private val portraitLayout = worldScreen.portraitLayout

    init {
        align(Align.top)

        val gameInfo = worldScreen.gameInfo
        val victoriesToShow = gameInfo.getEnabledVictories()

        defaults().pad(10f)
        for ((victoryName, victory) in victoriesToShow) {
            header.add("[$victoryName] Victory".toLabel()).pad(10f)
            add(getColumn(victory, worldScreen.selectedGameView.civView.getCiv())).top()
        }

        row()
        for (victory in victoriesToShow.values) {
            val victoryScreenHeaderLabel = victory.victoryScreenHeader.toLabel()
            victoryScreenHeaderLabel.wrap = true
            // Phone: the columns share the whole width instead of a fifth each (which broke words apart)
            val labelWidth = if (worldScreen.portraitLayout) (stageWidth - 40f) / victoriesToShow.size else stageWidth / 5
            add(victoryScreenHeaderLabel).width(labelWidth)
        }

        header.addSeparator(Color.GRAY)
    }

    private fun getColumn(victory: Victory, playerCiv: Civilization): Table {
        val table = Table()
        table.defaults().space(10f)
        var firstIncomplete = true
        for (milestone in victory.milestoneObjects) {
            val completionStatus = when {
                milestone.hasBeenCompletedBy(playerCiv) -> Victory.CompletionStatus.Completed
                firstIncomplete -> {
                    firstIncomplete = false
                    Victory.CompletionStatus.Partially
                }
                else -> Victory.CompletionStatus.Incomplete
            }
            for (button in milestone.getVictoryScreenButtons(completionStatus, playerCiv)) {
                if (portraitLayout) {
                    // Phone: a long goal ("Have at least 1 known major civilization (0/1)") wraps instead of leaving the screen
                    button.label.wrap = true
                    button.label.setAlignment(Align.center)
                    table.add(button).width(stageWidth - 60f).row()
                } else table.add(button).row()
            }
        }
        return table
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        equalizeColumns(header, this)
    }

    override fun getFixedContent() = header
}
