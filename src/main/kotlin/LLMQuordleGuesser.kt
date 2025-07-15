import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ChatResponseFormat.Companion.jsonSchema
import com.aallam.openai.api.chat.JsonSchema
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class LLMQuordleGuesser {


    private val systemPrompt = """
    You are an expert at guessing words in the game Quordle.

    Quordle is a word-guessing game similar to Wordle, but with a
    more challenging twist. In Quordle, players simultaneously solve
    four different 5-letter word puzzles using a single set of guesses.
    Here are the key rules:

    1. Goal: Guess four secret 5-letter words simultaneously
    2. You have 9 total attempts to solve all four words
    3. After each guess, tiles change color to provide feedback:
       - CORRECT: Letter is correct and in the right position
       - PRESENT: Letter is in the word but in the wrong position
       - ABSENT: Letter is not in the word at all

    Each word is independent, but you use the same guesses across all four
    word puzzles. The challenge is to strategically choose words that help
    you solve multiple puzzles efficiently. A successful game means
    correctly guessing all four words within 9 attempts.
    
    Help the user guess the next word based on the current game state.
    """.trimIndent()

    private val userPromptTemplate = """
    Here is the current Quordle game state:
    ```
    {gameState}
    ```
    
    You will think step by step, using the feedback from each previous guess across all 4 boards.
    Use reasoning to eliminate and confirm letter positions.

    The final_answer must be exactly 5 letters, all uppercase.
    """.trimIndent()

    private val allMessages = mutableListOf(
        ChatMessage(
            role = ChatRole.System,
            content = systemPrompt,
        )
    )

    val schemaJson = JsonObject(mapOf(
        "type" to JsonPrimitive("object"),
        "properties" to JsonObject(mapOf(
            "reasoning" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Chain of thought reasoning leading to the final answer")
            )),
            "final_answer" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("The final 5-letter word guess, in all uppercase")
            ))
        )),
        "additionalProperties" to JsonPrimitive(false),
        "required" to JsonArray(
            listOf(
                JsonPrimitive("reasoning"),
                JsonPrimitive("final_answer")
            )
        )
    ))

    @Serializable
    data class QuordleGuessResponse(
        @SerialName("reasoning")
        val reasoning: String,
        @SerialName("final_answer")
        val finalAnswer: String
    )

    val responseSchema = JsonSchema(
        name = "quordle_guess_response",
        schema = schemaJson,
        strict = true
    )

    private val openAiClient = OpenAI(
        token = System.getenv("OPENAI_API_KEY"),
        timeout = Timeout(socket = 60.seconds, connect = 10.seconds, request = 180.seconds),
        logging = LoggingConfig(logLevel = LogLevel.Headers)
    )

    private val modelId = ModelId(System.getenv("OPENAI_MODEL_ID"))

    fun guessWord(gameState: GameState): String {
        val prompt = userPromptTemplate.replace("{gameState}", gameState.toString())

        val nextMessage = ChatMessage(
            role = ChatRole.User,
            content = prompt,
        )

        allMessages.add(nextMessage)

        // Try up to 3 times to get a valid 5-letter word
        for (attempt in 1..3) {
            try {
                val responseChatMessage = runBlocking { chatCompletion() }
                val jsonString = responseChatMessage.content?.trim() ?: ""
                val response = Json.decodeFromString<QuordleGuessResponse>(jsonString)
                val finalAnswer = response.finalAnswer.trim().uppercase()

                if (finalAnswer.length == 5) {
                    println("LLM returned valid word: '$finalAnswer' on attempt $attempt")
                    allMessages.add(responseChatMessage)
                    return finalAnswer
                } else {
                    println("Attempt $attempt: LLM returned invalid word: '$finalAnswer', trying again...")
                }

            } catch (e: Exception) {
                println("Error when guessing word on attempt $attempt: ${e.message}")
                continue // Try again
            }
        }

        throw IllegalArgumentException("LLM did not return a valid 5-letter word after 3 attempts.")
    }

    suspend fun chatCompletion(): ChatMessage {
        val chatCompletionRequest = ChatCompletionRequest(
            model = modelId,
            messages = allMessages,
            //reasoningEffort = Effort("high"),
            responseFormat = jsonSchema(responseSchema)
        )

        val completion = openAiClient.chatCompletion(chatCompletionRequest)
        val message = completion.choices.firstOrNull()?.message
        if (message == null) {
            throw IllegalStateException("No message returned from LLM")
        }
        return message
    }
}