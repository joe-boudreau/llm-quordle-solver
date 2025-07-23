import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ChatResponseFormat.Companion.jsonSchema
import com.aallam.openai.api.chat.Effort
import com.aallam.openai.api.chat.JsonSchema
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.*
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
    3. After each guess, the word's character tiles are changed to provide feedback. There are three possible states:
       ✓ = CORRECT = Letter is correct and in the right position
       ↔ = PRESENT = Letter is in the word but in the wrong position
       ✕ = ABSENT = Letter is not in the word at all

    Each word is independent, but you use the same guesses across all four
    word puzzles. The challenge is to strategically choose words that help
    you solve multiple puzzles efficiently. A successful game means
    correctly guessing all four words within 9 attempts.
    
    STRATEGIC APPROACH:
    - Early game: Focus on common vowels (A, E, I, O, U) and consonants (R, S, T, L, N)
    - For each board, track which letters are confirmed, eliminated, or need positioning
    - Prioritize words that can solve multiple boards simultaneously
    - Use process of elimination: avoid letters marked as ✕
    - Position letters correctly based on ✓ or ↔ feedback
    - Common 5-letter word patterns: consonant-vowel-consonant-vowel-consonant
    
    Help the user choose the next word to guess based on the current game state.
    """.trimIndent()

    private val userPromptTemplate = """
    Here is the current Quordle game state:
    ```
    {gameState}
    ```
    
    Use reasoning and consider the feedback from the previous guesses across 
    all 4 boards to eliminate and confirm letter positions.

    The final_answer must be exactly 5 letters, all uppercase.
    """.trimIndent()

    val allMessages = mutableListOf(
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

    val responseSchema = JsonSchema(
        name = "quordle_guess_response",
        schema = schemaJson,
        strict = true
    )

    private val openAiClient = OpenAI(
        token = System.getenv("OPENAI_API_KEY"),
        timeout = Timeout(socket = 60.seconds, connect = 10.seconds, request = 300.seconds),
        logging = LoggingConfig(logLevel = LogLevel.Headers)
    )

    val modelId = ModelId(System.getenv("OPENAI_CHAT_MODEL_ID"))

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

    fun removeLastNMessages(n: Int) {
        if (n <= 0 || n > allMessages.size) {
            throw IllegalArgumentException("Invalid number of messages to remove: $n")
        }
        repeat(n) {
            allMessages.removeLast()
        }
    }

    private suspend fun chatCompletion(): ChatMessage {
        val chatCompletionRequest = ChatCompletionRequest(
            model = modelId,
            messages = allMessages,
            reasoningEffort = Effort("low"),
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