package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.PortraitWidgets
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize

/**
 *  Phone version of [PolicyPickerScreen]: a list of branch cards. A card opens to list its policies as rows
 *  (name, effect, state); tapping a row selects it and the green button at the bottom adopts it.
 *  Same rules as the classic screen (adoptability, cost, free policies), only the presentation differs.
 */
class PolicyPickerPortraitScreen(
    private val viewingCiv: Civilization,
    private val canChangeState: Boolean,
    select: String? = null
) : BaseScreen(), RecreateOnResize {

    private val policies = viewingCiv.policies
    private val ruleset = viewingCiv.gameInfo.ruleset
    private val branches = ruleset.policyBranches.values.toList()

    private var selectedPolicy: Policy? = select?.let { ruleset.policies[it] }
    private var openBranch: String? = selectedPolicy?.branch?.name
        ?: branches.firstOrNull { policies.isAdopted(it.name) && !policies.isAdopted(it.policies.last().name) }?.name
        ?: branches.firstOrNull { isPickable(it) }?.name
        ?: branches.firstOrNull()?.name

    private val panelColor = Color(0.03f, 0.05f, 0.24f, 0.96f)
    private val rowColor = Color(0.2f, 0.3f, 0.5f, 0.45f)
    private val rowDim = Color(0.15f, 0.17f, 0.24f, 0.8f)
    private val adoptedColor = Color(0.12f, 0.5f, 0.22f, 0.35f)
    private val pickableColor = Color(0.22f, 0.6f, 0.86f, 0.3f)
    private val selectedColor = Color(0.22f, 0.6f, 0.86f, 0.6f)
    private val primaryColor = Color(0.12f, 0.5f, 0.22f, 1f)
    private val muted = Color(0.73f, 0.78f, 0.87f, 1f)
    private val green = Color(0.4f, 0.86f, 0.45f, 1f)

    private val listTable = Table()
    private val bottomTable = Table()

    init {
        // Scenarios explain doctrines through their own tasks; the generic popup only gets in the way there
        if (!com.unciv.ui.screens.mainmenuscreen.ScenarioListScreen.isScenarioGame(viewingCiv.gameInfo.gameId))
            displayTutorial(com.unciv.models.TutorialTrigger.CultureAndPolicies)
        val root = Table()
        root.setFillParent(true)
        root.top()
        val width = stage.width - 16f
        root.add(buildHeader()).width(width).padTop(8f).row()
        listTable.top()
        listTable.defaults().width(width).padTop(8f)
        val scroll = AutoScrollPane(listTable)
        scroll.setScrollingDisabled(true, false)
        root.add(scroll).width(width + 16f).grow().row()
        root.add(bottomTable).width(width).pad(8f).row()
        stage.addActor(root)
        globalShortcuts.add(KeyCharAndCode.BACK) { game.popScreen() }
        rebuild()
    }

    private fun bg(part: String, color: Color) = skinStrings.getUiBackground(
        "PolicyScreen/Portrait/$part", skinStrings.roundedEdgeRectangleSmallShape, color)

    private fun isPickable(policy: Policy) = viewingCiv.isCurrentPlayer() && canChangeState && !viewingCiv.isDefeated()
        && !policies.isAdopted(policy.name) && policy.policyBranchType != PolicyBranchType.BranchComplete
        && policies.isAdoptable(policy) && policies.canAdoptPolicy()

    private fun buildHeader(): Table {
        val header = Table()
        header.background = bg("Header", panelColor)
        header.pad(10f, 12f, 10f, 12f)
        header.add(PortraitWidgets.backButton { game.popScreen() }).padRight(12f)
        val titles = Table()
        titles.add("Policies".toLabel(fontSize = 24, alignment = Align.left)).left().row()
        val subtitle = when {
            policies.freePolicies > 0 -> "Adopt free policy"
            policies.allPoliciesAdopted(checkEra = false) -> "All policies adopted"
            else -> "Culture stored: [${policies.storedCulture}] / [${policies.getCultureNeededForNextPolicy()}] for the next policy"
        }
        titles.add(subtitle.toLabel(fontSize = 15, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left()
        header.add(titles).expandX().growX().left()
        return header
    }

    private fun rebuild() {
        listTable.clear()
        for (branch in branches) listTable.add(buildBranchCard(branch)).row()
        rebuildBottom()
    }

    private fun buildBranchCard(branch: PolicyBranch): Table {
        val card = Table()
        card.pad(10f, 12f, 10f, 12f)
        val adopted = policies.isAdopted(branch.name)
        val complete = policies.isAdopted(branch.policies.last().name)
        val eraLocked = ruleset.eras[branch.era]!!.eraNumber > viewingCiv.getEraNumber()
        val open = branch.name == openBranch
        card.background = bg("Branch", when {
            adopted -> adoptedColor
            isPickable(branch) -> pickableColor
            else -> rowColor
        })

        // Title row: icon, name, status chip, chevron
        val title = Table()
        title.touchable = Touchable.enabled
        val iconPath = "PolicyBranchIcons/" + branch.name
        if (ImageGetter.imageExists(iconPath)) title.add(ImageGetter.getImage(iconPath)).size(32f).padRight(10f)
        title.add(branch.name.toLabel(fontSize = 20, alignment = Align.left, hideIcons = true)).expandX().left()
        val memberCount = branch.policies.size - 1
        val done = if (complete) memberCount else branch.policies.count { policies.isAdopted(it.name) && it.policyBranchType == PolicyBranchType.Member }
        val status = when {
            adopted -> "[$done]/[$memberCount]".tr() to green
            eraLocked -> "{Unlocked at} {${branch.era}}".tr() to muted
            isPickable(branch) -> "Adoptable".tr() to Color.WHITE
            else -> "" to muted
        }
        if (status.first.isNotEmpty()) title.add(status.first.toLabel(fontSize = 15, fontColor = status.second)).padLeft(8f)
        title.add(ImageGetter.getImage("OtherIcons/BackArrow").apply { rotation = if (open) -90f else 90f; setOrigin(Align.center) }.let {
            val holder = Table(); holder.add(it).size(16f); holder }).padLeft(8f)
        title.onClick { openBranch = if (open) null else branch.name; rebuild() }
        card.add(title).growX().row()

        if (!open) {
            // Folded: what adopting the branch gives, in one line
            val summary = branch.getDescription().lines().firstOrNull { it.isNotBlank() } ?: ""
            if (summary.isNotEmpty())
                card.add(summary.toLabel(fontSize = 15, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left().padTop(4f).row()
            return card
        }

        // Open: the branch itself first (opening it), then its policies in tree order, then the completion bonus
        card.add(buildPolicyRow(branch, "Open the branch".tr() + ": " + branch.name.tr(hideIcons = true))).growX().padTop(8f).row()
        for (policy in branch.policies.sortedWith(compareBy({ it.row }, { it.column }))) {
            if (policy.policyBranchType == PolicyBranchType.BranchComplete) continue
            card.add(buildPolicyRow(policy, policy.name.tr(hideIcons = true))).growX().padTop(6f).row()
        }
        val completion = branch.policies.last()
        val bonus = Table()
        bonus.background = bg("Completion", if (complete) adoptedColor else rowDim)
        bonus.pad(8f, 10f, 8f, 10f)
        bonus.add("Branch completion bonus".toLabel(fontSize = 15, fontColor = if (complete) green else muted, alignment = Align.left)).left().row()
        bonus.add(completion.getDescription().toLabel(fontSize = 14, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left()
        card.add(bonus).growX().padTop(6f).row()
        return card
    }

    private fun buildPolicyRow(policy: Policy, title: String): Table {
        val row = Table()
        row.touchable = Touchable.enabled
        row.pad(8f, 10f, 8f, 10f)
        val adopted = policies.isAdopted(policy.name)
        val pickable = isPickable(policy)
        val selected = policy == selectedPolicy
        row.background = bg("Policy", when {
            selected -> selectedColor
            adopted -> adoptedColor
            pickable -> pickableColor
            else -> rowDim
        })
        val iconPath = "PolicyIcons/" + policy.name
        if (ImageGetter.imageExists(iconPath))
            row.add(ImageGetter.getImage(iconPath).apply { color = if (adopted || pickable || selected) Color.WHITE else muted }).size(28f).padRight(10f)
        val texts = Table()
        texts.add(title.toLabel(fontSize = 17, alignment = Align.left, hideIcons = true).apply { wrap = true }).growX().left().row()
        // The effect, without the repeated name the classic description starts with
        val effect = policy.getDescription().lines().filter { it.isNotBlank() && it != policy.name.tr() }.joinToString("\n")
        if (effect.isNotEmpty())
            texts.add(effect.toLabel(fontSize = 14, fontColor = muted, alignment = Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
        val missing = policy.requires?.filter { !policies.isAdopted(it) && it != policy.branch.name || (it == policy.branch.name && !policies.isAdopted(it)) } ?: emptyList()
        if (!adopted && !pickable && missing.isNotEmpty())
            texts.add(("{Requires}: " + missing.joinToString(", ") { it.tr() }).toLabel(fontSize = 13, fontColor = Color(1f, 0.75f, 0.5f, 1f), alignment = Align.left).apply { wrap = true }).growX().left().padTop(2f).row()
        row.add(texts).growX()
        when {
            adopted -> row.add(ImageGetter.getImage("OtherIcons/Checkmark").apply { color = green }).size(22f).padLeft(8f)
            !pickable -> row.add(ImageGetter.getImage("OtherIcons/LockSmall").apply { color = muted }).size(16f).padLeft(8f)
        }
        row.onClick {
            selectedPolicy = if (selected) null else policy
            rebuild()
        }
        return row
    }

    private fun rebuildBottom() {
        bottomTable.clear()
        val policy = selectedPolicy
        val button = Table()
        button.pad(12f)
        val pickable = policy != null && isPickable(policy)
        button.background = bg("Primary", if (pickable) primaryColor else rowDim)
        val text = when {
            policy != null && pickable -> "Adopt [${policy.name}]"
            policies.freePolicies > 0 -> "Adopt free policy"
            policies.allPoliciesAdopted(checkEra = false) -> "All policies adopted"
            else -> "{Adopt policy} (${policies.storedCulture}/${policies.getCultureNeededForNextPolicy()})"
        }
        button.add(text.toLabel(fontSize = 19, fontColor = if (pickable) Color.WHITE else muted).apply { wrap = true; setAlignment(Align.center) }).growX()
        if (pickable) {
            button.touchable = Touchable.enabled
            button.onClick(UncivSound.Policy) { confirmAction() }
        }
        bottomTable.add(button).growX().minHeight(56f)
    }

    private fun confirmAction() {
        val policy = selectedPolicy ?: return
        if (!isPickable(policy)) return
        Gdx.input.inputProcessor = null  // no double taps while the game state changes
        policies.adopt(policy)
        if (game.screen !is PolicyPickerPortraitScreen) game.popScreen()
        else game.replaceCurrentScreen { recreate() }
    }

    override fun getCivilopediaRuleset() = ruleset

    override fun recreate(): BaseScreen = PolicyPickerPortraitScreen(viewingCiv, canChangeState, selectedPolicy?.name)
}
