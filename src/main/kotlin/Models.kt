import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val boardStates: List<BoardState>,
) {
    override fun toString() = "Attempts: ${numAttempts()} / 9 \n" +
            this.boardStates.withIndex().joinToString("\n\n") { (i, boardState) ->
                "Board ${i + 1}, Solved: ${boardState.isSolved()}, Guess Results:\n$boardState"
            } + "\n" +
            "Used & Present Letters: ${usedAndPresentLetters.sorted().joinToString(", ")}\n" +
            "Used & Absent Letters: ${usedAndAbsentLetters.sorted().joinToString(", ")}\n" +
            "Unused Letters: ${unusedLetters.sorted().joinToString(", ")}"

    fun isSolved() = boardStates.all { it.isSolved() }

    fun isFailed() = numAttempts() >= 9 && !isSolved()

    fun numAttempts() = boardStates.maxOf {it.attempts.size}

    fun usedLettersAfterAttempt(attemptIndex: Int): Set<Char> {
        return boardStates[0].attempts
            .take(attemptIndex + 1)
            .flatMap { it.word.toCharArray().toList() }
            .toSet()
    }

    val usedLetters: Set<Char> = boardStates
        .flatMap { it.attempts }
        .flatMap { it.word.toCharArray().toList() }
        .toSet()
    val usedAndPresentLetters: Set<Char> = boardStates
        .flatMap { it.attempts }
        .flatMap { attempt ->
            attempt.word.toCharArray()
                .zip(attempt.feedback)
                .filter { (_, state) -> state == TileState.PRESENT || state == TileState.CORRECT }
                .map { (char, _) -> char }
        }
        .toSet()
    val usedAndAbsentLetters: Set<Char> = usedLetters - usedAndPresentLetters
    val allLetters: Set<Char> = ('A'..'Z').toSet()
    val unusedLetters: Set<Char> = allLetters - usedLetters

    fun getFinalWords(): List<String> {
        return boardStates.map { it.attempts.lastOrNull()?.word ?: "" }
    }

    fun isInProgress() = !isSolved() && !isFailed()
}

@Serializable
data class BoardState(
    val attempts: List<Attempt>,
) {
    override fun toString() = attempts.joinToString("\n")
    fun isSolved() = attempts.any { it.isCorrect() }
}

@Serializable
data class Attempt(
    val word: String,
    val feedback: List<TileState>
) {
    override fun toString() =
        "$word => ${word
                        .toCharArray()
                        .zip(feedback)
                        .joinToString(", ") 
                        { (char, state) -> "$char $state" }
        }"

    fun isCorrect() = feedback.all { it == TileState.CORRECT }
}

@Serializable
enum class TileState {
    CORRECT,     // Green
    PRESENT,     // Yellow
    ABSENT,      // Grey
    EMPTY;       // No guess yet

    override fun toString(): String {
        return when (this) {
            CORRECT -> "✓"
            PRESENT -> "↔"
            ABSENT -> "✕"
            EMPTY -> " "
        }
    }
}

@Serializable
data class QuordleGuessResponse(
    @SerialName("reasoning")
    val reasoning: String,
    @SerialName("final_answer")
    val finalAnswer: String
)