import com.aallam.openai.api.chat.ChatCompletionChunk
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LLMQuordleGuesser {


    private val promptTemplate = """
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

    Given the following Quordle game state:
    ```
    {gameState}
    ```
    
    You will think step by step, analyzing the feedback for each guess across all 4 boards.
    Use reasoning to eliminate and confirm letter positions.

    The final_answer must be exactly 5 letters, all uppercase.
    """.trimIndent()

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
        timeout = Timeout(socket = 30.seconds, connect = 10.seconds, request = 60.seconds),
        logging = LoggingConfig(logLevel = LogLevel.Headers)
    )

    fun guessWord(gameState: GameState): String {
        val prompt = promptTemplate.replace("{gameState}", gameState.toString())
        
        // Try up to 3 times to get a valid 5-letter word
        for (attempt in 1..3) {
            try {
                val jsonResponse = runBlocking { chatCompletion(prompt) }

                // Print response for debugging
                //println("LLM JSON response on attempt $attempt:\n$jsonResponse")

                try {
                    // Parse the JSON response
                    val response = Json.decodeFromString<QuordleGuessResponse>(jsonResponse)
                    val finalAnswer = response.finalAnswer.trim().uppercase()

                    // Check if it's 5 letters
                    if (finalAnswer.length == 5) {
                        println("LLM returned valid word: '$finalAnswer' on attempt $attempt")
                        return finalAnswer
                    } else {
                        println("Attempt $attempt: LLM returned invalid word: '$finalAnswer', trying again...")
                    }
                } catch (e: Exception) {
                    println("Error parsing JSON response on attempt $attempt: ${e.message}")
                    continue // Try again with another request
                }
            } catch (e: Exception) {
                println("Error sending prompt on attempt $attempt: ${e.message}")
                continue // Try again
            }
        }

        throw IllegalArgumentException("LLM did not return a valid 5-letter word after 3 attempts.")
    }

    suspend fun chatCompletion(prompt: String): String {
        //println("Sending prompt to OpenAI\n: $prompt")
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-4.1-nano"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt,
                )
            ),
            //reasoningEffort = Effort("high"),
            responseFormat = jsonSchema(responseSchema)
        )

        val completionFlow: Flow<ChatCompletionChunk> = openAiClient.chatCompletions(chatCompletionRequest)
        // stream the response to standard out and also collect into a single string
        val response = completionFlow.map {it.choices.firstOrNull()?.delta?.content ?: ""}
            //.onEach { print(it) }
            .toList()
            .joinToString("")

        println("\n")
        //val response = completion.choices.firstOrNull()?.message?.content?.trim() ?: ""
        return response
    }
}