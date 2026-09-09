package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.logic.trade.TradeOffersList
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AskNumberPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.ForeignCivView
import kotlin.math.min

/**
 *  Phone (portrait) trade page, used instead of [TradeTable] + [OfferColumnsTable].
 *
 *  The classic screen puts four scrolling lists on one screen; on a 412-unit-wide phone that
 *  leaves four unreadable slivers and pushes the offer button off the bottom. Here it is one
 *  list at a time, chosen with two tabs ("what we can give" / "what they can give"), a summary
 *  of the trade on the table always visible, and the green offer button pinned to the bottom.
 *
 *  Only built when `settings.usePortraitLayout(isPortrait())` - landscape keeps [TradeTable].
 */
class TradePortraitTable(
    private val screen: DiplomacyScreen,
    private val ourCiv: ForeignCivView,
    private val theirCiv: ForeignCivView
) : Table(), TradeUi {

    override val tradeView = screen.viewingCivView.getTradeView(theirCiv)

    private val pageWidth get() = screen.stage.width - 16f

    /** false = our goods, true = theirs */
    private var showTheirs = false

    private val tabsRow = Table()
    private val listHolder = Table()
    private val stagedHolder = Table()
    private val buttonHolder = Table()

    private var offerEnabled = false

    init {
        top()
        defaults().width(pageWidth)

        if (tradeView.tryLoadOurPendingOffer()) offerEnabled = true

        add(tabsRow).padTop(8f).row()
        add(listHolder).grow().padTop(8f).row()
        add(stagedHolder).padTop(8f).row()
        add(buttonHolder).padTop(8f).padBottom(8f).row()
        refreshOffers()
    }

    override fun enableOfferButton(isEnabled: Boolean) {
        offerEnabled = isEnabled
        updateButton()
    }

    override fun refreshOffers() {
        updateTabs()
        updateList()
        updateStaged()
        updateButton()
    }

    //region tabs

    private fun updateTabs() {
        tabsRow.clear()
        tabsRow.add(tab("Our items", !showTheirs) { showTheirs = false; refreshOffers() })
            .width(pageWidth / 2 - 3f).padRight(6f)
        tabsRow.add(tab("[${theirCiv.civName}]'s items", showTheirs) { showTheirs = true; refreshOffers() })
            .width(pageWidth / 2 - 3f)
    }

    private fun tab(text: String, selected: Boolean, action: () -> Unit): Table {
        val tab = Table()
        tab.background = DiplomacyPortrait.bg("Tab",
            if (selected) Color(0.22f, 0.6f, 0.86f, 1f) else DiplomacyPortrait.cardColor)
        tab.pad(10f)
        tab.add(text.toLabel(Color.WHITE, 17, hideIcons = true).apply {
            wrap = true; setAlignment(Align.center)
        }).growX()
        tab.touchable = Touchable.enabled
        tab.onClick(action)
        return tab
    }

    //endregion
    //region the list of things one side can offer

    private fun updateList() {
        listHolder.clear()
        val list = Table()
        list.top()
        list.defaults().width(pageWidth - 16f).padTop(6f)

        val civ = if (showTheirs) theirCiv else ourCiv
        val available = if (showTheirs) tradeView.theirAvailableOffers() else tradeView.ourAvailableOffers()
        val staged = if (showTheirs) tradeView.theirStagedOffers() else tradeView.ourStagedOffers()
        val counter = if (showTheirs) tradeView.ourStagedOffers() else tradeView.theirStagedOffers()
        val otherSideOffers = if (showTheirs) tradeView.ourAvailableOffers() else tradeView.theirAvailableOffers()
        val untradable = civ.getPerTurnResourcesWithOriginsForTrade().removeAll(Constants.tradable)

        var any = false
        for (offerType in TradeOfferType.entries) {
            val offers = available.without(staged).filter { it.type == offerType }
                .sortedWith(compareBy(
                    { if (UncivGame.Current.settings.orderTradeOffersByAmount) -it.amount else 0 },
                    { if (it.type == TradeOfferType.City) it.getOfferText() else it.name.tr() }
                ))
            if (offers.isEmpty()) continue
            any = true
            val header = sectionName(offerType)
            if (header.isNotEmpty())
                list.add(DiplomacyPortrait.sectionLabel(header)).left().padTop(12f).row()
            for (offer in offers) {
                val unique = offerType in listOf(TradeOfferType.Luxury_Resource, TradeOfferType.Strategic_Resource)
                    && otherSideOffers.all { it.type != offer.type || it.name != offer.name || it.amount < 0 }
                list.add(offerRow(offer, untradable.sumBy(offer.name), unique, tradable(offer)) {
                    click(offer, false, staged, counter, civ)
                }).row()
            }
        }
        if (!any)
            list.add("There's nothing on the table".toLabel(DiplomacyPortrait.muted, 16, Align.center)
                .apply { wrap = true }).padTop(30f).row()

        val scroll = ScrollPane(list)
        scroll.setScrollingDisabled(true, false)
        listHolder.add(scroll).grow()
    }

    private fun sectionName(offerType: TradeOfferType) = when (offerType) {
        TradeOfferType.Embassy, TradeOfferType.Gold, TradeOfferType.Gold_Per_Turn,
        TradeOfferType.Treaty, TradeOfferType.Agreement, TradeOfferType.Introduction -> ""
        TradeOfferType.Luxury_Resource -> "Luxury resources"
        TradeOfferType.Strategic_Resource -> "Strategic resources"
        TradeOfferType.Stockpiled_Resource -> "Stockpiled resources"
        TradeOfferType.Technology -> "Technologies"
        TradeOfferType.WarDeclaration -> "Declarations of war"
        TradeOfferType.PeaceProposal -> "Peace Proposals"
        TradeOfferType.City -> "Cities"
    }

    private fun tradable(offer: TradeOffer) =
        offer.isTradable() && offer.name != Constants.peaceTreaty
            && (offer.name != Constants.researchAgreement
                || ourCiv.gold + theirCiv.gold > ourCiv.getResearchAgreementCost(theirCiv) * 2)

    /** One finger-sized line: icon, what it is, and a green plus on the right */
    private fun offerRow(offer: TradeOffer, untradable: Int, unique: Boolean, enabled: Boolean, action: () -> Unit): Table {
        val row = Table()
        row.background = DiplomacyPortrait.bg("Offer",
            if (!enabled) DiplomacyPortrait.cardDim else DiplomacyPortrait.cardColor)
        row.pad(8f, 12f, 8f, 12f)
        offerIcon(offer)?.let { row.add(it).size(30f).padRight(10f) }
        val color = when {
            !enabled -> DiplomacyPortrait.muted
            unique -> DiplomacyPortrait.green
            else -> Color.WHITE
        }
        row.add(offer.getOfferText(untradable).toLabel(color, 17, Align.left).apply { wrap = true })
            .growX().left().minHeight(28f)
        if (enabled) {
            row.add(ImageGetter.getImage("OtherIcons/New").apply { this.color = DiplomacyPortrait.green })
                .size(22f).padLeft(8f)
            row.touchable = Touchable.enabled
            row.onClick(action)
        }
        return row
    }

    private fun offerIcon(offer: TradeOffer) = when (offer.type) {
        TradeOfferType.Embassy -> ImageGetter.getImage("OtherIcons/Star")
        TradeOfferType.Gold, TradeOfferType.Gold_Per_Turn -> ImageGetter.getStatIcon("Gold")
        TradeOfferType.Luxury_Resource, TradeOfferType.Strategic_Resource, TradeOfferType.Stockpiled_Resource ->
            ImageGetter.getResourcePortrait(offer.name, 30f)
        TradeOfferType.Technology -> ImageGetter.getStatIcon("Science")
        TradeOfferType.City -> ImageGetter.getImage("OtherIcons/Cities")
        TradeOfferType.WarDeclaration, TradeOfferType.PeaceProposal ->
            ourCiv.ruleset.nations[offer.name]?.let { ImageGetter.getNationPortrait(it, 30f) }
        else -> ImageGetter.getImage("OtherIcons/Diplomacy")
    }

    //endregion
    //region what is already on the table

    /** Both staged lists, as chips one can tap to take back off the table */
    private fun updateStaged() {
        stagedHolder.clear()
        val card = Table()
        card.background = DiplomacyPortrait.bg("Staged", DiplomacyPortrait.panelColor)
        card.pad(8f, 12f, 8f, 12f)
        card.defaults().width(pageWidth - 24f)

        val ours = tradeView.ourStagedOffers()
        val theirs = tradeView.theirStagedOffers()
        if (ours.isEmpty() && theirs.isEmpty()) {
            card.add("Tap an item to put it on the table"
                .toLabel(DiplomacyPortrait.muted, 15, Align.center).apply { wrap = true })
        } else {
            card.add(stagedLine("We give", ours, tradeView.ourStagedOffers(), tradeView.theirStagedOffers(), ourCiv)).row()
            card.add(stagedLine("We receive", theirs, tradeView.theirStagedOffers(), tradeView.ourStagedOffers(), theirCiv))
                .padTop(6f).row()
        }
        stagedHolder.add(card).width(pageWidth)
    }

    private fun stagedLine(
        title: String,
        offers: TradeOffersList,
        list: TradeOffersList,
        counter: TradeOffersList,
        civ: ForeignCivView
    ): Table {
        val line = Table()
        line.add(title.toLabel(DiplomacyPortrait.muted, 14, Align.left)).left().growX().row()
        if (offers.isEmpty()) {
            line.add("-".toLabel(DiplomacyPortrait.muted, 15, Align.left)).left().growX().row()
            return line
        }
        val chips = Table()
        chips.left()
        chips.defaults().padRight(6f).padTop(4f)
        var perRow = 0
        for (offer in offers) {
            val chip = stagedChip(offer) { click(offer, true, list, counter, civ) }
            chips.add(chip).left()
            if (++perRow % 2 == 0) chips.row()
        }
        line.add(chips).left().growX().row()
        return line
    }

    private fun stagedChip(offer: TradeOffer, action: () -> Unit): Table {
        val chip = Table()
        chip.background = DiplomacyPortrait.bg("Chip", DiplomacyPortrait.cardColor)
        chip.pad(6f, 10f, 6f, 10f)
        chip.add(offer.getOfferText().toLabel(Color.WHITE, 15, Align.left, hideIcons = true))
        chip.add(ImageGetter.getImage("OtherIcons/Close").apply { color = DiplomacyPortrait.red })
            .size(14f).padLeft(8f)
        chip.touchable = Touchable.enabled
        chip.onClick(action)
        return chip
    }

    //endregion
    //region the offer button

    private fun updateButton() {
        buttonHolder.clear()
        val pending = tradeView.hasPendingOfferFromUs()
        val text = if (pending) "Retract offer" else "{Offer trade}\n({They'll decide on their turn})"
        val enabled = pending || offerEnabled ||
            tradeView.ourStagedOffers().isNotEmpty() || tradeView.theirStagedOffers().isNotEmpty()
        buttonHolder.add(DiplomacyPortrait.bigButton(text,
            if (pending) DiplomacyPortrait.cardColor else DiplomacyPortrait.primaryColor, enabled) {
            if (pending) {
                tradeView.tryRetractOffer()
            } else {
                addGoldForResearchAgreementIfNeeded()
                tradeView.tryProposeStagedTrade()
            }
            refreshOffers()
        }).width(pageWidth).minHeight(56f)
    }

    /** Same rule as the classic screen: a research agreement both sides can only afford together */
    private fun addGoldForResearchAgreementIfNeeded() {
        if (tradeView.ourStagedOffers().none { it.name == Constants.researchAgreement }) return
        val researchCost = screen.viewingCivView.getResearchAgreementCost(theirCiv)
        val ourGoldOffered = tradeView.ourStagedOffers().firstOrNull { it.type == TradeOfferType.Gold }?.amount ?: 0
        val theirGoldOffered = tradeView.theirStagedOffers().firstOrNull { it.type == TradeOfferType.Gold }?.amount ?: 0
        val newOurGold = ourCiv.gold + theirGoldOffered - researchCost
        val newTheirGold = theirCiv.gold + ourGoldOffered - researchCost
        if (newOurGold < 0)
            tradeView.theirStagedOffers().add(tradeView.theirAvailableOffers()
                .first { it.type == TradeOfferType.Gold }.copy(amount = -newOurGold))
        if (newTheirGold < 0)
            tradeView.ourStagedOffers().add(tradeView.ourAvailableOffers()
                .first { it.type == TradeOfferType.Gold }.copy(amount = -newTheirGold))
    }

    //endregion
    //region staging, same semantics as OfferColumnsTable

    private fun click(offer: TradeOffer, invert: Boolean, list: TradeOffersList, counter: TradeOffersList, civ: ForeignCivView) {
        when (offer.type) {
            TradeOfferType.Gold -> askGold(offer, list, civ.gold)
            TradeOfferType.Gold_Per_Turn -> askGold(offer, list, civ.getGoldPerTurn())
            else -> {
                val amount = if (invert) -offer.amount
                    else min(if (offer.type == TradeOfferType.Treaty) Int.MAX_VALUE else 1, offer.amount)
                add(offer.copy(amount = amount), list, counter)
            }
        }
    }

    private fun add(offer: TradeOffer, list: TradeOffersList, counter: TradeOffersList) {
        list.add(offer.copy())
        if (offer.type == TradeOfferType.Treaty) counter.add(offer.copy())
        onChange()
    }

    private fun askGold(offer: TradeOffer, list: TradeOffersList, maxGold: Int) {
        val existing = list.firstOrNull { it.type == offer.type }
        if (existing != null) offer.amount = existing.amount
        AskNumberPopup(
            screen,
            label = "Enter the amount of gold",
            icon = ImageGetter.getStatIcon("Gold").surroundWithCircle(80f),
            defaultValue = offer.amount,
            amountButtons = if (offer.type == TradeOfferType.Gold) listOf(50, 500) else listOf(5, 15),
            bounds = IntRange(0, maxGold),
            actionOnOk = { userInput ->
                offer.amount = userInput
                if (existing == null) list.add(offer) else existing.amount = offer.amount
                if (offer.amount == 0) list.remove(offer)
                onChange()
            }
        ).open()
    }

    private fun onChange() {
        // Any change invalidates an offer already sent - the classic screen retracts it too
        tradeView.tryRetractOffer()
        offerEnabled = false
        refreshOffers()
    }

    //endregion
}

/** What [DiplomacyScreen.setTrade] hands back, so callers work with both the classic and the phone trade page */
interface TradeUi {
    val tradeView: com.unciv.view.TradeView
    /** Rebuild the offer lists after the caller staged something */
    fun refreshOffers()
    fun enableOfferButton(isEnabled: Boolean)
}
