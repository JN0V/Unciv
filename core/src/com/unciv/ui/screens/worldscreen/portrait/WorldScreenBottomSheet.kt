package com.unciv.ui.screens.worldscreen.portrait

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup.Companion.addContextMenu
import com.unciv.ui.popups.UnitUpgradeMenu
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnButton
import com.unciv.ui.screens.worldscreen.unit.UnitTable
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions

/**
 *  Portrait-layout bottom sheet of the [WorldScreen]: one panel spanning the full width that holds
 *  the selected unit/city ([UnitTable]), the most used unit actions as big buttons, a "More" button
 *  opening the complete action list ([UnitActionsMenu]), and the next turn button.
 *
 *  Replaces [com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsTable] and the floating
 *  [NextTurnButton] position when [com.unciv.models.metadata.GameSettings.usePortraitLayout] is true.
 */
class WorldScreenBottomSheet(
    private val worldScreen: WorldScreen,
    private val unitTable: UnitTable,
    private val nextTurnButton: NextTurnButton
) : Table() {
    companion object {
        /** Actions shown as big buttons before the "More" button */
        private const val primaryActionCount = 3
        private const val buttonHeight = 52f
        private const val actionFontSize = 16
    }

    private val actionsRow = Table()
    private val statusRow = Table()
    private val minimapToggle = Button(BaseScreen.skin)
    private var openMore: (() -> Unit)? = null
    private var shownForUnitHash = 0
    private var shownActionCount = -1

    init {
        touchable = Touchable.enabled
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/BottomSheet",
            BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
            BaseScreen.skinStrings.skinConfig.baseColor.darken(0.6f)
        )
        pad(6f, 8f, 8f, 8f)
        defaults().pad(3f)

        actionsRow.defaults().uniformX().minHeight(buttonHeight).pad(2f)

        minimapToggle.add(ImageGetter.getImage("OtherIcons/Hexagon")).size(24f).pad(12f)
        minimapToggle.onClick {
            val settings = UncivGame.Current.settings
            settings.showMinimap = !settings.showMinimap
            settings.save()
            worldScreen.shouldUpdate = true
        }
        statusRow.defaults().pad(2f)
        statusRow.add(minimapToggle).size(buttonHeight)
        statusRow.add(nextTurnButton).growX().minHeight(buttonHeight).padLeft(8f)

        add(unitTable).center().row()
        add(actionsRow).growX().row()
        add(statusRow).growX()
    }

    /** Rebuilds the action buttons for [unit] (or clears them) and re-lays the sheet out at the bottom of the stage. */
    fun update(unit: MapUnit?) {
        val actions = if (unit != null && worldScreen.canChangeState)
            UnitActions.getUnitActions(unit).sortedByDescending { it.useFrequency }.toList()
        else emptyList()

        val newHash = unit?.hashCode() ?: 0
        if (newHash != shownForUnitHash || actions.size != shownActionCount) {
            shownForUnitHash = newHash
            shownActionCount = actions.size
            rebuildActions(unit, actions)
        }

        nextTurnButton.pack()
        invalidateHierarchy()
        pack()
        setSize(stage.width, prefHeight)
        setPosition(0f, 0f)
        layout()
    }

    private fun rebuildActions(unit: MapUnit?, actions: List<UnitAction>) {
        actionsRow.clear()
        keyShortcuts.clear()
        openMore = null
        if (unit == null || actions.isEmpty()) return

        // When only one more action than the primary slots exists, show it instead of a "More" button
        val primaryCount = if (actions.size <= primaryActionCount + 1) actions.size else primaryActionCount
        val columns = primaryCount + (if (actions.size > primaryCount) 1 else 0)
        val cellWidth = (worldScreen.stage.width - padLeft - padRight) / columns - 4f
        for (unitAction in actions.take(primaryCount))
            actionsRow.add(getActionButton(unit, unitAction, cellWidth)).width(cellWidth)

        if (actions.size > primaryCount) {
            val remaining = actions.size - primaryCount
            val moreButton = buildStackedButton(ImageGetter.getImage("OtherIcons/ArrowRight"), "{More} ($remaining)", Color.WHITE, cellWidth)
            openMore = { UnitActionsMenu(worldScreen.stage, moreButton, unit, actions) { activateAction(it, unit) } }
            moreButton.onClick { openMore?.invoke() }
            actionsRow.add(moreButton).width(cellWidth)
        }

        // Every action stays reachable by keyboard, shown or not
        for (unitAction in actions) {
            if (unitAction.action == null) continue
            keyShortcuts.add(unitAction.type.binding) { activateAction(unitAction, unit) }
        }
    }

    /** Opens the full action list, as the "More" button does (no-op when there is none) */
    fun openMoreMenu() = openMore?.invoke()

    /** Icon above a single ellipsized text line, so any number of columns fits the sheet width */
    private fun buildStackedButton(icon: Actor, text: String, fontColor: Color, width: Float): Button {
        val button = Button(BaseScreen.skin)
        button.add(icon).size(24f).padTop(4f).row()
        val label = text.toLabel(fontColor, actionFontSize - 2, hideIcons = true)
        label.setEllipsis("…")
        label.setAlignment(Align.center)
        button.add(label).width(width - 12f).padBottom(2f)
        return button
    }

    private fun getActionButton(unit: MapUnit, unitAction: UnitAction, width: Float): Button {
        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        val button = buildStackedButton(unitAction.getIcon(24f), unitAction.title, fontColor, width)
        if (unitAction.type == UnitActionType.Promote && unitAction.action != null)
            button.color = Color.GREEN.brighten(0.5f)

        if (unitAction is UpgradeUnitAction) {
            // Same trick as UnitActionsTable: the upgrade menu is useful even when upgrading is not possible
            button.isDisabled = false
            button.touchable = Touchable.enabled
            button.addContextMenu {
                UnitUpgradeMenu(worldScreen.stage, button, unit, unitAction, enable = unitAction.action != null, callbackAfterAnimation = true) {
                    worldScreen.shouldUpdate = true
                }
            }
        }

        if (unitAction.action == null) button.disable()
        else button.onActivation(unitAction.uncivSound) { activateAction(unitAction, unit) }
        return button
    }

    private fun activateAction(unitAction: UnitAction, unit: MapUnit) {
        unitAction.action!!.invoke()
        worldScreen.shouldUpdate = true
        worldScreen.mapHolder.removeUnitActionOverlay()
        if (!UncivGame.Current.settings.autoUnitCycle) return
        if (unit.isDestroyed ||
            unitAction.type.isSkippingToNextUnit && (!unit.isMoving() || !unit.hasMovement()))
            worldScreen.switchToNextUnit()
        else unitTable.shouldUpdate = true
    }
}
