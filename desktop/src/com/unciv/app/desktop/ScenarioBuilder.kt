package com.unciv.app.desktop

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import java.io.File

/**
 * Headless generator for the "Unciv Découverte" discovery scenarios.
 *
 * Run from `android/assets` (so `mods/` resolves) with the output folder as argument:
 *   java -cp Unciv.jar com.unciv.app.desktop.ScenarioBuilder /path/to/unciv-decouverte/scenarios
 */
object ScenarioBuilder {
    private const val MOD = "Unciv Découverte"
    private const val HUMAN = "Rome"

    @JvmStatic
    fun main(args: Array<String>) {
        val game = UncivGame(true)
        UncivGame.Current = game
        game.settings = GameSettings()
        RulesetCache.loadRulesets(consoleMode = true, noMods = false)
        require(RulesetCache.containsKey(MOD)) { "Mod '$MOD' not found in mods/ - available: ${RulesetCache.keys}" }

        if (args.firstOrNull() == "gnk") {
            // A regular Gods & Kings game (full ruleset) for UI testing, saved to args[1]
            val gp = GameParameters().apply {
                players = arrayListOf(Player("France", PlayerType.Human), Player("England", PlayerType.AI), Player("Rome", PlayerType.AI), Player("Persia", PlayerType.AI))
                numberOfCityStates = 4
                espionageEnabled = true
            }
            val mp = MapParameters().apply { mapSize = MapSize("Small"); seed = 42 }
            val game = GameStarter.startNewGame(GameSetupInfo(gp, mp))
            game.gameParameters.victoryTypes = ArrayList(game.ruleset.victories.keys)
            game.currentPlayer = "France"
            File(args[1]).writeText(UncivFiles.gameInfoToString(game, forceZip = false))
            println("Wrote ${args[1]}")
            return
        }
        if (args.firstOrNull() == "verify") {
            verify(File(args.getOrElse(1) { "scenarios" }), args.getOrElse(2) { "10" }.toInt())
            return
        }
        val outDir = File(args.getOrElse(0) { "scenarios" }).apply { mkdirs() }
        buildS1(outDir)
        buildS2(outDir)
        buildS3(outDir)
        buildS4(outDir)
        buildS5(outDir)
        buildS6(outDir)
        println("Done: ${outDir.listFiles()?.map { it.name }}")
    }

    // ------------------------------------------------------------------ verification

    /** Loads every scenario, prints a summary, then simulates [turns] turns with every civ as AI. */
    private fun verify(dir: File, turns: Int) {
        var failures = 0
        for (file in dir.listFiles()!!.filter { it.isFile }.sortedBy { it.name }) {
            println("=== ${file.name}")
            try {
                val game = UncivFiles.gameInfoFromString(file.readText())
                val human = game.human()
                println("  ruleset=${game.gameParameters.baseRuleset} difficulty=${game.difficulty} speed=${game.gameParameters.speed} victories=${game.gameParameters.victoryTypes} maxTurns=${game.gameParameters.maxTurns} turn=${game.turns} current=${game.currentPlayer}")
                println("  map: radius=${game.tileMap.mapParameters.mapSize.radius} tiles=${game.tileMap.values.size}")
                for (civ in game.civilizations.filter { !it.isBarbarian && !it.isSpectator() }) {
                    val cities = civ.cities.joinToString { "${it.name}@${it.location}(pop ${it.population.population}, ${it.cityConstructions.getBuiltBuildings().map { b -> b.name }})" }
                    val units = civ.units.getCivUnits().groupBy { it.name }.map { "${it.value.size}x${it.key}" }
                    val dist = if (civ != human && civ.cities.isNotEmpty() && human.cities.isNotEmpty())
                        " dist=" + civ.getCapital()!!.getCenterTile().aerialDistanceTo(human.getCapital()!!.getCenterTile()) else ""
                    println("  ${civ.civID} [${civ.playerType}]${if (civ.isCityState) " city-state" else ""} techs=${civ.tech.techsResearched.size} cities=[$cities] units=$units war=${civ.diplomacy.values.filter { it.diplomaticStatus == com.unciv.logic.civilization.diplomacy.DiplomaticStatus.War }.map { it.otherCivName }}$dist")
                }
                println("  camps=${game.barbarians.encampments.map { it.position }} alerts=${human.popupAlerts.map { it.value }}")
                // nextTurn() runs every AI and stops at the (idle) human: one call = one full round
                repeat(turns) { game.nextTurn() }
                println("  simulated $turns turns OK -> turn ${game.turns}, human cities=${human.cities.size}, units=${human.units.getCivUnits().count()}, victory=${game.getAliveMajorCivs().firstOrNull { it.victoryManager.hasWon() }?.civID}")
            } catch (ex: Throwable) {
                failures++
                println("  FAILED: $ex")
                ex.stackTrace.take(8).forEach { println("    at $it") }
            }
        }
        println(if (failures == 0) "All scenarios verified." else "$failures scenario(s) failed.")
    }

