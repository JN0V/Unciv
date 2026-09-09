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
import java.util.UUID

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
        buildS7(outDir)
        buildS8(outDir)
        buildS9(outDir)
        buildS10(outDir)
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
                    if (civ == human) println("  gold=${civ.gold} resources=${civ.getCivResourcesByName().filterValues { amount -> amount != 0 }}")
                    if (civ == human) for (city in civ.cities) {
                        city.cityStats.update()
                        val stats = city.cityStats.currentCityStats
                        println("  ${city.name}: pop ${city.population.population} owned tiles ${city.getTiles().count()}" +
                            " food ${stats.food.toInt()} prod ${stats.production.toInt()} science ${stats.science.toInt()}" +
                            " culture ${stats.culture.toInt()} gold ${stats.gold.toInt()}" +
                            " river=${city.getCenterTile().isAdjacentToRiver()} coastal=${city.getCenterTile().isAdjacentToCoast()}" +
                            " specialists=${city.population.specialistAllocations}")
                        // For each resource: is it visible to the human (its revealedBy tech known)? A hidden one
                        // means the briefing promises a tile the player cannot see.
                        val near = city.getCenterTile().getTilesInDistance(2).filter { it.resource != null }
                            .joinToString { tile ->
                                val revealedBy = tile.tileResource?.revealedBy
                                val visibility = when {
                                    revealedBy == null -> "visible"
                                    human.tech.isResearched(revealedBy) -> "visible ($revealedBy known)"
                                    else -> "HIDDEN (needs $revealedBy)"
                                }
                                "${tile.resource}@${tile.aerialDistanceTo(city.getCenterTile())} $visibility"
                            }
                        println("  resources within 2 of ${city.name}: $near")
                    }
                    val dist = if (civ != human && civ.cities.isNotEmpty() && human.cities.isNotEmpty())
                        " dist=" + civ.getCapital()!!.getCenterTile().aerialDistanceTo(human.getCapital()!!.getCenterTile()) else ""
                    println("  ${civ.civID} [${civ.playerType}]${if (civ.isCityState) " city-state" else ""} techs=${civ.tech.techsResearched.size} cities=[$cities] units=$units war=${civ.diplomacy.values.filter { it.diplomaticStatus == com.unciv.logic.civilization.diplomacy.DiplomaticStatus.War }.map { it.otherCivName }}$dist")
                }
                println("  camps=${game.barbarians.encampments.map { it.position }} alerts=${human.popupAlerts.map { it.value }}")
                // nextTurn() runs every AI and stops at the (idle) human: one call = one full round
                repeat(turns) { game.nextTurn() }
                // How much the idle player suffered: city health and enemies inside the borders say whether a
                // "defend the border" scenario actually threatens anyone
                val health = human.cities.joinToString { "${it.name} ${it.health}" }
                val hostiles = game.tileMap.values.count { tile ->
                    tile.getUnits().any { it.civ.isBarbarian || it.civ.isAtWarWith(human) } && tile.getOwner() == human
                }
                println("  after: cities=[$health], enemy units inside our borders=$hostiles, camps=${game.barbarians.encampments.size}")
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

    /** Puts [resource] on a land tile next to [center] (distance 1, else 2), reshaping the terrain when no tile fits:
     *  [baseTerrain] (or any) with a Hill feature when [hill], flat otherwise. Returns the tile. */
    private fun GameInfo.ensureResource(center: Tile, resource: String, baseTerrain: String?, hill: Boolean): Tile {
        val candidates = center.getTilesInDistance(2)
            .filter { it != center && it.isLand && it.resource == null && it.naturalWonder == null }
            .sortedBy { it.aerialDistanceTo(center) }
        fun Tile.fits() = (baseTerrain == null || this.baseTerrain == baseTerrain) && isHill() == hill && terrainFeatures.all { it == Constants.hill }
        // Next to the city first (the player sees it at once and the city works it), reshaping if needed
        val adjacent = candidates.filter { it.aerialDistanceTo(center) == 1 }
        val tile = adjacent.firstOrNull { it.fits() }
            ?: adjacent.firstOrNull { it.isHill() == hill }
            ?: adjacent.firstOrNull()
            ?: candidates.firstOrNull { it.fits() }
            ?: candidates.first()
        if (baseTerrain != null && tile.baseTerrain != baseTerrain) tile.setBaseTerrain(ruleset.terrains[baseTerrain]!!)
        val features: List<String> = if (hill) arrayListOf(Constants.hill) else ArrayList()  // a plain ArrayList: singleton lists do not survive the save
        if (tile.terrainFeatures != features) tile.setTerrainFeatures(features)
        tile.setTileResource(ruleset.tileResources[resource]!!, majorDeposit = true)
        return tile
    }

    /** Puts [resource] on a Coast tile next to [center] (distance 1, else 2). The capital must already be coastal. */
    private fun GameInfo.ensureWaterResource(center: Tile, resource: String): Tile {
        val tile = center.getTilesInDistance(2)
            .filter { it.baseTerrain == Constants.coast && it.resource == null && it.naturalWonder == null }
            .sortedBy { it.aerialDistanceTo(center) }
            .firstOrNull() ?: error("No free Coast tile within 2 of ${center.position}")
        tile.setTileResource(ruleset.tileResources[resource]!!, majorDeposit = true)
        return tile
    }

    /** Every Coast tile a Trireme starting next to [from] can reach without ever entering the Ocean. */
    private fun coastReachableFrom(from: Tile): Set<Tile> {
        val seen = HashSet<Tile>()
        val queue = ArrayDeque<Tile>()
        for (tile in from.neighbors) if (tile.baseTerrain == Constants.coast && seen.add(tile)) queue.add(tile)
        while (queue.isNotEmpty())
            for (next in queue.removeFirst().neighbors)
                if (next.baseTerrain == Constants.coast && seen.add(next)) queue.add(next)
        return seen
    }

    /** Gives [city] the two rings around it and makes sure it feeds itself.
     *  Map generation is not reproducible tile for tile even with a fixed seed, so a city that starts
     *  bigger than the generator's default must be given room to work, then shrunk if it still starves:
     *  a briefing that promises a prosperous capital must never open on a starving one. */
    private fun feed(city: com.unciv.logic.city.City, minPopulation: Int) {
        for (tile in city.getCenterTile().getTilesInDistance(2))
            if (tile.getOwner() == null) city.expansion.takeOwnership(tile)
        city.reassignPopulation()
        city.cityStats.update()
        while (city.cityStats.currentCityStats.food < 0 && city.population.population > minPopulation) {
            city.population.setPopulation(city.population.population - 1)
            city.reassignPopulation()
            city.cityStats.update()
        }
        require(city.cityStats.currentCityStats.food >= 0) {
            "${city.name} starves (${city.cityStats.currentCityStats.food} Food) at population ${city.population.population}"
        }
    }

    /** Moves every unit of [civ] next to [target] (the generator's start position is not always what a scenario needs). */
    private fun relocateUnits(civ: Civilization, target: Tile) {
        val units = civ.units.getCivUnits().toList()
        for (unit in units) unit.removeFromTile()
        for (unit in units) {
            val tile = target.getTilesInDistance(3).firstOrNull { unit.movement.canMoveTo(it) }
                ?: error("No room for ${unit.name} near ${target.position}")
            unit.putInTile(tile)
        }
    }

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
        // A stable id derived from the scenario name: rebuilding the mod must not lose the player's
        // "Completed" marks (GameSettings.wonGameIds) nor the per-scenario tutorial state, both keyed by gameId.
        game.gameId = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)).toString()
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
        // The briefing promises Wheat, Cattle and Iron next to the city: put them there whatever the map looks like
        game.ensureResource(center, "Wheat", Constants.plains, hill = false)
        game.ensureResource(center, "Cattle", Constants.grassland, hill = false)
        game.ensureResource(center, "Iron", null, hill = true)
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
            cityStates = 0, victories = listOf("S4 Defend the border"), noBarbarians = false, maxTurns = 15,
        )
        val civ = game.human()
        val campTilesSoFar = ArrayList<Tile>()
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
        // Two camps, so the raids keep coming over the 15 turns instead of ending with the first camp
        val campTiles = listOf(6, 5).mapNotNull { distance ->
            game.tileMap.getTilesAtDistance(center.position, distance)
                .filter { it.isLand && !it.isImpassible() && it.getOwner() == null }
                .filter { tile -> campTilesSoFar.none { it.aerialDistanceTo(tile) < 4 } }
                .sortedByDescending { if (it.isFlatLand()) 1 else 0 }
                .firstOrNull()
                ?.also { campTilesSoFar.add(it) }
        }
        require(campTiles.size == 2) { "S4: could not place two barbarian camps" }
        for (tile in campTiles) game.barbarians.createNewCamp(tile)
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
            .firstOrNull { it.isFlatLand() && it.getOwner() == null } ?: error("S5: no flat free tile at distance 6")
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
        enemy.getDiplomacyManager(civ)!!.declareWar()  // Greece is the aggressor
        capital.reassignAllPopulation()
        enemyCity.reassignAllPopulation()
        game.brief(civ, "S5 Briefing")
        save(game, outDir, "S5 Take a city")
    }

    /** S6: a coastal capital with Fish; work the sea, then sail to the people on the next island. */
    private fun buildS6(outDir: File) {
        val game = newGame(
            radius = 8, seed = 616,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Greece", PlayerType.AI)),
            cityStates = 0, victories = listOf("S6 Take to the sea"), noBarbarians = true, maxTurns = 100,
            mapType = MapType.archipelago,
        )
        val civ = game.human()
        // The briefing promises a city on the shore: move the whole start to a coastal tile if the generator did not
        val start = civ.units.getCivUnits().first { it.baseUnit.isCityFounder() }.currentTile
        if (!start.isAdjacentToCoast()) {
            val shore = start.getTilesInDistance(6)
                .filter { it.isLand && !it.isImpassible() && it.isAdjacentToCoast() && it.getOwner() == null }
                .minByOrNull { it.aerialDistanceTo(start) } ?: error("S6: no coastal tile near the start")
            relocateUnits(civ, shore)
        }
        val capital = civ.foundCapital()
        val center = capital.getCenterTile()
        require(center.isAdjacentToCoast()) { "S6: the capital is not coastal" }
        capital.population.setPopulation(4)
        capital.cityConstructions.addBuilding("Monument")
        capital.cityConstructions.addBuilding("Granary")
        // Sailing: Work Boats, Fishing Boats, Trireme. Optics: Lighthouse and embarking. Compass: Harbor.
        civ.give("Pottery", "Sailing", "Optics", "Compass")
        civ.addGold(200)
        // Fish next to the city, so the Work Boat has something to improve from the first turns
        val fish = game.ensureWaterResource(center, "Fish")
        capital.expansion.takeOwnership(fish)
        civ.spawn("Work Boats", center)
        civ.spawn("Worker", center)
        // Greece lives across the water, on a shore a Trireme can reach without ever crossing the Ocean
        val reachable = coastReachableFrom(center)
        val enemy = game.getCivilization("Greece")!!
        val site = game.tileMap.values.asSequence()
            .filter { it.isLand && !it.isImpassible() && it.getOwner() == null }
            .filter { it.getContinent() != center.getContinent() }
            .filter { it.aerialDistanceTo(center) in 5..10 }
            .filter { tile -> tile.neighbors.any { it in reachable } }
            .sortedBy { it.aerialDistanceTo(center) }
            .firstOrNull() ?: error("S6: no other island a Trireme can reach")
        relocateUnits(enemy, site)
        val enemyCity = enemy.foundCapital()
        enemyCity.population.setPopulation(3)
        enemy.give("Pottery", "Sailing")
        capital.reassignAllPopulation()
        feed(capital, minPopulation = 3)
        enemyCity.reassignAllPopulation()
        game.brief(civ, "S6 Briefing")
        save(game, outDir, "S6 Take to the sea")
    }

    /** S7: upgrade to Legions, research Engineering (a new era) for the Fort, spend the Great General on a Citadel. */
    private fun buildS7(outDir: File) {
        val game = newGame(
            radius = 8, seed = 717,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Greece", PlayerType.AI)),
            cityStates = 0, victories = listOf("S7 Forge an army"), noBarbarians = true, maxTurns = 100,
        )
        val civ = game.human()
        val enemy = game.getCivilization("Greece")!!
        val capital = civ.foundCapital()
        capital.population.setPopulation(6)
        for (building in listOf("Monument", "Granary", "Walls", "Barracks", "Library"))
            capital.cityConstructions.addBuilding(building)
        // Everything up to the Classical era: Engineering (Medieval, unlocks the Fort) is what the player researches
        civ.give("Pottery", "Mining", "Masonry", "Archery", "Bronze Working", "Writing", "The Wheel",
            "Iron Working", "Mathematics", "Construction")
        civ.addGold(400)
        // Engineering is the scenario's research (a new era, and the Fort with it): pre-selected so the
        // very first "pick a technology" prompt does not send the player down another branch
        civ.tech.techsToResearch.add("Engineering")
        val center = capital.getCenterTile()
        // The Legion needs Iron: the hill is ours and the Mine is already dug, so the upgrade works from turn 1
        val iron = game.ensureResource(center, "Iron", null, hill = true)
        capital.expansion.takeOwnership(iron)
        iron.setImprovement("Mine")
        civ.spawn("Warrior", center)
        civ.spawn("Warrior", center)
        civ.spawn("Archer", center)
        civ.spawn("Worker", center)
        // The Citadel must be reachable inside the scenario: the General is there from the start
        civ.spawn("Great General", center)
        // Greece: same era, walls, a small army, and it declared war - a rival at the player's own pace
        val site = game.tileMap.getTilesAtDistance(center.position, 8).firstOrNull { it.isFlatLand() && it.getOwner() == null }
            ?: game.tileMap.getTilesAtDistance(center.position, 8).first { it.isLand && !it.isImpassible() && it.getOwner() == null }
        relocateUnits(enemy, site)
        val enemyCity = enemy.foundCapital()
        enemyCity.population.setPopulation(5)
        enemyCity.cityConstructions.addBuilding("Walls")
        enemy.give("Pottery", "Mining", "Masonry", "Archery", "Bronze Working")
        val enemyCenter = enemyCity.getCenterTile()
        enemy.spawn("Spearman", enemyCenter)
        enemy.spawn("Spearman", enemyCenter)
        enemy.spawn("Archer", enemyCenter)
        civ.diplomacyFunctions.makeCivilizationsMeet(enemy)
        enemy.getDiplomacyManager(civ)!!.declareWar()  // Greece is the aggressor
        capital.reassignAllPopulation()
        feed(capital, minPopulation = 4)
        enemyCity.reassignAllPopulation()
        game.brief(civ, "S7 Briefing")
        save(game, outDir, "S7 Forge an army")
    }

    /** S8: a peaceful builder scenario - a wonder, an Academy, the Water Mill on its river, a road, a luxury. */
    private fun buildS8(outDir: File) {
        val game = newGame(
            radius = 8, seed = 818, players = listOf(Player(HUMAN, PlayerType.Human)),
            cityStates = 0, victories = listOf("S8 Make it prosper"), noBarbarians = true, maxTurns = 100,
        )
        val civ = game.human()
        val capital = civ.foundCapital()
        val center = capital.getCenterTile()
        capital.population.setPopulation(8)
        for (building in listOf("Monument", "Granary", "Library", "University"))
            capital.cityConstructions.addBuilding(building)
        civ.give("Pottery", "Mining", "Animal Husbandry", "Masonry", "Calendar", "Writing", "The Wheel",
            "Trapping", "Mathematics", "Construction", "Philosophy", "Civil Service", "Theology", "Education")
        civ.addGold(300)
        // The Water Mill demands a river along the city: dig one if the generator put the capital away from water
        if (!center.isAdjacentToRiver()) {
            val neighbour = center.neighbors.first { it.isLand && !it.isImpassible() }
            center.setConnectedByRiver(neighbour, true)
        }
        require(center.isAdjacentToRiver()) { "S8: the capital is not on a river" }
        // A luxury next to the city: Wine, improved by a Plantation (Calendar is known)
        val wine = game.ensureResource(center, "Wine", Constants.grassland, hill = false)
        capital.expansion.takeOwnership(wine)
        // Second city 4 tiles away (3 free tiles between): the road the scenario asks for is 3 tiles long
        val site = game.tileMap.getTilesAtDistance(center.position, 4)
            .firstOrNull { it.isFlatLand() && it.getTilesInDistance(1).all { t -> t.isLand } }
            ?: game.tileMap.getTilesAtDistance(center.position, 4).first { it.isLand && !it.isImpassible() }
        val second = civ.addCity(site.position)
        second.population.setPopulation(3)
        second.cityConstructions.addBuilding("Monument")
        // Own every tile on the way, so the three road tiles are inside our borders and can be counted
        var walk = center
        var steps = 0
        while (walk.aerialDistanceTo(site) > 1 && steps++ < 10) {
            walk = walk.neighbors.filter { it.isLand && !it.isImpassible() }
                .minByOrNull { it.aerialDistanceTo(site) } ?: break
            if (walk != site && walk.getOwner() == null) capital.expansion.takeOwnership(walk)
        }
        civ.spawn("Worker", center)
        civ.spawn("Worker", site)
        civ.spawn("Warrior", center)
        capital.reassignAllPopulation()
        second.reassignAllPopulation()
        // Two scholars in the University: a Great Scientist arrives within a few turns, the Academy with it
        capital.manualSpecialists = true
        capital.population.specialistAllocations.clear()
        capital.population.specialistAllocations.add("Scientist", 2)
        // reassignPopulation keeps the manual specialists and puts every other citizen back on a tile;
        // unassignExtraPopulation alone left them idle and the city starved (-4 Food)
        feed(capital, minPopulation = 6)
        civ.greatPeople.greatPersonPointsCounter.add("Great Scientist", 40)
        game.brief(civ, "S8 Briefing")
        save(game, outDir, "S8 Make it prosper")
    }

    /** S9: the short full game - one rival, one city-state, 80 turns. */
    private fun buildS9(outDir: File) {
        val game = newGame(
            radius = 9, seed = 919,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Egypt", PlayerType.AI)),
            cityStates = 1, victories = listOf("Domination", "Time"), noBarbarians = false, maxTurns = 80,
            mapType = MapType.pangaea, ruins = true,
        )
        game.brief(game.human(), "S9 Briefing")
        save(game, outDir, "S9 First empire")
    }

    /** S10: the long full game (was S6): two AIs, two city-states, ruins and barbarians, 150 turns. */
    private fun buildS10(outDir: File) {
        val game = newGame(
            radius = 11, seed = 606,
            players = listOf(Player(HUMAN, PlayerType.Human), Player("Egypt", PlayerType.AI), Player("Greece", PlayerType.AI)),
            cityStates = 2, victories = listOf("Domination", "Time"), noBarbarians = false, maxTurns = 150,
            mapType = MapType.pangaea, ruins = true,
        )
        game.brief(game.human(), "S10 Briefing")
        save(game, outDir, "S10 Great empire")
    }
}
