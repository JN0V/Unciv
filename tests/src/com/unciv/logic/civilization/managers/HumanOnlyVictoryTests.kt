package com.unciv.logic.civilization.managers

import com.unciv.json.json
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Victory
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/** Scenario goals ([Victory.humanOnly]) are won by the human player only, and are not offered as game options */
@RunWith(BaseTestRunner::class)
class HumanOnlyVictoryTests {

    private fun victoryFromJson(humanOnly: Boolean): Victory = json().fromJson(
        Victory::class.java,
        """{ "name": "Scenario", "humanOnly": $humanOnly, "milestones": ["Build [Monument]"] }"""
    )

    private fun gameWithScenarioVictory(humanOnly: Boolean): TestGame {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val victory = victoryFromJson(humanOnly)
        testGame.ruleset.victories[victory.name] = victory
        testGame.gameInfo.gameParameters.victoryTypes = arrayListOf(victory.name)
        return testGame
    }

    @Test
    fun `AI civ does not achieve a humanOnly victory`() {
        val testGame = gameWithScenarioVictory(humanOnly = true)
        val ai = testGame.addCiv()
        val human = testGame.addCiv(isPlayer = true)
        testGame.addCity(ai, testGame.getTile(0, 0)).cityConstructions.addBuilding("Monument")
        testGame.addCity(human, testGame.getTile(2, 0)).cityConstructions.addBuilding("Monument")

        Assert.assertNull(ai.victoryManager.getVictoryTypeAchieved())
        Assert.assertEquals("Scenario", human.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `AI civ achieves a normal victory`() {
        val testGame = gameWithScenarioVictory(humanOnly = false)
        val ai = testGame.addCiv()
        testGame.addCity(ai, testGame.getTile(0, 0)).cityConstructions.addBuilding("Monument")

        Assert.assertEquals("Scenario", ai.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `humanOnly victories are not selectable in the new game options`() {
        val testGame = gameWithScenarioVictory(humanOnly = true)
        val selectable = testGame.ruleset.selectableVictories().map { it.name }
        Assert.assertFalse("Scenario" in selectable)
        Assert.assertTrue(selectable.isNotEmpty())
    }

    @Test
    fun `per-scenario tutorial tasks survive a settings round trip`() {
        val settings = GameSettings()
        settings.scenarioTutorialTasks["game-1"] = hashSetOf("Found city", "Pass a turn")
        settings.tutorialTasksCompleted.add("Open the options table")
        val restored = json().fromJson(GameSettings::class.java, json().toJson(settings))
        Assert.assertEquals(hashSetOf("Found city", "Pass a turn"), restored.scenarioTutorialTasks["game-1"])
        Assert.assertEquals(hashSetOf("Open the options table"), restored.tutorialTasksCompleted)
    }
}