    // ------------------------------------------------------------------ helpers

    private fun newGame(
        radius: Int,
        seed: Long,
        players: List<Player>,
        cityStates: Int,
        victories: List<String>,
        noBarbarians: Boolean,
        maxTurns: Int,
        mapType: String = MapType.pangaea,
        ruins: Boolean = false,
    ): GameInfo {
        val gp = GameParameters().apply {
            baseRuleset = MOD
            mods = LinkedHashSet()
            difficulty = "Discovery"
            speed = "Discovery"
            this.players = ArrayList(players)
            numberOfCityStates = cityStates
            minNumberOfCityStates = cityStates
            maxNumberOfCityStates = cityStates
            this.noBarbarians = noBarbarians
            victoryTypes = ArrayList(victories)
            this.maxTurns = maxTurns
            espionageEnabled = false
            nuclearWeaponsEnabled = false
            noStartBias = true
        }
        val mp = MapParameters().apply {
            type = mapType
            shape = MapShape.hexagonal
            mapSize = MapSize(radius)
            this.seed = seed
            noRuins = !ruins
            noNaturalWonders = true
        }
        return GameStarter.startNewGame(GameSetupInfo(gp, mp))
    }

    private fun GameInfo.human(): Civilization = getCivilization(HUMAN)!!

    /** Replaces every pending popup (tech/first-contact noise from the setup) with the scenario briefing. */
    private fun GameInfo.brief(civ: Civilization, eventName: String) {
        civ.popupAlerts.clear()
        civ.notifications.clear()
        civ.popupAlerts.add(PopupAlert(AlertType.Event, eventName))
    }

    /** Turns the civ's starting settler into a city, returns it. */
    private fun Civilization.foundCapital(): com.unciv.logic.city.City {
        val settler = units.getCivUnits().first { it.baseUnit.isCityFounder() }
        val city = addCity(settler.currentTile.position, settler)
        settler.destroy()
        return city
    }

    private fun Civilization.give(vararg techs: String) {
        for (t in techs) if (!tech.isResearched(t)) tech.addTechnology(t, showNotification = false)
    }

    private fun Civilization.spawn(unitName: String, near: Tile) {
        units.placeUnitNearTile(near.position, unitName)
            ?: error("Could not place $unitName near ${near.position}")
    }

    private fun Tile.isFlatLand() = isLand && !isImpassible() && !isHill() && terrainFeatures.isEmpty()

    private fun save(game: GameInfo, outDir: File, name: String) {
        game.currentPlayer = HUMAN
        val text = UncivFiles.gameInfoToString(game, forceZip = false)
        File(outDir, name).writeText(text)
        println("Wrote $name (${text.length} chars)")
    }

    // ------------------------------------------------------------------ scenarios

    /** S1: one civ alone, found a city, research, build a Monument. */
    private fun buildS1(outDir: File) {
        val game = newGame(
            radius = 6, seed = 101, players = listOf(Player(HUMAN, PlayerType.Human)),
            cityStates = 0, victories = listOf("S1 First steps"), noBarbarians = true, maxTurns = 100,
        )
        game.brief(game.human(), "S1 Briefing")
        save(game, outDir, "S1 First steps")
    }

    /** S2: city already founded, a worker, resources nearby; build farm, mine, Granary. */
    private fun buildS2(outDir: File) {
        val game = newGame(
            radius = 6, seed = 202, players = listOf(Player(HUMAN, PlayerType.Human)),
            cityStates = 0, victories = listOf("S2 Feed the city"), noBarbarians = true, maxTurns = 100,
        )
        val civ = game.human()
        val city = civ.foundCapital()
        city.population.setPopulation(2)
        city.cityConstructions.addBuilding("Monument")
        civ.give("Pottery", "Mining")
        val center = city.getCenterTile()
        val ring = center.getTilesInDistance(2).filter { it != center && it.isLand && it.resource == null }.toList()
        ring.firstOrNull { it.isFlatLand() && it.baseTerrain == Constants.grassland }?.setTileResource("Wheat")
        ring.firstOrNull { it.isHill() && it.resource == null }?.setTileResource("Iron")
        ring.firstOrNull { it.isFlatLand() && it.resource == null && it.baseTerrain == Constants.plains }?.setTileResource("Cattle")
        civ.spawn("Worker", center)
        city.reassignAllPopulation()
        game.brief(civ, "S2 Briefing")
        save(game, outDir, "S2 Feed the city")
    }

