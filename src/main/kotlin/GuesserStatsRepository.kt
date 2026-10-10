import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class DailyRunStats(
    val date: String,
    val numberOfGuesses: Int,
    val success: Boolean,
    val reasoningModel: String,
    val durationMillis: Long? = null,
    val reasoningEffort: String? = null,
    val isMainRun: Boolean = true,
)

@Serializable
data class GuesserStats(
    val dailyRuns: MutableList<DailyRunStats> = mutableListOf(),
)

private val STATS_FILENAME = "${OUTPUT_FILEPATH}llm_guesser_stats.json"
private const val STATS_S3_KEY = "llm_guesser_stats.json"
private val statsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

class GuesserStatsRepository(
    s3Repository: S3BucketRepository? = null,
    statsFile: File = File(STATS_FILENAME),
    private val store: StatsStore = if (s3Repository != null) {
        S3StatsStore(s3Repository, STATS_S3_KEY)
    } else {
        FileStatsStore(statsFile)
    },
) {

    fun updateStats(
        gameState: GameState,
        reasoningModel: String,
        duration: Duration? = null,
        runDate: LocalDate = LocalDate.now(),
        reasoningEffort: String? = null,
        isMainRun: Boolean = true,
    ): GuesserStats {
        val isWin = gameState.isSolved()
        val attempts = gameState.numAttempts()
        val dailyRun = DailyRunStats(
            date = runDate.toString(),
            numberOfGuesses = attempts,
            success = isWin,
            reasoningModel = reasoningModel,
            durationMillis = duration?.inWholeMilliseconds,
            reasoningEffort = reasoningEffort,
            isMainRun = isMainRun,
        )

        repeat(10) {
            val snapshot = store.read()
            val stats = decodeStats(snapshot)
            stats.dailyRuns.add(dailyRun)

            val content = statsJson.encodeToString(stats)
            if (store.write(content, snapshot?.version)) {
                return stats
            }
        }
        error("Stats changed during all 10 save attempts; result was not saved")
    }

    fun loadStats(): GuesserStats {
        return decodeStats(store.read())
    }

    private fun decodeStats(snapshot: StatsSnapshot?): GuesserStats =
        snapshot?.let { statsJson.decodeFromString<GuesserStats>(it.content) } ?: GuesserStats()
}
