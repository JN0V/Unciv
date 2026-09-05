package com.unciv.ui.screens.worldscreen.portrait

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.popups.ScrollableAnimatedMenuPopup

/**
 *  The complete, scrollable list of a unit's actions, one full-width row per action.
 *  Opened from the "More" button of [WorldScreenBottomSheet].
 */
class UnitActionsMenu(
    stage: Stage,
    anchor: Actor,
    private val unit: MapUnit,
    private val actions: List<UnitAction>,
    private val activate: (UnitAction) -> Unit
) : ScrollableAnimatedMenuPopup(stage, anchor, Align.topLeft) {

    override fun createScrollableContent(): Table {
        val table = Table()
        // Comfortable full-width rows on a phone, capped so a tablet does not get a giant menu
        val rowWidth = (stageToShowOn.width - 48f).coerceAtMost(440f)
        table.defaults().width(rowWidth).minHeight(52f).pad(2f)
        table.add(unit.displayName().toLabel(fontSize = 18)).padBottom(6f).row()
        for (unitAction in actions) {
            val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
            val button = IconTextButton(unitAction.title, unitAction.getIcon(), 17, fontColor)
            button.labelCell.padTop(0f).expandX().left()
            if (unitAction.action == null) button.disable()
            else button.onActivation(unitAction.uncivSound, unitAction.type.binding) {
                close()
                activate(unitAction)
            }
            table.add(button).row()
        }
        return table
    }

    override fun createFixedContent(): Table? = null

    override fun maxPopupWidth() = stageToShowOn.width - 16f
    override fun maxPopupHeight() = 0.75f * stageToShowOn.height
}
