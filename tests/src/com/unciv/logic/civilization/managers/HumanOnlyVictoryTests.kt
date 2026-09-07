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

/** [com.unciv.models.ruleset.MilestoneType.HaveCountable]: a scenario goal can require several concrete things */
@RunWith(BaseTestRunner::class)
class HaveCountableMilestoneTests {

    @Test
    fun `countable milestones complete in any order and show their progress`() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val victory = json().fromJson(Victory::class.java, """{ "name": "S2", "humanOnly": true, "milestones": [
            "Have at least [1] [Owned [Farm] Tiles]", "Build [Granary]", "Have at least [1] [Adopted [Tradition] Policies]" ] }""")
        testGame.ruleset.victories[victory.name] = victory
        testGame.gameInfo.gameParameters.victoryTypes = arrayListOf(victory.name)
        val human = testGame.addCiv(isPlayer = true)
        val city = testGame.addCity(human, testGame.getTile(0, 0))
        val farmMilestone = victory.milestoneObjects[0]
        val policyMilestone = victory.milestoneObjects[2]

        Assert.assertFalse(farmMilestone.hasBeenCompletedBy(human))
        Assert.assertEquals("{Have at least [1] [Owned [Farm] Tiles]} (0/1)", farmMilestone.getVictoryScreenButtonHeaderText(false, human))

        // Out of order: the policy first, then the building, then the farm
        human.policies.freePolicies = 1
        human.policies.adopt(testGame.ruleset.policies["Tradition"]!!, branchCompletion = false)
        Assert.assertTrue(policyMilestone.hasBeenCompletedBy(human))
        city.cityConstructions.addBuilding("Granary")
        Assert.assertNull(human.victoryManager.getVictoryTypeAchieved())

        val farmTile = testGame.getTile(1, 0)
        testGame.addTileToCity(city, farmTile)
        farmTile.setImprovement("Farm")
        Assert.assertTrue(farmMilestone.hasBeenCompletedBy(human))
        Assert.assertEquals("S2", human.victoryManager.getVictoryTypeAchieved())
    }
}

/** [com.unciv.models.ruleset.unique.Countables.KnownCivs]: other civilizations met, by civ filter */
@RunWith(BaseTestRunner::class)
class KnownCivsCountableTests {
    @Test
    fun `known civilizations count only the ones met, excluding self`() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(12)  // far enough apart that no city sees another (cities meet on sight)
        val human = testGame.addCiv(isPlayer = true)
        val other = testGame.addCiv()
        val cityState = testGame.addCiv(cityStateType = "Cultured")
        testGame.addCity(human, testGame.getTile(0, 0))
        testGame.addCity(other, testGame.getTile(10, 0))
        testGame.addCity(cityState, testGame.getTile(-10, 0))
        val context = com.unciv.models.ruleset.unique.GameContext(human)
        val known = { filter: String -> com.unciv.models.ruleset.unique.Countables.getCountableAmount("Known [$filter] Civilizations", context) }

        Assert.assertEquals(0, known("Major"))
        human.diplomacyFunctions.makeCivilizationsMeet(cityState)
        Assert.assertEquals(0, known("Major"))
        Assert.assertEquals(1, known("City-State"))
        human.diplomacyFunctions.makeCivilizationsMeet(other)
        Assert.assertEquals(1, known("Major"))
        Assert.assertEquals(2, known("all"))
    }
}
