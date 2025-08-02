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
private const val STATS_S3_KEY = "llm_guesser_stats.json"

class GuesserStatsRepository(private val s3Repository: S3BucketRepository? = null) {

    fun updateStats(gameState: GameState): GuesserStats {
        // Load existing stats
        val stats = loadStats()

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

        // Save updated stats
        saveStats(stats)
        return stats
    }

    fun loadStats(): GuesserStats {
        return if (s3Repository != null) {
            // Load from S3
            val content = s3Repository.downloadFile(STATS_S3_KEY)
            if (content != null) {
                kotlinx.serialization.json.Json.decodeFromString<GuesserStats>(content)
            } else {
                GuesserStats()
            }
        } else {
            // Load from local filesystem
            val statsFile = File(STATS_FILENAME)
            if (statsFile.exists()) {
                kotlinx.serialization.json.Json.decodeFromString<GuesserStats>(statsFile.readText())
            } else {
                GuesserStats()
            }
        }
    }

    private fun saveStats(stats: GuesserStats) {
        val jsonContent = kotlinx.serialization.json.Json.encodeToString(stats)

        if (s3Repository != null) {
            // Save to S3
            s3Repository.uploadFile(STATS_S3_KEY, jsonContent)
        } else {
            // Save to local filesystem
            val statsFile = File(STATS_FILENAME)
            statsFile.writeText(jsonContent)
        }
    }
}