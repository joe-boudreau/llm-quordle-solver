import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

val OUTPUT_FILEPATH = System.getenv("OUTPUT_FILEPATH") ?: "./"
const val IMAGE_FILENAME = "generated_quordle_art.png"

fun main() {
    println("Starting QuordleWebDriver...")
    // create the output directory if it doesn't exist
    val outputDir = File(OUTPUT_FILEPATH)
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    // Initialize S3 repository if bucket name is provided
    val s3BucketName = System.getenv("S3_BUCKET_NAME")
    val s3Repository = if (!s3BucketName.isNullOrBlank()) {
        println("Using S3 bucket: $s3BucketName")
        S3BucketRepository(s3BucketName)
    } else {
        println("Using local filesystem")
        null
    }

    val quordleDriver = QuordleWebDriver()
    val llmGuesser = LLMQuordleGuesser()
    val llmImageGenerator = LLMImageGenerator()
    val llmGuesserStatsRepository = GuesserStatsRepository(s3Repository)

    try {
        quordleDriver.initializeDriver()

        //var gameState = quordleDriver.parseGameState()
        val gameReplayData = loadGameState()
        var gameState = gameReplayData.gameState

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

        val guesserStats = llmGuesserStatsRepository.updateStats(gameState)

        if (gameState.isSolved()) {
            val (imagePrompt, imageUrl) = llmImageGenerator.generateImageUsingWords(gameState.getFinalWords())
            downloadImage(imageUrl, IMAGE_FILENAME, s3Repository)
            finalMessages.addAll(getGameSolvedFinalMessages(imagePrompt, IMAGE_FILENAME, guesserStats))
        } else {
            finalMessages.addAll(getGameFailedFinalMessages(guesserStats))
        }

        saveGameState(
            gameState,
            wordAnswers,
            systemMessage,
            guessChat,
            finalMessages
        )

        saveHtmlReplay(
            gameState,
            wordAnswers,
            systemMessage,
            guessChat,
            finalMessages,
            s3Repository
        )

    } finally {
        quordleDriver.close()
    }
}

fun getGameSolvedFinalMessages(imagePrompt: String, imageFilename: String, guesserStats: GuesserStats) = listOf(
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
    ),
) + getFinalSystemMessages(guesserStats)

fun getGameFailedFinalMessages(guesserStats: GuesserStats) = listOf(
    ChatMessage(
        role = ChatRole.User,
        content = "<p>Unfortunately, you failed to solve the Quordle today.</p>"
    )
) + getFinalSystemMessages(guesserStats)

private fun getFinalSystemMessages(guesserStats: GuesserStats) = listOf(
    ChatMessage(
        role = ChatRole.System,
        content = generateStatsHtml(guesserStats)
    ),
    ChatMessage(
        role = ChatRole.System,
        content = "<div><p><a class=\"link\" href=\"https://github.com/joe-boudreau/llm-quordle-solver\">Source Code</a></p>" +
                "<p><a class=\"link\" href=\"/post/getting-an-llm-to-solve-the-daily-quordle\">How I Made This</a></p></div>"
    ),
)

private fun generateStatsHtml(stats: GuesserStats): String {
    val totalGames = stats.winCount + stats.lossCount
    val winPercentage = if (totalGames > 0) (stats.winCount * 100.0 / totalGames) else 0.0

    // Generate distribution bars
    val maxAttempts = stats.attemptsDistributionForWins.values.maxOrNull() ?: 1
    val distributionBars = (4..9).joinToString("") { attempts ->
        val count = stats.attemptsDistributionForWins[attempts] ?: 0
        val percentage = if (maxAttempts > 0) (count * 100.0 / maxAttempts) else 0.0
        """
        <div class="stat-bar-row">
            <div class="stat-bar-label">$attempts</div>
            <div class="stat-bar-container">
                <div class="stat-bar-fill" style="width: ${percentage}%"></div>
            </div>
            <div class="stat-bar-count">$count</div>
        </div>
        """.trimIndent()
    }

    return """
    <div class="stats-container">
        <h3 class="stats-title">📊 LLM Quordle Stats</h3>
        
        <div class="stats-overview">
            <div class="stat-item">
                <div class="stat-number">${stats.winCount}</div>
                <div class="stat-label">Wins</div>
            </div>
            <div class="stat-item">
                <div class="stat-number">${stats.lossCount}</div>
                <div class="stat-label">Losses</div>
            </div>
            <div class="stat-item">
                <div class="stat-number">${String.format("%.1f", winPercentage)}%</div>
                <div class="stat-label">Win Rate</div>
            </div>
            <div class="stat-item">
                <div class="stat-number">${stats.currentStreak}</div>
                <div class="stat-label">Current Streak</div>
            </div>
        </div>
        
        <div class="stats-distribution">
            <h4 class="distribution-title">Total Attempts (Wins Only)</h4>
            <div class="stat-bars">
                $distributionBars
            </div>
        </div>
    </div>
    """.trimIndent()
}

fun downloadImage(imageUrl: String?, imageFilename: String, s3Repository: S3BucketRepository?) {
    if (imageUrl.isNullOrEmpty()) {
        return
    }

    val imageFilepath = OUTPUT_FILEPATH + imageFilename
    val destinationFile = File(imageFilepath)

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

        // Upload to S3 if repository is available
        s3Repository?.let {
            it.uploadFile(imageFilename, destinationFile)
            println("Image uploaded to S3")
        }
    } catch (e: Exception) {
        println("Failed to download image: ${e.message}")
        e.printStackTrace()
    }
}