import com.aallam.openai.api.chat.ChatMessage
import java.io.File

fun saveGameState(gameState: GameState, allMessages: MutableList<ChatMessage>) {
    // Save game state to JSON file
    val gameStateFile = File("game_state.json")
    gameStateFile.writeText(kotlinx.serialization.json.Json.encodeToString(gameState))

    // Save chat messages to JSON file
    val messagesFile = File("chat_messages.json")
    messagesFile.writeText(kotlinx.serialization.json.Json.encodeToString(allMessages))

    println("Game state and messages saved.")
}

fun loadGameState(): Pair<GameState, MutableList<ChatMessage>> {
    // Load game state from JSON file
    val gameStateFile = File("game_state.json")
    if (!gameStateFile.exists()) {
        throw IllegalStateException("Game state file does not exist. Please save the game state first.")
    }

    val gameState = kotlinx.serialization.json.Json.decodeFromString<GameState>(gameStateFile.readText())

    // Load chat messages from JSON file
    val messagesFile = File("chat_messages.json")
    if (!messagesFile.exists()) {
        throw IllegalStateException("Chat messages file does not exist. Please save the chat messages first.")
    }
    val allMessages = kotlinx.serialization.json.Json.decodeFromString<MutableList<ChatMessage>>(messagesFile.readText())

    return Pair(gameState, allMessages)
}