package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.UncivTooltip.Companion.removeTooltips
import com.badlogic.gdx.graphics.Color
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.setFontSize
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup.Companion.addContextMenu
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnAction.Default
import com.unciv.utils.Concurrency
import yairm210.purity.annotations.Readonly

class NextTurnButton(
    private val worldScreen: WorldScreen
) : IconTextButton("", null, 30) {
    private var nextTurnAction = Default
    /** Portrait layout: an action the game requires before the turn can end (pick construction, tech...), shown as a to-do by the bottom sheet */
    var pendingAction: NextTurnAction? = null
        private set
    private val nonBlockingActions = setOf(Default, NextTurnAction.NextTurn, NextTurnAction.Working, NextTurnAction.Waiting,
        NextTurnAction.AutoPlay, NextTurnAction.RetryUpload, NextTurnAction.NextUnit, NextTurnAction.MoveAutomatedUnits)
    private val unitsDueLabel = Label("", BaseScreen.skin)
    private val unitsDueCell: Cell<Label>

    init {
        pad(15f)
        onActivation { nextTurnAction.action(worldScreen) }
        addContextMenu { NextTurnMenu(stage, this, worldScreen) }
        keyShortcuts.add(KeyboardBinding.NextTurn)
        keyShortcuts.add(KeyboardBinding.NextTurnAlternate)
        labelCell.row()
        unitsDueCell = add(unitsDueLabel).padTop(6f).colspan(2).center()
    }

    fun update() {
        nextTurnAction = getNextTurnAction(worldScreen)
        updateButton(nextTurnAction)
        val autoPlay = worldScreen.autoPlay
        if (autoPlay.shouldContinueAutoPlaying() && worldScreen.isPlayersTurn
            && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning()) {
            autoPlay.runAutoPlayJobInNewThread("MultiturnAutoPlay", worldScreen, false) {
                TurnManager(worldScreen.selectedGameView.civView.getCiv()).automateTurn()
                Concurrency.runOnGLThread { worldScreen.nextTurn() }
                autoPlay.endTurnMultiturnAutoPlay()
            }
        }

        isEnabled = nextTurnAction.getText(worldScreen) == "AutoPlay"
            || ((worldScreen.isPlayersTurn || worldScreen.failedUpload) && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning())
        if (isEnabled) {
            addTooltip(KeyboardBinding.NextTurn)
        } else {
            removeTooltips()
        }

        worldScreen.smallUnitButton.update()
    }

    internal fun updateButton(nextTurnAction: NextTurnAction) {
        label.setText(nextTurnAction.getText(worldScreen).tr())
        label.color = if (worldScreen.portraitLayout) Color.WHITE else nextTurnAction.color
        if (worldScreen.portraitLayout) {
            // One stable, green "next turn" button; a required action is listed separately by the sheet
            pendingAction = if (nextTurnAction in nonBlockingActions) null else nextTurnAction
            // A blocking action (pick a tech, a construction...) turns the button orange and says what the tap opens
            if (pendingAction != null) label.setText("Before ending: [${nextTurnAction.getText(worldScreen).tr()}]".tr())
            val tint = when {
                pendingAction != null -> colorFromRGB(160, 90, 15)
                nextTurnAction == NextTurnAction.Working || nextTurnAction == NextTurnAction.Waiting -> colorFromRGB(37, 43, 62)
                nextTurnAction == NextTurnAction.AutoPlay || nextTurnAction == NextTurnAction.RetryUpload -> nextTurnAction.color.cpy().lerp(Color.BLACK, 0.5f)
                else -> colorFromRGB(31, 126, 55)
            }
            val upDrawable = BaseScreen.skinStrings.getUiBackground("WorldScreen/Portrait/NextTurnButton", BaseScreen.skinStrings.roundedEdgeRectangleShape, tint)
            val downDrawable = BaseScreen.skinStrings.getUiBackground("WorldScreen/Portrait/NextTurnButtonPressed", BaseScreen.skinStrings.roundedEdgeRectangleShape, tint.cpy().lerp(Color.BLACK, 0.3f))
            // (locals deliberately not named up/down: inside apply{} the style's own members would shadow them)
            style = ButtonStyle(style).apply { up = upDrawable; down = downDrawable; over = upDrawable; disabled = downDrawable; checked = upDrawable }
            // Long action names ("Pick construction", "Waiting for other players...") must still fit the sheet
            val text = label.text.toString()
            label.setFontSize(if (text.length > 16) 17 else 22)
            label.setEllipsis("…")
            labelCell.width(minOf(label.prefWidth, worldScreen.stage.width - 215f)).minWidth(0f)
        }
        val iconName = if (worldScreen.portraitLayout && pendingAction != null) "OtherIcons/ExclamationMark" else nextTurnAction.icon
        if (iconName != null && ImageGetter.imageExists(iconName))
            iconCell.setActor(ImageGetter.getImage(iconName).apply {
                setSize(30f)
                color = if (worldScreen.portraitLayout) Color.WHITE else nextTurnAction.color
            })
        else
            iconCell.clearActor()

        nextTurnAction.getSubText(worldScreen)?.let {
            unitsDueLabel.setText(it.tr())
            unitsDueCell.setActor(unitsDueLabel)
        } ?: unitsDueCell.clearActor()

        pack()
    }

    private fun getNextTurnAction(worldScreen: WorldScreen) =
        // Guaranteed to return a non-null NextTurnAction because the last isChoice always returns true
        NextTurnAction.entries.first {
            it.isChoice(worldScreen) && !(worldScreen.portraitLayout && it == NextTurnAction.NextUnit)
        }

    @Readonly fun isNextUnitAction(): Boolean = nextTurnAction == NextTurnAction.NextUnit

}
