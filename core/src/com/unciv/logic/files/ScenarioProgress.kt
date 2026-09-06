package com.unciv.logic.files

import com.unciv.UncivGame
import com.unciv.logic.GameInfo

/**
 *  Knows which games are scenarios shipped by an installed mod (see [UncivFiles.getScenarioFiles]).
 *  Scenarios are tutorials: their guided tasks live per game, not in the device-wide tutorial memory
 *  (see [com.unciv.models.metadata.GameSettings.completedTutorialTasks]).
 */
object ScenarioProgress {
    /** The scenario files (path and modification time) the cached [scenarioGameIds] were read from */
    private var scenarioGameIdsKey: List<Pair<String, Long>>? = null
    private var scenarioGameIds: Set<String> = emptySet()

    /** gameIds of the scenarios shipped by installed mods. The previews are re-read only when the list of scenario
     *  files changes (a mod installed or updated from the mod manager during the session) - a rare, small read.
     *  Empty when no game files are available (unit tests, console launcher). */
    @Synchronized
    fun getScenarioGameIds(): Set<String> {
        if (!UncivGame.isCurrentInitialized() || !UncivGame.Current.isFilesInitialized()) return emptySet()
        val files = UncivGame.Current.files
        val scenarioFiles = try { files.getScenarioFiles().map { (file, _) -> file }.toList() } catch (_: Exception) { return scenarioGameIds }
        val key = scenarioFiles.map { it.path() to it.lastModified() }
        if (key != scenarioGameIdsKey) {
            scenarioGameIds = scenarioFiles.mapNotNull { file ->
                try { files.loadGamePreviewFromFile(file).gameId } catch (_: Exception) { null }
            }.toSet()
            scenarioGameIdsKey = key
        }
        return scenarioGameIds
    }

    /** True when [gameId] is the id of a scenario shipped by an installed mod */
    fun isScenarioGame(gameId: String): Boolean = gameId.isNotEmpty() && gameId in getScenarioGameIds()

    fun isScenarioGame(gameInfo: GameInfo?): Boolean = gameInfo != null && isScenarioGame(gameInfo.gameId)

    /** The game being played, when it is a scenario */
    fun currentScenarioGameId(): String? {
        val gameInfo = UncivGame.getGameInfoOrNull() ?: return null
        return if (isScenarioGame(gameInfo.gameId)) gameInfo.gameId else null
    }
}
