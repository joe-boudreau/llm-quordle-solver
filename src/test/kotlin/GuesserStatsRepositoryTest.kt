import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GuesserStatsRepositoryTest {

    @Test
    fun `updateStats records successful daily run metadata`() = withStatsRepository { repository ->
        val gameState = solvedGameState(numberOfGuesses = 4)

        repository.updateStats(
            gameState = gameState,
            reasoningModel = "test-reasoning-model",
            duration = 95.seconds + 250.milliseconds,
            reasoningEffort = "high",
            runDate = LocalDate.parse("2026-09-08"),
        )
        val stats = repository.loadStats()

        assertEquals(1, stats.winCount)
        assertEquals(0, stats.lossCount)
        assertEquals(1, stats.dailyRuns.size)
        assertEquals("2026-09-08", stats.dailyRuns.single().date)
        assertEquals(4, stats.dailyRuns.single().numberOfGuesses)
        assertTrue(stats.dailyRuns.single().success)
        assertEquals("test-reasoning-model", stats.dailyRuns.single().reasoningModel)
        assertEquals(95_250L, stats.dailyRuns.single().durationMillis)
        assertEquals("high", stats.dailyRuns.single().reasoningEffort)
    }

    @Test
    fun `updateStats records failed daily run metadata`() = withStatsRepository { repository ->
        val gameState = failedGameState()

        val stats = repository.updateStats(
            gameState = gameState,
            reasoningModel = "test-reasoning-model",
            runDate = LocalDate.parse("2026-09-09"),
            reasoningEffort = "medium",
        )

        assertEquals(0, stats.winCount)
        assertEquals(1, stats.lossCount)
        assertEquals(9, stats.dailyRuns.single().numberOfGuesses)
        assertFalse(stats.dailyRuns.single().success)
        assertEquals("medium", repository.loadStats().dailyRuns.single().reasoningEffort)
    }

    @Test
    fun `updateStats appends each run to existing history`() = withStatsRepository { repository ->
        repository.updateStats(
            gameState = solvedGameState(numberOfGuesses = 5),
            reasoningModel = "first-model",
            reasoningEffort = "high",
            runDate = LocalDate.parse("2026-09-08"),
        )
        repository.updateStats(
            gameState = failedGameState(),
            reasoningModel = "second-model",
            reasoningEffort = "low",
            runDate = LocalDate.parse("2026-09-09"),
        )

        val dailyRuns = repository.loadStats().dailyRuns

        assertEquals(2, dailyRuns.size)
        assertEquals(listOf("2026-09-08", "2026-09-09"), dailyRuns.map { it.date })
        assertEquals(listOf("first-model", "second-model"), dailyRuns.map { it.reasoningModel })
        assertEquals(listOf("high", "low"), dailyRuns.map { it.reasoningEffort })
    }

    @Test
    fun `loadStats accepts legacy JSON without daily runs`() {
        val statsFile = Files.createTempFile("legacy-guesser-stats", ".json").toFile()
        try {
            statsFile.writeText(
                """
                {
                  "winCount": 2,
                  "lossCount": 1,
                  "attemptsDistributionForWins": {"1": 0, "2": 0, "3": 0, "4": 1, "5": 1, "6": 0, "7": 0, "8": 0, "9": 0},
                  "currentStreak": 1
                }
                """.trimIndent()
            )

            val stats = GuesserStatsRepository(statsFile = statsFile).loadStats()

            assertEquals(2, stats.winCount)
            assertEquals(1, stats.lossCount)
            assertTrue(stats.dailyRuns.isEmpty())
        } finally {
            statsFile.delete()
        }
    }

    @Test
    fun `loadStats accepts legacy daily runs without duration or reasoning effort`() {
        val statsFile = Files.createTempFile("legacy-daily-runs", ".json").toFile()
        try {
            statsFile.writeText(
                """
                {
                  "winCount": 1,
                  "dailyRuns": [{"date": "2026-09-08", "numberOfGuesses": 6, "success": true, "reasoningModel": "old-model"}]
                }
                """.trimIndent()
            )

            val stats = GuesserStatsRepository(statsFile = statsFile).loadStats()

            assertNull(stats.dailyRuns.single().durationMillis)
            assertNull(stats.dailyRuns.single().reasoningEffort)
        } finally {
            statsFile.delete()
        }
    }

    private fun withStatsRepository(block: (GuesserStatsRepository) -> Unit) {
        val statsFile = Files.createTempFile("guesser-stats", ".json").toFile()
        statsFile.delete()
        try {
            block(GuesserStatsRepository(statsFile = statsFile))
        } finally {
            statsFile.delete()
        }
    }

    private fun solvedGameState(numberOfGuesses: Int): GameState {
        val guesses = (1 until numberOfGuesses).map { index ->
            Attempt("TEST$index", List(5) { TileState.ABSENT })
        } + Attempt("SOLVE", List(5) { TileState.CORRECT })
        return GameState(List(4) { BoardState(guesses) })
    }

    private fun failedGameState(): GameState {
        val guesses = (1..9).map { index ->
            Attempt("FAIL$index", List(5) { TileState.ABSENT })
        }
        return GameState(List(4) { BoardState(guesses) })
    }
}
