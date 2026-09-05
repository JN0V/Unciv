package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.GUI
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton

class PickerPane(
    disableScroll: Boolean = false,
) : Table() {
    /** The close button on the lower left of [bottomTable], see [PickerScreen.setDefaultCloseAction].
     *  Note if you don't use that helper, you'll need to do both click and keyboard support yourself. */
    val closeButton = Constants.close.toTextButton()
    /** A scrollable wrapped Label you can use to show descriptions in the [bottomTable], starts empty */
    val descriptionLabel = DescriptionLabel()
    /** Wraps descriptionLabel - a subclass may replace the content, but beware: zero padding around this */
    internal val descriptionScroll: AutoScrollPane
    /** A wrapper containing [rightSideButton]. You can add buttons, they will be arranged vertically */
    val rightSideGroup = VerticalGroup()
    /** A button on the lower right of [bottomTable] you can use for a "OK"-type action, starts disabled */
    val rightSideButton = "".toTextButton()

    private val screenSplit = 0.85f
    private val maxBottomTableHeight = 150f     // about 7 lines of normal text

    /**
     * The table displaying the choices from which to pick (usually).
     * Also the element which most of the screen real estate is devoted to displaying.
     */
    val topTable = Table()
    /** Holds the [Close button][closeButton], a [description label][descriptionLabel] and an [action button][rightSideButton] */
    val bottomTable = Table()
    /** A ScrollPane scrolling [topTable], disabled by the disableScroll parameter */
    val scrollPane = AutoScrollPane(topTable)
    /** A fixed SplitPane holds [scrollPane] and [bottomTable] */
    val splitPane = SplitPane(scrollPane, bottomTable, true, BaseScreen.skin)

    init {
        descriptionLabel.wrap = true
        val descriptionWithPad = Table()
        descriptionWithPad.add(descriptionLabel).pad(10f).grow()
        descriptionScroll = AutoScrollPane(descriptionWithPad, BaseScreen.skin)

        rightSideButton.disable()
        rightSideGroup.addActor(rightSideButton)

        val portrait = com.badlogic.gdx.Gdx.graphics.height > com.badlogic.gdx.Gdx.graphics.width
            && UncivGame.Current.settings.usePortraitLayout(true)
        if (portrait) {
            // Phone: description on its own full-width row, buttons below it
            bottomTable.add(descriptionScroll).colspan(2).grow().row()
            bottomTable.add(closeButton).pad(6f, 10f, 10f, 10f).left()
            bottomTable.add(rightSideGroup).pad(6f, 10f, 10f, 10f).right()
        } else {
            bottomTable.add(closeButton).pad(10f)
            bottomTable.add(descriptionScroll).grow()
            bottomTable.add(rightSideGroup).pad(10f).right()
        }

        scrollPane.setScrollingDisabled(disableScroll, disableScroll)  // lock scrollPane
        if (disableScroll) scrollPane.clearListeners()  // remove focus capture of AutoScrollPane too
        add(splitPane).expand().fill()
    }

    override fun layout() {
        super.layout()
        bottomTable.height = bottomTable.height.coerceAtMost(maxBottomTableHeight)
        splitPane.splitAmount = (scrollPane.height / (scrollPane.height + bottomTable.height)).coerceAtLeast(screenSplit)
    }

    /** Enables the [rightSideButton]. See [pick] for a way to set the text. */
    fun setRightSideButtonEnabled(enabled: Boolean) {
        rightSideButton.isEnabled = enabled
    }

    /** Sets the text of the [rightSideButton] (does not autotranslate) and enables it if it's the player's turn */
    fun pick(rightButtonText: String) {
        if (GUI.isMyTurn()) rightSideButton.enable()
        rightSideButton.setText(rightButtonText)
    }

    /** This Label adjusts the Y scroll of its ascendant ScrollPane when its text is set:
     *  - short texts stay top-aligned with [closeButton]
     *  - taller texts scroll some of the wrapper top padding out
     *  - still taller texts scroll most but not all of the top padding out
     *
     *  This also ensures vertical scroll is reset when changing description
     */
    inner class DescriptionLabel : Label("", BaseScreen.skin) {
        override fun setText(newText: CharSequence?) {
            super.setText(newText)
            descriptionScroll.validate()
            descriptionScroll.scrollY = (prefHeight + 10f - descriptionScroll.height).coerceIn(0f, 8f)
            descriptionScroll.updateVisualScroll()
        }
    }

    companion object {
        /** Icon size used in [getPickerOptionButton]. */
        const val pickerOptionIconSize = 30f
        /** Return a button for picker screens that display a list of big buttons with icons and labels. */
        fun getPickerOptionButton(icon: Actor, label: String): Button {
            return IconTextButton(label, icon).apply {
                iconCell.size(pickerOptionIconSize).pad(10f)
                labelCell.pad(10f)
            }
        }
    }
}
