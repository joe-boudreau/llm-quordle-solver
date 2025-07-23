import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val boardStates: List<BoardState>,
) {
    override fun toString() = "Attempts: ${numAttempts()} / 9 \n" +
            this.boardStates.withIndex().joinToString("\n\n") { (i, boardState) ->
                "Board ${i + 1}, Solved: ${boardState.isSolved()}, Guess Results:\n$boardState"
            }

    fun isSolved() = boardStates.all { it.isSolved() }

    fun isFailed() = numAttempts() >= 9 && !isSolved()

    fun numAttempts() = boardStates.maxOf {it.attempts.size}

    fun usedLettersAfterAttempt(attemptIndex: Int): Set<Char> {
        return boardStates[0].attempts
            .take(attemptIndex + 1)
            .flatMap { it.word.toCharArray().toList() }
            .toSet()
    }
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