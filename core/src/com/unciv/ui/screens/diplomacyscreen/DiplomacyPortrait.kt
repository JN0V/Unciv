package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.CityStateFunctions
import com.unciv.logic.civilization.diplomacy.Demand
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.civilization.managers.quests.AssignedQuest
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.tilegroups.citybutton.InfluenceTable
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.widgets.PortraitWidgets
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.roundToInt

/**
 *  Phone (portrait) presentation of the [DiplomacyScreen].
 *
 *  Replaces the classic "icon column + squeezed detail" layout with the language of the other portrait
 *  screens (see [com.unciv.ui.screens.pickerscreens.PolicyPickerPortraitScreen]): a dark header with a
 *  round back button, full-width rounded cards, section labels, 44+ unit touch targets and a green
 *  primary / dark red destructive action at the bottom.
 *
 *  Nothing here runs in landscape - [DiplomacyScreen] only builds this when
 *  `settings.usePortraitLayout(isPortrait())`.
 */
class DiplomacyPortrait(private val screen: DiplomacyScreen) {

    /** One phone page: [body] scrolls, [footer] stays pinned above the bottom edge */
    class Page(val body: Table, val footer: Table? = null)

    companion object {
        val panelColor = Color(0.03f, 0.05f, 0.24f, 0.96f)
        val cardColor = Color(0.2f, 0.3f, 0.5f, 0.45f)
        val cardDim = Color(0.15f, 0.17f, 0.24f, 0.8f)
        val primaryColor = Color(0.12f, 0.5f, 0.22f, 1f)
        val dangerColor = Color(0.45f, 0.05f, 0.03f, 1f)
        val muted = Color(0.73f, 0.78f, 0.87f, 1f)
        val green = Color(0.4f, 0.86f, 0.45f, 1f)
        val warm = Color(1f, 0.75f, 0.5f, 1f)
        val red = Color(0.95f, 0.42f, 0.35f, 1f)

        fun bg(part: String, color: Color) = BaseScreen.skinStrings.getUiBackground(
            "DiplomacyScreen/Portrait/$part", BaseScreen.skinStrings.roundedEdgeRectangleSmallShape, color)

        /** Full-width tappable row: icon, title, optional subtitle, chevron. The card of every portrait screen. */
        fun actionRow(
            iconPath: String?,
            title: String,
            subtitle: String = "",
            enabled: Boolean = true,
            color: Color = cardColor,
            titleColor: Color = Color.WHITE,
            action: () -> Unit
        ): Table {
            val row = Table()
            row.background = bg("Row", if (enabled) color else cardDim)
            row.pad(10f, 12f, 10f, 12f)
            if (iconPath != null && ImageGetter.imageExists(iconPath))
                row.add(ImageGetter.getImage(iconPath).apply {
                    this.color = if (enabled) Color.WHITE else muted
                }).size(28f).padRight(12f)
            val texts = Table()
            texts.add(title.toLabel(if (enabled) titleColor else muted, 18, Align.left, hideIcons = true)
                .apply { wrap = true }).growX().left().row()
            if (subtitle.isNotEmpty())
                texts.add(subtitle.toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
            row.add(texts).growX()
            if (enabled) {
                row.add(chevron()).size(16f).padLeft(8f)
                row.touchable = Touchable.enabled
                row.onClick(action)
            }
            return row
        }

        fun chevron(): Actor = ImageGetter.getImage("OtherIcons/ArrowRight").apply { color = muted }

        /** The green / dark red bar at the bottom of a page */
        fun bigButton(text: String, color: Color, enabled: Boolean, iconPath: String? = null, action: () -> Unit): Table {
            val button = Table()
            button.background = bg("Primary", if (enabled) color else cardDim)
            button.pad(12f)
            if (iconPath != null && ImageGetter.imageExists(iconPath))
                button.add(ImageGetter.getImage(iconPath).apply { this.color = if (enabled) Color.WHITE else muted })
                    .size(24f).padRight(10f)
            button.add(text.toLabel(if (enabled) Color.WHITE else muted, 19, hideIcons = true)
                .apply { wrap = true; setAlignment(Align.center) }).growX()
            if (enabled) {
                button.touchable = Touchable.enabled
                button.onClick(action)
            }
            return button
        }

        /** Small rounded chip, e.g. "At war" */
        fun chip(text: String, color: Color): Table {
            val chip = Table()
            chip.background = bg("Chip", color)
            chip.pad(3f, 8f, 3f, 8f)
            chip.add(text.toLabel(Color.WHITE, 13, hideIcons = true))
            return chip
        }

        fun sectionLabel(text: String) = text.toLabel(muted, 14, Align.left)
    }

    private val viewingCiv = screen.viewingCiv
    /** Width available to a card, the header and the bottom button */
    val contentWidth get() = screen.stage.width - 16f

    //region shared pieces

    /** Header of a detail page: round back button, name, one line of context */
    fun header(title: String, subtitle: String, back: () -> Unit): Table {
        val header = Table()
        header.background = bg("Header", panelColor)
        header.pad(10f, 12f, 10f, 12f)
        header.add(PortraitWidgets.backButton(back)).padRight(12f)
        val texts = Table()
        texts.add(title.toLabel(Color.WHITE, 23, Align.left, hideIcons = true).apply { wrap = true }).growX().left().row()
        if (subtitle.isNotEmpty())
            texts.add(subtitle.toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left()
        header.add(texts).growX().left()
        return header
    }

    private fun civSubtitle(civ: Civilization): String {
        val parts = ArrayList<String>()
        if (civ.isCityState) parts += civ.cityStateType.name.tr()
        else if (civ.nation.leaderName.isNotEmpty()) parts += civ.getLeaderDisplayName().tr(hideIcons = true)
        val cities = civ.cities.count { viewingCiv.hasExplored(it.getCenterTile()) }
        if (cities > 1) parts += "[$cities] cities".tr()
        return parts.joinToString(" • ")
    }

    private fun statusChip(civ: Civilization, ourManager: DiplomacyManager, theirManager: DiplomacyManager): Table? = when {
        viewingCiv.isAtWarWith(civ) -> chip("At war", Color(0.6f, 0.1f, 0.08f, 1f))
        ourManager.diplomaticStatus == DiplomaticStatus.DefensivePact -> chip(Constants.defensivePact, Color(0.35f, 0.15f, 0.5f, 1f))
        civ.isCityState && civ.allyCiv == viewingCiv -> chip("Ally", Color(0.1f, 0.35f, 0.55f, 1f))
        ourManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship) -> chip("Friend", Color(0.1f, 0.4f, 0.2f, 1f))
        theirManager.hasFlag(DiplomacyFlags.Denunciation) || ourManager.hasFlag(DiplomacyFlags.Denunciation) ->
            chip("Denounced", Color(0.5f, 0.28f, 0.06f, 1f))
        else -> null
    }

    private fun relationshipColor(level: RelationshipLevel) = when (level) {
        RelationshipLevel.Neutral -> Color.WHITE
        RelationshipLevel.Favorable, RelationshipLevel.Friend, RelationshipLevel.Ally -> green
        RelationshipLevel.Afraid -> Color.YELLOW
        else -> red
    }

    //endregion

    //region list of known civilizations

    /** The list page: cards for the major civs, then cards for the city-states */
    fun buildList(): Table {
        val list = Table()
        list.top()
        list.defaults().width(contentWidth).padTop(8f)

        val knownCivs = viewingCiv.diplomacyFunctions.getKnownCivsSorted().toList()
        if (knownCivs.isEmpty()) {
            list.add("You have not met any other civilizations yet".toLabel(muted, 16, Align.center)
                .apply { wrap = true }).padTop(40f).row()
            return list
        }

        val majors = knownCivs.filter { !it.isCityState }
        val cityStates = knownCivs.filter { it.isCityState }
        if (majors.isNotEmpty()) {
            list.add(sectionLabel("Major civilizations")).left().padTop(12f).row()
            for (civ in majors) list.add(buildCivCard(civ)).row()
        }
        if (cityStates.isNotEmpty()) {
            list.add(sectionLabel("City-States")).left().padTop(16f).row()
            for (civ in cityStates) list.add(buildCivCard(civ)).row()
        }
        return list
    }

    private fun buildCivCard(civ: Civilization): Table {
        val theirManager = civ.getDiplomacyManager(viewingCiv)!!
        val ourManager = viewingCiv.getDiplomacyManager(civ)!!
        val card = Table()
        card.background = bg("CivCard", if (viewingCiv.isAtWarWith(civ)) Color(0.32f, 0.12f, 0.12f, 0.6f) else cardColor)
        card.pad(10f, 12f, 10f, 12f)
        card.touchable = Touchable.enabled
        card.onClick { screen.updateRightSide(civ) }

        card.add(ImageGetter.getNationPortrait(civ.nation, 48f)).size(48f).padRight(12f)

        val texts = Table()
        val titleRow = Table()
        titleRow.add(civ.civName.toLabel(Color.WHITE, 20, Align.left, hideIcons = true)).left().growX()
        statusChip(civ, ourManager, theirManager)?.let { titleRow.add(it).padLeft(6f) }
        texts.add(titleRow).growX().row()
        texts.add(civSubtitle(civ).toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(2f).row()

        if (civ.isCityState) {
            val influence = theirManager.getInfluence()
            val level = theirManager.relationshipLevel()
            val influenceRow = Table()
            influenceRow.add(("{Influence}: " + influence.toInt().tr()).toLabel(relationshipColor(level), 14, Align.left)).left()
            influenceRow.add(InfluenceTable(influence, level, 110f, 8f)).padLeft(8f).left()
            if (civ.questManager.haveQuestsFor(viewingCiv))
                influenceRow.add(chip("Quests", Color(0.55f, 0.42f, 0.05f, 1f))).padLeft(8f)
            influenceRow.add().growX()
            texts.add(influenceRow).growX().left().padTop(4f).row()
        } else {
            val level = theirManager.relationshipLevel()
            val opinion = theirManager.opinionOfOtherCiv().toInt()
            val text = if (civ.isHuman()) level.name.tr() else "${level.name.tr()} ($opinion)"
            texts.add("{Our relationship}: $text".toLabel(relationshipColor(level), 15, Align.left)).growX().left().padTop(4f).row()
        }
        card.add(texts).growX()
        card.add(chevron()).size(16f).padLeft(8f)
        return card
    }

    //endregion

    //region major civilization page

    fun buildMajorCivPage(otherCiv: Civilization): Page {
        val theirManager = otherCiv.getDiplomacyManager(viewingCiv)!!
        val ourManager = viewingCiv.getDiplomacyManager(otherCiv)!!
        val atWar = viewingCiv.isAtWarWith(otherCiv)
        val canChangeRelations = !viewingCiv.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)
        val ourTurn = !screen.isNotPlayersTurn()

        val page = pageTable()

        // What they think of us, and why
        val relationCard = Table()
        relationCard.background = bg("Relation", panelColor)
        relationCard.pad(10f, 12f, 10f, 12f)
        val level = theirManager.relationshipLevel()
        val head = Table()
        head.add(ImageGetter.getNationPortrait(otherCiv.nation, 40f)).size(40f).padRight(10f)
        val headTexts = Table()
        val opinionText = if (otherCiv.isHuman()) level.name.tr() else "${level.name.tr()} (${theirManager.opinionOfOtherCiv().toInt()})"
        headTexts.add("{Our relationship}: $opinionText".toLabel(relationshipColor(level), 18, Align.left)
            .apply { wrap = true }).growX().left().row()
        val hello = if (theirManager.isRelationshipLevelLE(RelationshipLevel.Enemy)) otherCiv.nation.hateHello else otherCiv.nation.neutralHello
        if (hello.isNotEmpty())
            headTexts.add(hello.toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
        head.add(headTexts).growX()
        relationCard.add(head).growX().row()

        val knownCities = otherCiv.cities.filter { viewingCiv.hasExplored(it.getCenterTile()) }
        if (knownCities.isNotEmpty()) {
            val names = knownCities.joinToString(", ") {
                if (it.isCapital()) it.name.tr() + " " + Fonts.star else it.name.tr()
            }
            relationCard.add(("{Cities}: " + names).toLabel(muted, 14, Align.left).apply { wrap = true })
                .growX().left().padTop(6f).row()
        }

        if (!otherCiv.isHuman()) {
            for (modifier in theirManager.diplomaticModifiers) {
                if (modifier.key == DiplomaticModifiers.AttackedProtectedMinor.name
                    && theirManager.hasModifier(DiplomaticModifiers.DestroyedProtectedMinor)) continue
                val diplomaticModifier = DiplomaticModifiers.safeValueOf(modifier.key) ?: continue
                val value = modifier.value.roundToInt()
                val text = diplomaticModifier.text.tr() + " " + (if (value > 0) "+" else "") + value
                relationCard.add(text.toLabel(if (modifier.value < 0) red else green, 14, Align.left)
                    .apply { wrap = true }).growX().left().padTop(4f).row()
            }
        }
        page.add(relationCard).row()

        // Promises made, in their own card so they don't look like relationship modifiers
        val promises = ArrayList<String>()
        for (demand in Demand.entries) {
            if (theirManager.hasFlag(demand.agreedToDemand))
                promises += demand.wePromisedText.fillPlaceholders(theirManager.getFlag(demand.agreedToDemand).toString())
            if (ourManager.hasFlag(demand.agreedToDemand))
                promises += demand.theyPromisedText.fillPlaceholders(ourManager.getFlag(demand.agreedToDemand).toString())
        }
        if (promises.isNotEmpty()) {
            val card = Table()
            card.background = bg("Promises", cardDim)
            card.pad(10f, 12f, 10f, 12f)
            card.add(sectionLabel("Promises")).left().growX().row()
            for (promise in promises)
                card.add(promise.toLabel(Color.LIGHT_GRAY, 14, Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
            page.add(card).row()
        }

        buildRelationsCard(otherCiv)?.let { page.add(it).row() }

        page.add(sectionLabel("Diplomatic actions")).left().padTop(12f).row()

        if (atWar) {
            if (canChangeRelations) {
                val declaredWarTurns = theirManager.getFlag(DiplomacyFlags.DeclaredWar)
                val blocked = theirManager.hasFlag(DiplomacyFlags.DeclaredWar)
                page.add(actionRow("OtherIcons/Diplomacy", "Negotiate Peace",
                    if (blocked) "${declaredWarTurns.tr()}${Fonts.turn}" else "",
                    enabled = ourTurn && !blocked, color = primaryColor) {
                    val tradeTable = screen.setTrade(otherCiv)
                    val peaceTreaty = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = viewingCiv.gameInfo.speed)
                    tradeTable.tradeView.theirStagedOffers().add(peaceTreaty)
                    tradeTable.tradeView.ourStagedOffers().add(peaceTreaty)
                    tradeTable.refreshOffers()
                    tradeTable.enableOfferButton(true)
                }).row()
            }
        } else {
            page.add(actionRow("OtherIcons/Diplomacy", "Trade", "What do you have in mind?", ourTurn) {
                screen.setTrade(otherCiv).refreshOffers()
            }).row()

            if (!ourManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)) {
                val alreadyAsked = otherCiv.popupAlerts.any {
                    it.type == AlertType.DeclarationOfFriendship && it.value == viewingCiv.civID
                }
                page.add(actionRow("OtherIcons/Star", "Offer Declaration of Friendship ([30] turns)",
                    "", ourTurn && !alreadyAsked) {
                    otherCiv.popupAlerts.add(PopupAlert(AlertType.DeclarationOfFriendship, viewingCiv.civID))
                    screen.updateRightSide(otherCiv)
                }).row()
            }
        }

        page.add(actionRow("OtherIcons/Question", "Demands", "", ourTurn) {
            screen.showPortraitSubPage(otherCiv, buildDemandsPage(otherCiv), "Demands")
        }).row()

        if (otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.getCenterTile()))
            page.add(actionRow("OtherIcons/Cities", "Go to on map") {
                val worldScreen = UncivGame.Current.resetToWorldScreen()
                worldScreen.mapHolder.setCenterPosition(otherCiv.getCapital()!!.location.toHexCoord(), selectUnit = false)
            }).row()

        // Everything that makes an enemy, grouped and clearly marked
        if (!atWar) {
            val hostile = ArrayList<Table>()
            if (!ourManager.hasFlag(DiplomacyFlags.Denunciation) && !ourManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                hostile += bigButton("Denounce ([30] turns)", dangerColor, ourTurn, "OtherIcons/ExclamationMark") {
                    ConfirmPopup(screen, "Denounce [${otherCiv.civName}]?", "Denounce ([30] turns)") {
                        ourManager.denounce()
                        screen.setRightSideFlavorText(otherCiv,
                            if (otherCiv.nation.denounced.isNotEmpty()) otherCiv.nation.denounced else "We will remember this.",
                            "Very well.")
                        UncivGame.Current.musicController.playVoice("${otherCiv.nation.name}.denounced")
                    }.open()
                }
            if (canChangeRelations) {
                val turnsToPeaceTreaty = ourManager.turnsToPeaceTreaty()
                val text = if (turnsToPeaceTreaty > 0) "{Declare war} (${turnsToPeaceTreaty.tr()}${Fonts.turn})" else "Declare war"
                hostile += bigButton(text, dangerColor, ourTurn && turnsToPeaceTreaty == 0, "StatIcons/Strength") {
                    ConfirmPopup(screen, screen.getDeclareWarConfirmText(otherCiv), "Declare war") {
                        ourManager.declareWar()
                        screen.setRightSideFlavorText(otherCiv, otherCiv.nation.attacked, "Very well.")
                        val music = UncivGame.Current.musicController
                        music.chooseTrack(otherCiv.civName, com.unciv.ui.audio.MusicMood.War,
                            com.unciv.ui.audio.MusicTrackChooserFlags.setSpecific)
                        music.playVoice("${otherCiv.civName}.attacked")
                    }.open()
                }
            }
            if (hostile.isNotEmpty()) {
                val footer = pageTable()
                footer.add(sectionLabel("Hostile actions")).left().padTop(4f).row()
                for (button in hostile) footer.add(button).minHeight(52f).padTop(6f).row()
                return Page(page, footer)
            }
        }
        return Page(page)
    }

    /** Who [otherCiv] fights, likes and denounces, among the civs we know ourselves */
    private fun buildRelationsCard(otherCiv: Civilization): Table? {
        val lines = ArrayList<Pair<String, Color>>()
        for (third in otherCiv.getKnownCivs()) {
            if (third == viewingCiv || third.isDefeated()) continue
            val name = if (viewingCiv.knows(third)) third.civName else "an unknown civilization"
            val manager = otherCiv.getDiplomacyManager(third)
            when {
                otherCiv.isAtWarWith(third) -> lines += "At war with [$name]" to red
                third.isCityState -> {}  // their standing with every city-state would drown the card
                manager?.hasFlag(DiplomacyFlags.DefensivePact) == true -> lines += "Defensive pact with [$name]" to Color.SKY
                manager?.hasFlag(DiplomacyFlags.DeclarationOfFriendship) == true -> lines += "Friends with [$name]" to green
                manager?.hasFlag(DiplomacyFlags.Denunciation) == true -> lines += "Denounced [$name]" to warm
                else -> {}
            }
        }
        if (lines.isEmpty()) return null
        val card = Table()
        card.background = bg("Promises", cardDim)
        card.pad(10f, 12f, 10f, 12f)
        card.add(sectionLabel("Relations")).left().growX().row()
        for ((text, color) in lines)
            card.add(text.toLabel(color, 14, Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
        return card
    }

    private fun buildDemandsPage(otherCiv: Civilization): Table {
        val page = pageTable()
        val ourManager = viewingCiv.getDiplomacyManager(otherCiv)!!
        var any = false
        for (demand in Demand.entries) {
            if (!demand.show(viewingCiv)) continue
            any = true
            val alreadyDone = otherCiv.popupAlerts.any { it.type == demand.demandAlert && it.value == viewingCiv.civID }
                || ourManager.hasFlag(demand.agreedToDemand)
            page.add(actionRow("OtherIcons/Question", demand.demandText, "",
                enabled = alreadyDone.not() && !screen.isNotPlayersTurn()) {
                otherCiv.popupAlerts.add(PopupAlert(demand.demandAlert, viewingCiv.civID))
                screen.updateRightSide(otherCiv)
            }).row()
        }
        if (!any) page.add("There's nothing on the table".toLabel(muted, 16, Align.center)).padTop(20f).row()
        return page
    }

    //endregion

    //region city-state page

    fun buildCityStatePage(otherCiv: Civilization): Page {
        val theirManager = otherCiv.getDiplomacyManager(viewingCiv)!!
        val ourManager = viewingCiv.getDiplomacyManager(otherCiv)!!
        val ourTurn = !screen.isNotPlayersTurn()
        val atWar = viewingCiv.isAtWarWith(otherCiv)
        val page = pageTable()

        page.add(buildInfluenceCard(otherCiv, theirManager)).row()

        // Quests first - that is what a city-state page is for
        val quests = otherCiv.questManager.getAssignedQuestsFor(viewingCiv).toList()
        if (quests.isNotEmpty()) {
            page.add(sectionLabel("Quests")).left().padTop(12f).row()
            for (quest in quests) page.add(buildQuestCard(quest)).row()
        }
        for (target in otherCiv.getKnownCivs().filter { otherCiv.questManager.isWarWithMajorActive(it) && viewingCiv != it }) {
            page.add(buildWarQuestCard(target, otherCiv)).padTop(8f).row()
        }

        page.add(sectionLabel("Diplomatic actions")).left().padTop(12f).row()

        page.add(actionRow("OtherIcons/Present", "Give a Gift", "", ourTurn && !atWar) {
            screen.showPortraitSubPage(otherCiv, buildGoldGiftPage(otherCiv), "Give a Gift")
        }).row()

        if (canGiftImprovement(otherCiv))
            page.add(actionRow("OtherIcons/Improvements", "Gift Improvement", "",
                ourTurn && theirManager.getInfluence() >= 60) {
                screen.showPortraitSubPage(otherCiv, buildImprovementGiftPage(otherCiv), "Gift Improvement")
            }).row()

        if (theirManager.diplomaticStatus != DiplomaticStatus.Protector)
            page.add(actionRow("OtherIcons/Shield", "Pledge to protect", "",
                ourTurn && otherCiv.cityStateFunctions.otherCivCanPledgeProtection(viewingCiv)) {
                ConfirmPopup(screen, "Declare Protection of [${otherCiv.civName}]?", "Pledge to protect", true) {
                    otherCiv.cityStateFunctions.addProtectorCiv(viewingCiv)
                    screen.updateRightSide(otherCiv)
                }.open()
            }).row()
        else
            page.add(actionRow("OtherIcons/Shield", "Revoke Protection", "",
                ourTurn && otherCiv.cityStateFunctions.otherCivCanWithdrawProtection(viewingCiv)) {
                ConfirmPopup(screen, "Revoke protection for [${otherCiv.civName}]?", "Revoke Protection") {
                    otherCiv.cityStateFunctions.removeProtectorCiv(viewingCiv)
                    screen.updateRightSide(otherCiv)
                }.open()
            }).row()

        page.add(actionRow("StatIcons/Gold", "Demand Tribute", "", ourTurn && !atWar) {
            screen.showPortraitSubPage(otherCiv, buildTributePage(otherCiv), "Demand Tribute")
        }).row()

        if (viewingCiv.hasUnique(UniqueType.CityStateCanBeBoughtForGold))
            page.add(actionRow("OtherIcons/Star",
                "Diplomatic Marriage ([${otherCiv.cityStateFunctions.getDiplomaticMarriageCost()}] Gold)", "",
                ourTurn && otherCiv.cityStateFunctions.canBeMarriedBy(viewingCiv)) {
                val newCities = otherCiv.cities
                otherCiv.cityStateFunctions.diplomaticMarriage(viewingCiv)
                UncivGame.Current.popScreen()
                for (city in newCities)
                    viewingCiv.popupAlerts.add(PopupAlert(AlertType.DiplomaticMarriage, city.id))
            }).row()

        if (otherCiv.getCapital() != null && viewingCiv.hasExplored(otherCiv.getCapital()!!.getCenterTile()))
            page.add(actionRow("OtherIcons/Cities", "Go to on map") {
                val worldScreen = UncivGame.Current.resetToWorldScreen()
                worldScreen.mapHolder.setCenterPosition(otherCiv.getCapital()!!.location.toHexCoord(), selectUnit = false)
            }).row()

        val footer = pageTable()
        if (!viewingCiv.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)) {
            if (atWar) {
                val cityStatesAlly = otherCiv.allyCiv
                val atWarWithItsAlly = viewingCiv.getKnownCivs().any { it == cityStatesAlly && it.isAtWarWith(viewingCiv) }
                val blocked = theirManager.hasFlag(DiplomacyFlags.DeclaredWar)
                footer.add(bigButton("Negotiate Peace", primaryColor, ourTurn && !atWarWithItsAlly && !blocked,
                    "OtherIcons/Diplomacy") {
                    ConfirmPopup(screen, "Peace with [${otherCiv.civName}]?", "Negotiate Peace", true) {
                        val tradeLogic = TradeLogic(viewingCiv, otherCiv)
                        val treaty = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = viewingCiv.gameInfo.speed)
                        tradeLogic.currentTrade.ourOffers.add(treaty)
                        tradeLogic.currentTrade.theirOffers.add(treaty)
                        tradeLogic.acceptTrade()
                        screen.updateRightSide(otherCiv)
                    }.open()
                }).minHeight(52f).padTop(4f).row()
            } else {
                val turnsToPeaceTreaty = ourManager.turnsToPeaceTreaty()
                val text = if (turnsToPeaceTreaty > 0) "{Declare war} (${turnsToPeaceTreaty.tr()}${Fonts.turn})" else "Declare war"
                footer.add(sectionLabel("Hostile actions")).left().padTop(4f).row()
                footer.add(bigButton(text, dangerColor, ourTurn && turnsToPeaceTreaty == 0, "StatIcons/Strength") {
                    ConfirmPopup(screen, screen.getDeclareWarConfirmText(otherCiv), "Declare war") {
                        ourManager.declareWar()
                        screen.setRightSideFlavorText(otherCiv, otherCiv.nation.attacked, "Very well.")
                    }.open()
                }).minHeight(52f).padTop(6f).row()
            }
        }
        return Page(page, if (footer.hasChildren()) footer else null)
    }

    private fun buildInfluenceCard(otherCiv: Civilization, theirManager: DiplomacyManager): Table {
        val card = Table()
        card.background = bg("Relation", panelColor)
        card.pad(10f, 12f, 10f, 12f)

        val level = theirManager.relationshipLevel()
        val influence = theirManager.getInfluence()
        card.add("{Our relationship}: ${level.name.tr()} (${influence.toInt()})"
            .toLabel(relationshipColor(level), 18, Align.left).apply { wrap = true }).growX().left().row()
        card.add(InfluenceTable(influence, level, contentWidth - 40f, 10f)).growX().left().padTop(6f).row()

        val atWar = otherCiv.isAtWarWith(viewingCiv)
        otherCiv.cityStateFunctions.updateAllyCivForCityState()
        val ally = otherCiv.allyCiv
        val nextLevel = when {
            atWar -> ""
            influence.toInt() < 30 -> "Reach 30 for friendship."
            ally == viewingCiv -> ""
            else -> "Reach highest influence above 60 for alliance."
        }
        if (nextLevel.isNotEmpty())
            card.add(nextLevel.toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(4f).row()

        card.add("{Personality}: {${otherCiv.cityStatePersonality}}".toLabel(muted, 14, Align.left))
            .growX().left().padTop(4f).row()

        if (ally != null) {
            val allyInfluence = otherCiv.getDiplomacyManager(ally)!!.getInfluence().toInt()
            val allyName = if (!viewingCiv.knows(ally) && ally != viewingCiv) "Unknown civilization" else ally.civName
            card.add("Ally: [$allyName] with [$allyInfluence] Influence".toLabel(muted, 14, Align.left)
                .apply { wrap = true }).growX().left().padTop(2f).row()
        }

        val protectors = otherCiv.cityStateFunctions.getProtectorCivs()
        if (protectors.isNotEmpty()) {
            val names = protectors.map {
                if (!viewingCiv.knows(it) && it.civName != viewingCiv.civName) "Unknown civilization".tr() else it.civName.tr()
            }
            card.add(("{Protected by}: " + names.joinToString(", ")).toLabel(muted, 14, Align.left)
                .apply { wrap = true }).growX().left().padTop(2f).row()
        }

        if (otherCiv.detailedCivResources.any { it.resource.resourceType != ResourceType.Bonus }) {
            val resourcesRow = Table()
            resourcesRow.add("{Resources}: ".toLabel(muted, 14, Align.left)).padRight(6f)
            for (supplyList in otherCiv.cityStateFunctions.getCityStateResourcesForAlly()) {
                if (supplyList.resource.resourceType == ResourceType.Bonus) continue
                resourcesRow.add(ImageGetter.getResourcePortrait(supplyList.resource.name, 26f)).size(26f).padRight(3f)
                resourcesRow.add(supplyList.amount.tr().toLabel(muted, 14)).padRight(10f)
            }
            resourcesRow.add().growX()
            card.add(resourcesRow).growX().left().padTop(6f).row()
        }

        // The bonuses, so the player knows what influence buys
        val relationLevel = theirManager.relationshipIgnoreAfraid()
        val gameContext = GameContext(viewingCiv)
        for ((header, bonusLevel) in listOf("When Friends:" to RelationshipLevel.Friend, "When Allies:" to RelationshipLevel.Ally)) {
            val bonuses = CityStateFunctions.getCityStateBonuses(otherCiv.cityStateType, bonusLevel)
                .filterNot { it.isHiddenToUsers() }
            if (bonuses.none()) continue
            val active = relationLevel == bonusLevel
            card.add(header.toLabel(if (active) green else muted, 14, Align.left)).growX().left().padTop(6f).row()
            for (bonus in bonuses) {
                val color = if (active && bonus.conditionalsApply(gameContext)) green else Color.GRAY
                card.add(ColorMarkupLabel(bonus.getDisplayText(), color, fontSize = 14)
                    .apply { wrap = true; setAlignment(Align.left) }).growX().left().padTop(1f).row()
            }
        }

        if (otherCiv.cityStateUniqueUnit != null) {
            val techNames = viewingCiv.gameInfo.ruleset.units[otherCiv.cityStateUniqueUnit]!!.requiredTechs()
            val techAndTech = techNames.joinToString(" and ")
            val isOrAre = if (techNames.count() == 1) "is" else "are"
            card.add(("[${otherCiv.civName}] is able to provide [${otherCiv.cityStateUniqueUnit}] " +
                "once [$techAndTech] [$isOrAre] researched.").toLabel(muted, 14, Align.left)
                .apply { wrap = true }).growX().left().padTop(6f).row()
        }
        return card
    }

    private fun buildQuestCard(assignedQuest: AssignedQuest): Table {
        val card = Table()
        card.background = bg("Quest", Color(0.35f, 0.28f, 0.06f, 0.6f))
        card.pad(10f, 12f, 10f, 12f)
        card.touchable = Touchable.enabled
        card.onClick { assignedQuest.onClickAction() }
        val quest = assignedQuest.quest
        val title = if (quest.influence > 0) "[${quest.name}] (+[${quest.influence.toInt()}] influence)" else quest.name
        val titleRow = Table()
        titleRow.add(ImageGetter.getImage("OtherIcons/Quest")).size(24f).padRight(8f)
        titleRow.add(title.toLabel(Color.WHITE, 18, Align.left).apply { wrap = true }).growX().left()
        titleRow.add(chevron()).size(16f).padLeft(8f)
        card.add(titleRow).growX().row()
        card.add(assignedQuest.getDescription().toLabel(muted, 14, Align.left).apply { wrap = true })
            .growX().left().padTop(4f).row()
        if (quest.duration > 0)
            card.add("[${assignedQuest.getRemainingTurns()}] turns remaining".toLabel(warm, 14, Align.left))
                .growX().left().padTop(2f).row()
        if (quest.isGlobal()) {
            val leaderString = assignedQuest.assignerCiv.questManager.getScoreStringForGlobalQuest(assignedQuest)
            if (leaderString.isNotEmpty())
                card.add(leaderString.toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
        }
        return card
    }

    private fun buildWarQuestCard(target: Civilization, otherCiv: Civilization): Table {
        val card = Table()
        card.background = bg("Quest", Color(0.35f, 0.15f, 0.1f, 0.6f))
        card.pad(10f, 12f, 10f, 12f)
        card.add("War against [${target.civName}]".toLabel(Color.WHITE, 18, Align.left).apply { wrap = true })
            .growX().left().row()
        card.add(("We need you to help us defend against [${target.civName}]. Killing " +
            "[${otherCiv.questManager.unitsToKill(target)}] of their military units would slow their offensive.")
            .toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
        val progress = if (viewingCiv.knows(target))
            "Currently you have killed [${otherCiv.questManager.unitsKilledSoFar(target, viewingCiv)}] of their military units."
        else "You need to find them first!"
        card.add(progress.toLabel(muted, 14, Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
        return card
    }

    private fun buildGoldGiftPage(otherCiv: Civilization): Table {
        val page = pageTable()
        val purse = Table()
        purse.background = bg("Relation", panelColor)
        purse.pad(10f, 12f, 10f, 12f)
        purse.add(ImageGetter.getStatIcon("Gold")).size(24f).padRight(8f)
        purse.add(("{Gold}: " + viewingCiv.gold.tr()).toLabel(Color.WHITE, 17, Align.left)).growX().left()
        page.add(purse).row()
        for (giftAmount in listOf(250, 500, 1000)) {
            val influenceAmount = otherCiv.cityStateFunctions.influenceGainedByGift(viewingCiv, giftAmount)
            page.add(actionRow("StatIcons/Gold", "Gift [$giftAmount] gold (+[$influenceAmount] influence)", "",
                enabled = viewingCiv.gold >= giftAmount && !screen.isNotPlayersTurn()) {
                otherCiv.cityStateFunctions.receiveGoldGift(viewingCiv, giftAmount)
                screen.updateRightSide(otherCiv)
            }).row()
        }
        return page
    }

    private fun canGiftImprovement(otherCiv: Civilization): Boolean {
        if (otherCiv.cities.isEmpty()) return false
        val improvements = otherCiv.gameInfo.ruleset.tileImprovements.filter { it.value.turnsToBuild != -1 }
        return getImprovableResourceTiles(otherCiv).any { tile ->
            improvements.values.any {
                tile.tileResource!!.isImprovedBy(it.name) && tile.improvementFunctions.canBuildImprovement(it, otherCiv.state)
            }
        }
    }

    private fun getImprovableResourceTiles(otherCiv: Civilization) =
        otherCiv.cities.flatMap { it.getTiles() }.filter {
            val resource = it.tileResource
            otherCiv.canSeeResource(resource) && resource.resourceType != ResourceType.Bonus
                && (it.improvement == null || !resource.isImprovedBy(it.improvement!!))
        }

    private fun buildImprovementGiftPage(otherCiv: Civilization): Table {
        val page = pageTable()
        for (improvableTile in getImprovableResourceTiles(otherCiv)) {
            for (tileImprovement in otherCiv.gameInfo.ruleset.tileImprovements.values) {
                if (!improvableTile.tileResource!!.isImprovedBy(tileImprovement.name)) continue
                if (!improvableTile.improvementFunctions.canBuildImprovement(tileImprovement, otherCiv.state)) continue
                page.add(actionRow("OtherIcons/Improvements",
                    "Build [$tileImprovement] on [${improvableTile.tileResource}] (200 Gold)", "",
                    enabled = viewingCiv.gold >= 200) {
                    viewingCiv.addGold(-200)
                    improvableTile.stopWorkingOnImprovement()
                    improvableTile.setImprovement(tileImprovement)
                    otherCiv.cache.updateCivResources()
                    screen.updateRightSide(otherCiv)
                }).row()
            }
        }
        return page
    }

    private fun buildTributePage(otherCiv: Civilization): Table {
        val page = pageTable()
        val card = Table()
        card.background = bg("Relation", panelColor)
        card.pad(10f, 12f, 10f, 12f)
        card.add("Tribute Willingness".toLabel(Color.WHITE, 18, Align.left)).growX().left().row()
        val tributeModifiers = otherCiv.cityStateFunctions.getTributeModifiers(viewingCiv, requireWholeList = true)
        for (item in tributeModifiers) {
            val line = Table()
            line.add(item.key.toLabel(if (item.value >= 0) green else red, 14, Align.left).apply { wrap = true }).growX().left()
            line.add(item.value.tr().toLabel(if (item.value >= 0) green else red, 14)).padLeft(8f)
            card.add(line).growX().padTop(2f).row()
        }
        val sumLine = Table()
        sumLine.add("Sum:".toLabel(Color.WHITE, 15, Align.left)).growX().left()
        sumLine.add(tributeModifiers.values.sum().tr().toLabel(Color.WHITE, 15)).padLeft(8f)
        card.add(sumLine).growX().padTop(6f).row()
        card.add("At least 0 to take gold, at least 30 and size 4 city for worker"
            .toLabel(muted, 13, Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
        page.add(card).row()

        page.add(actionRow("StatIcons/Gold",
            "Take [${otherCiv.cityStateFunctions.goldGainedByTribute()}] gold (-15 Influence)", "",
            enabled = otherCiv.cityStateFunctions.getTributeWillingness(viewingCiv, demandingWorker = false) >= 0) {
            otherCiv.cityStateFunctions.tributeGold(viewingCiv)
            screen.updateRightSide(otherCiv)
        }).padTop(12f).row()
        page.add(actionRow("OtherIcons/Improvements", "Take worker (-50 Influence)", "",
            enabled = otherCiv.cityStateFunctions.getTributeWillingness(viewingCiv, demandingWorker = true) >= 0) {
            otherCiv.cityStateFunctions.tributeWorker(viewingCiv)
            screen.updateRightSide(otherCiv)
        }).row()
        return page
    }

    //endregion

    /** A page: full-width cells, top aligned - the caller wraps it in the scroll pane */
    fun pageTable(): Table {
        val page = Table()
        page.top()
        page.defaults().width(contentWidth).padTop(8f)
        return page
    }
}
