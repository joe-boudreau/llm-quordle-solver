import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

const val IMAGE_FILENAME = "generated_quordle_art.png"

fun main() {
    println("Starting QuordleWebDriver...")
    val quordleDriver = QuordleWebDriver()

    val llmGuesser = LLMQuordleGuesser()
    val llmImageGenerator = LLMImageGenerator()

    try {
        quordleDriver.initializeDriver()

        var gameState = quordleDriver.parseGameState()

        while (gameState.isInProgress()) {

            println("Current Game State:")
            println(gameState)

            val nextGuess = llmGuesser.guessWord(gameState)
            quordleDriver.enterGuess(nextGuess)

            val currAttempts = gameState.numAttempts()
            gameState = quordleDriver.parseGameState()
            if (gameState.numAttempts() == currAttempts) {
                // the word was not accepted, remove the last 2 messages from LLM guesser
                llmGuesser.removeLastNMessages(2)
            }
        }

        val wordAnswers = quordleDriver.getWordAnswers()

        val systemMessage = llmGuesser.allMessages.first { it.role == ChatRole.System }

        val llmGuessResponses = llmGuesser.allMessages
            .filter { it.role == ChatRole.Assistant }
            .map {Json.decodeFromString<QuordleGuessResponse>(it.content?.trim() ?: "") }

        val userMessages = llmGuesser.allMessages
            .filter { it.role == ChatRole.User }

        val guessChat: List<Pair<ChatMessage, QuordleGuessResponse>> = userMessages.zip(llmGuessResponses)

        val finalMessages = mutableListOf<ChatMessage>()

        if (gameState.isSolved()) {
            val (imagePrompt, imageUrl) = llmImageGenerator.generateImageUsingWords(gameState.getFinalWords())
            downloadImage(imageUrl, IMAGE_FILENAME)
            finalMessages.addAll(getGameSolvedFinalMessages(imagePrompt, IMAGE_FILENAME))
        } else {
            finalMessages.addAll(getGameFailedFinalMessages())
        }

        saveGameState(
            gameState,
            wordAnswers,
            systemMessage,
            guessChat,
            finalMessages
        )
        val guesserStats = updateLLMGuesserStats(gameState, llmGuesser.modelId.toString())

        saveHtmlReplay(
            gameState,
            systemMessage,
            guessChat,
            finalMessages,
            guesserStats
        )

    } finally {
        quordleDriver.close()
    }
}

fun getGameSolvedFinalMessages(imagePrompt: String, imageFilename: String) = listOf(
    ChatMessage(
        role = ChatRole.User,
        content = "<p>Congratulations, you solved the Quordle today! Your prize is to create some art.</p>"
    ),
    ChatMessage(
        role = ChatRole.User,
        content = "<p>$imagePrompt</p>",
    ),
    ChatMessage(
        role = ChatRole.Assistant,
        content = "<p>Here you go!</p><img src=$imageFilename alt=\"Generated Image\" style=\"max-width: 100%; height: auto;\" />",
    )
)

fun getGameFailedFinalMessages() = listOf(
    ChatMessage(
        role = ChatRole.User,
        content = "<p>Unfortunately, you failed to solve the Quordle today.</p>"
    ),
)

fun updateLLMGuesserStats(gameState: GameState, modelId: String) {
    // Update LLM guesser stats based on the game state
    val statsFile = File("llm_guesser_stats.json")
    val stats = if (statsFile.exists()) {
        kotlinx.serialization.json.Json.decodeFromString<MutableMap<String, Int>>(statsFile.readText())
    } else {
        mutableMapOf()
    }

    // Increment the count for this model ID
    stats[modelId] = stats.getOrDefault(modelId, 0) + 1

    // Save updated stats back to file
    statsFile.writeText(kotlinx.serialization.json.Json.encodeToString(stats))
    println("LLM Guesser stats updated for model $modelId")
}

fun downloadImage(imageUrl: String?, imageFilename: String) {
    if (imageUrl.isNullOrEmpty()) {
        return
    }

    val destinationFile = File(imageFilename)
    try {
        val uri = URI.create(imageUrl)
        val connection = uri.toURL().openConnection()
        connection.connect()
        connection.getInputStream().use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        println("Image downloaded successfully to ${destinationFile.absolutePath}")
    } catch (e: Exception) {
        println("Failed to download image: ${e.message}")
        e.printStackTrace()
    }
}