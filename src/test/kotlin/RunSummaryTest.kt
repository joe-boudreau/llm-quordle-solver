import kotlin.test.Test
import kotlin.test.assertEquals

class RunSummaryTest {
    @Test
    fun `summary is derived from supplied history in date order`() {
        val runs = listOf(
            DailyRunStats("2026-10-10", 7, true, "model"),
            DailyRunStats("2026-10-08", 9, false, "model"),
            DailyRunStats("2026-10-09", 8, true, "model"),
        )
        assertEquals(RunSummary(2, 1, mapOf(7 to 1, 8 to 1), 2), summarizeRuns(runs))
        assertEquals(RunSummary(0, 0, emptyMap(), 0), summarizeRuns(emptyList()))
    }

    @Test
    fun `main replay summary excludes comparison runs`() {
        val runs = listOf(
            DailyRunStats("2026-10-09", 7, true, "main-model"),
            DailyRunStats("2026-10-10", 8, true, "main-model"),
            DailyRunStats("2026-10-10", 9, false, "comparison-model", isMainRun = false),
        )
        assertEquals(RunSummary(2, 0, mapOf(7 to 1, 8 to 1), 2), summarizeRuns(runs.filter { it.isMainRun }))
    }
}
