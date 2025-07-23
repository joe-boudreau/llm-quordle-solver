import com.aallam.openai.api.chat.ChatMessage
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GameReplayData(
    val gameState: GameState,
    val systemMessage: ChatMessage,
    val llmGuessResponses: List<QuordleGuessResponse>,
    val finalMessages: List<ChatMessage>
){
}

private const val REPLAY_DATA_FILENAME = "game_replay_data.json"

fun saveGameState(
    gameState: GameState,
    systemMessage: ChatMessage,
    llmGuessResponses: List<QuordleGuessResponse>,
    finalMessages: List<ChatMessage>,
    ) {
    // save everything to one JSON file
    val gameStateFile = File(REPLAY_DATA_FILENAME)
    val gameReplayData = GameReplayData(
        gameState = gameState,
        systemMessage = systemMessage,
        llmGuessResponses = llmGuessResponses,
        finalMessages = finalMessages
    )
    gameStateFile.writeText(kotlinx.serialization.json.Json.encodeToString(gameReplayData))
}

fun loadGameState(): GameReplayData {
    // load everything from one JSON file
    val gameStateFile = File(REPLAY_DATA_FILENAME)
    if (!gameStateFile.exists()) {
        throw IllegalStateException("Game state file does not exist: ${gameStateFile.absolutePath}")
    }
    return kotlinx.serialization.json.Json.decodeFromString(gameStateFile.readText())
}