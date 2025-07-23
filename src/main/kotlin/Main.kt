import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

fun main() {
    println("Starting Quordle...")
    val quordleDriver = QuordleWebDriver()
    val llmGuesser = LLMQuordleGuesser()
    val llmImageGenerator = LLMImageGenerator()

    try {
        quordleDriver.initializeDriver()

        var gameState = quordleDriver.parseGameState()
        //var (gameState, allMessages) = loadGameState()
        //llmGuesser.allMessages.addAll(allMessages.toMutableList())

        while (true) {
            // Print current state
            println("Current Game State:")
            println(gameState)

            if (gameState.isSolved()) {
                val systemMessage = llmGuesser.allMessages.first { it.role == ChatRole.System }

                val llmGuessResponses = llmGuesser.allMessages
                    .filter { it.role == ChatRole.Assistant }
                    .map {Json.decodeFromString<QuordleGuessResponse>(it.content?.trim() ?: "") }

                val finalMessages = mutableListOf<ChatMessage>()

                finalMessages.add(
                    ChatMessage(
                        role = ChatRole.User,
                        content = "<p>Congratulations, you solved the Quordle today! Your prize is to create some art.</p>"
                    )
                )

                val (imagePrompt, imageUrl) = llmImageGenerator.generateImageUsingWords(gameState.getFinalWords())

                // save image to disk
                val imageFile = File("generated_image.png")
                downloadImage(imageUrl, imageFile)

                finalMessages.add(
                    ChatMessage(
                        role = ChatRole.User,
                        content = "<p>$imagePrompt</p>",
                    )
                )

                finalMessages.add(
                    ChatMessage(
                        role = ChatRole.Assistant,
                        content = "<img src=generated_image.png alt=\"Generated Image\" style=\"max-width: 100%; height: auto;\" />",
                    )
                )

                saveGameState(gameState, systemMessage, llmGuessResponses, finalMessages)

                saveHtmlReplay(
                    gameState,
                    systemMessage,
                    llmGuessResponses,
                    finalMessages
                )
                updateLLMGuesserStats(gameState, llmGuesser.modelId.toString())
                println("All puzzles solved!")
                break
            }

            if (gameState.isFailed()) {
                //saveGameState(gameState, llmGuesser.allMessages)
                //saveHtmlReplay(gameState, llmGuesser.allMessages)
                updateLLMGuesserStats(gameState, llmGuesser.modelId.toString())
                println("You lose. Game over!")
                break
            }

            println("Making a guess...")
            val nextGuess = llmGuesser.guessWord(gameState)
            quordleDriver.enterGuess(nextGuess)
            val currAttempts = gameState.numAttempts()

            gameState = quordleDriver.parseGameState()
            if (gameState.numAttempts() == currAttempts) {
                // the word was not accepted, remove the last 2 messages from LLM guesser
                llmGuesser.removeLastNMessages(2)
            }
        }
    } finally {
        //Thread.sleep(60000) // lemme take a screenshot
        quordleDriver.close()
    }
}

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

fun downloadImage(imageUrl: String, destinationFile: File) {
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