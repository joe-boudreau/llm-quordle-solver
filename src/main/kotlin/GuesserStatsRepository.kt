import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DailyRunStats(
    val date: String,
    val numberOfGuesses: Int,
    val success: Boolean,
    val reasoningModel: String,
)

@Serializable
data class GuesserStats(
    var winCount: Int = 0,
    var lossCount: Int = 0,
    val attemptsDistributionForWins: MutableMap<Int, Int> = (1..9).associateWith { 0 }.toMutableMap(),
    var currentStreak: Int = 0,
    val dailyRuns: MutableList<DailyRunStats> = mutableListOf(),
)

private val STATS_FILENAME = "${OUTPUT_FILEPATH}llm_guesser_stats.json"
private const val STATS_S3_KEY = "llm_guesser_stats.json"

class GuesserStatsRepository(
    private val s3Repository: S3BucketRepository? = null,
    private val statsFile: File = File(STATS_FILENAME),
) {

    fun updateStats(
        gameState: GameState,
        reasoningModel: String,
        runDate: LocalDate = LocalDate.now(),
    ): GuesserStats {
        // Load existing stats
        val stats = loadStats()

        val isWin = gameState.isSolved()
        val attempts = gameState.numAttempts()
        stats.dailyRuns.add(
            DailyRunStats(
                date = runDate.toString(),
                numberOfGuesses = attempts,
                success = isWin,
                reasoningModel = reasoningModel,
            )
        )

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
            statsFile.writeText(jsonContent)
        }
    }
}
