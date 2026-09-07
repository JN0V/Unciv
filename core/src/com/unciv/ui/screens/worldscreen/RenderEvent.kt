package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color

import com.unciv.ui.images.ImageGetter

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.Event
import com.unciv.models.ruleset.EventChoice
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.ActivationAction
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClickSuppressive
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer

/** Renders an [Event] for [AlertPopup] or a floating tutorial task on [WorldScreen] */
class RenderEvent(
    event: Event,
    val worldScreen: WorldScreen,
    val unit: MapUnit? = null,
    val mode: Mode = Mode.Classic,
    /** [Mode.Compact] only: leave out the how-to line (a one-line strip, e.g. over the city map) */
    val titleOnly: Boolean = false,
    /** [Mode.Popup] only: keep the choice buttons out of the (scrolling) text, the caller pins them under it - see [pinnedChoices] */
    val pinChoices: Boolean = false,
    val onChoice: (EventChoice) -> Unit
) : Table() {
    /** With [pinChoices]: the choices as (button text, action) for the caller to place; the action closes through [onChoice] then triggers the choice */
    val pinnedChoices = ArrayList<Pair<String, () -> Unit>>()
    /** [Compact]: title line only (floating card on a phone). [Popup]: full text and images, wide. */
    enum class Mode { Classic, Compact, Popup }
    private val gameInfo get() = worldScreen.gameInfo
    private val stageWidth get() = worldScreen.stage.width

    val isValid: Boolean

    //todo check generated translations

    init {
        defaults().fillX().center().pad(5f)

        val gameContext = GameContext(gameInfo.currentPlayerCiv, unit = unit)
        val choices = event.getMatchingChoices(gameContext)
        isValid = choices != null
        if (isValid) {
            val textWidth = when (mode) {
                Mode.Classic -> stageWidth * 0.5f
                Mode.Compact -> stageWidth * 0.8f
                // Popup: 90% max width minus the popup's own 20+5 padding on each side, with margin - otherwise it scrolls sideways
                Mode.Popup -> stageWidth * 0.7f
            }
            if (event.text.isNotEmpty()) {
                add(WrappableLabel(event.text, textWidth).apply {
                    wrap = true
                    setAlignment(Align.center)
                    optimizePrefWidth()
                }).row()
            }
            if (event.civilopediaText.isNotEmpty()) {
                if (mode == Mode.Compact) {
                    // Title plus the first "how to" line in smaller type; images and the rest open on tap (see WorldScreen)
                    val textLines = event.civilopediaText.filter { it.extraImage.isEmpty() && it.text.isNotEmpty() && !it.separator }
                    val lines = ArrayList<FormattedLine>()
                    // The title may be a header in the popup; on the card it is plain centered text
                    textLines.firstOrNull()?.let { lines.add(FormattedLine(it.text, centered = true, link = it.link)) }
                    if (!titleOnly) textLines.drop(1).firstOrNull()?.let { lines.add(FormattedLine(it.text, size = 15, color = "#c8d2e6", link = it.link)) }
                    val row = Table()
                    row.add(MarkupRenderer.render(lines, textWidth - 40f, linkAction = ::openCivilopedia)).growX()
                    row.add(ImageGetter.getImage("OtherIcons/ForwardArrow").apply { color = Color.LIGHT_GRAY }).size(18f).padLeft(8f)
                    add(row).growX().row()
                } else
                    add(event.renderCivilopediaText(textWidth, ::openCivilopedia)).row()
            }

            for (choice in choices!!) {
                if (pinChoices) pinnedChoices.add(choice.text to { onChoice(choice); choice.triggerChoice(gameInfo.currentPlayerCiv, unit) })
                else addChoice(choice)
            }
        }
    }

    private fun addChoice(choice: EventChoice) {
        addSeparator()

        val button = choice.text.toTextButton()
        val activate: ActivationAction = {
            onChoice(choice)
            choice.triggerChoice(gameInfo.currentPlayerCiv, unit)
        }
        // Suppressive: the compact card and the city strip open the help popup on tap, a choice must not bubble up to that
        button.onClickSuppressive(action = activate)
        val key = KeyCharAndCode.parse(choice.keyShortcut)
        if (key != KeyCharAndCode.UNKNOWN) {
            button.keyShortcuts.add(key, activate)  // explicit: onClick does not register the key/tap equivalence
            button.addTooltip(key)
        }
        add(button).row()

        val lines = (
            choice.civilopediaText.asSequence()
                + choice.uniqueObjects.filter { it.isTriggerable || it.type == UniqueType.Comment }
                    .filterNot { it.isHiddenToUsers() }
                    .map { FormattedLine(it) }
            ).asIterable()
        add(MarkupRenderer.render(lines, stageWidth * 0.5f, linkAction = ::openCivilopedia)).row()
    }

    private fun openCivilopedia(link: String) = worldScreen.openCivilopedia(link)
}
