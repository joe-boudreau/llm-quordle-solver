import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class GuesserStats(
    var winCount: Int = 0,
    var lossCount: Int = 0,
    val attemptsDistributionForWins: MutableMap<Int, Int> = (1..9).associateWith { 0 }.toMutableMap(),
    var currentStreak: Int = 0,
)

private val STATS_FILENAME = "${OUTPUT_FILEPATH}llm_guesser_stats.json"

class GuesserStatsRepository {

    fun updateStats(gameState: GameState): GuesserStats {
        // Update LLM guesser stats based on the game state
        val statsFile = File(STATS_FILENAME)
        val stats = if (statsFile.exists()) {
            kotlinx.serialization.json.Json.decodeFromString<GuesserStats>(statsFile.readText())
        } else {
            GuesserStats()
        }

        val isWin = gameState.isSolved()
        val attempts = gameState.numAttempts()
        if (isWin) {
            stats.winCount++
            stats.attemptsDistributionForWins[attempts] = stats.attemptsDistributionForWins[attempts]!! + 1
            stats.currentStreak++
        } else {
            stats.lossCount++
            stats.currentStreak = 0
        }

        // Save updated stats back to file
        statsFile.writeText(kotlinx.serialization.json.Json.encodeToString(stats))
        return stats
    }

    fun loadStats(): GuesserStats {
        val statsFile = File(STATS_FILENAME)
        return if (statsFile.exists()) {
            kotlinx.serialization.json.Json.decodeFromString<GuesserStats>(statsFile.readText())
        } else {
            GuesserStats()
        }
    }
}