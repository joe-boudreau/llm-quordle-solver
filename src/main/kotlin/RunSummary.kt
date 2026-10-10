data class RunSummary(
    val winCount: Int,
    val lossCount: Int,
    val attemptsDistributionForWins: Map<Int, Int>,
    val currentStreak: Int,
)

fun summarizeRuns(runs: List<DailyRunStats>): RunSummary {
    val orderedRuns = runs.sortedBy { it.date }
    return RunSummary(
        winCount = runs.count { it.success },
        lossCount = runs.count { !it.success },
        attemptsDistributionForWins = runs.filter { it.success }.groupingBy { it.numberOfGuesses }.eachCount(),
        currentStreak = orderedRuns.asReversed().takeWhile { it.success }.size,
    )
}