    /** S3: explore, meet a civ and a city-state, found a second city. */
    private fun buildS3(outDir: File) {
        val game = newGame(
            radius = 9, seed = 303,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Egypt", PlayerType.AI)),
            cityStates = 1, victories = listOf("S3 Explore the world"), noBarbarians = true, maxTurns = 150,
            mapType = MapType.pangaea, ruins = true,
        )
        val civ = game.human()
        val city = civ.foundCapital()
        city.population.setPopulation(3)
        city.cityConstructions.addBuilding("Monument")
        city.cityConstructions.addBuilding("Granary")
        civ.give("Pottery")
        val center = city.getCenterTile()
        civ.spawn("Scout", center)
        civ.spawn("Settler", center)
        city.reassignAllPopulation()
        game.brief(civ, "S3 Briefing")
        save(game, outDir, "S3 Explore the world")
    }

    /** S4: two cities, a barbarian camp nearby, hold 30 turns. */
    private fun buildS4(outDir: File) {
        val game = newGame(
            radius = 8, seed = 404, players = listOf(Player(HUMAN, PlayerType.Human)),
            cityStates = 0, victories = listOf("S4 Defend the border"), noBarbarians = false, maxTurns = 30,
        )
        val civ = game.human()
        val capital = civ.foundCapital()
        capital.population.setPopulation(3)
        capital.cityConstructions.addBuilding("Monument")
        capital.cityConstructions.addBuilding("Granary")
        civ.give("Pottery", "Mining", "Archery", "Bronze Working", "Masonry")
        val center = capital.getCenterTile()
        val secondSite = game.tileMap.getTilesAtDistance(center.position, 4)
            .firstOrNull { it.isFlatLand() && it.getTilesInDistance(1).all { t -> t.isLand } }
            ?: game.tileMap.getTilesAtDistance(center.position, 4).first { it.isLand && !it.isImpassible() }
        val second = civ.addCity(secondSite.position)
        second.population.setPopulation(2)
        civ.spawn("Archer", center)
        civ.spawn("Worker", center)
        civ.spawn("Warrior", secondSite)
        val campTile = game.tileMap.getTilesAtDistance(center.position, 6)
            .firstOrNull { it.isLand && !it.isImpassible() && it.getOwner() == null && it.isFlatLand() }
            ?: game.tileMap.getTilesAtDistance(center.position, 6).first { it.isLand && !it.isImpassible() && it.getOwner() == null }
        game.barbarians.createNewCamp(campTile)
        capital.reassignAllPopulation()
        second.reassignAllPopulation()
        game.brief(civ, "S4 Briefing")
        save(game, outDir, "S4 Defend the border")
    }

    /** S5: at war with Greece, whose single city is 6 tiles away; capture it. */
    private fun buildS5(outDir: File) {
        val game = newGame(
            radius = 8, seed = 505,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Greece", PlayerType.AI)),
            cityStates = 0, victories = listOf("S5 Take a city"), noBarbarians = true, maxTurns = 150,
        )
        val civ = game.human()
        val enemy = game.getCivilization("Greece")!!
        val capital = civ.foundCapital()
        capital.population.setPopulation(4)
        capital.cityConstructions.addBuilding("Monument")
        capital.cityConstructions.addBuilding("Granary")
        capital.cityConstructions.addBuilding("Barracks")
        civ.give("Pottery", "Mining", "Archery", "Bronze Working", "Iron Working", "The Wheel", "Mathematics")
        // Enemy city: move its settler to a spot 6 tiles from our capital if the generator put it further
        val enemySettler = enemy.units.getCivUnits().first { it.baseUnit.isCityFounder() }
        val site = game.tileMap.getTilesAtDistance(capital.getCenterTile().position, 6)
            .firstOrNull { it.isFlatLand() && it.getOwner() == null } ?: enemySettler.currentTile
        enemySettler.removeFromTile()
        enemySettler.putInTile(site)
        val enemyCity = enemy.foundCapital()
        enemyCity.population.setPopulation(4)
        enemy.give("Pottery", "Archery", "Bronze Working")
        enemy.spawn("Archer", enemyCity.getCenterTile())
        enemy.spawn("Spearman", enemyCity.getCenterTile())
        val center = capital.getCenterTile()
        civ.spawn("Swordsman", center)
        civ.spawn("Swordsman", center)
        civ.spawn("Archer", center)
        civ.spawn("Archer", center)
        civ.spawn("Catapult", center)
        civ.diplomacyFunctions.makeCivilizationsMeet(enemy)
        civ.getDiplomacyManager(enemy)!!.declareWar()
        capital.reassignAllPopulation()
        enemyCity.reassignAllPopulation()
        game.brief(civ, "S5 Briefing")
        save(game, outDir, "S5 Take a city")
    }

    /** S6: a short real game: two AIs, two city-states, ruins and barbarians, 150 turns. */
    private fun buildS6(outDir: File) {
        val game = newGame(
            radius = 11, seed = 606,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Egypt", PlayerType.AI), Player("Greece", PlayerType.AI)),
            cityStates = 2, victories = listOf("Domination", "Time"), noBarbarians = false, maxTurns = 150,
            mapType = MapType.pangaea, ruins = true,
        )
        game.brief(game.human(), "S6 Briefing")
        save(game, outDir, "S6 First empire")
    }
}
