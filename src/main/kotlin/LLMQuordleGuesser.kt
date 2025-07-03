import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
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
    Use reasoning to eliminate and confirm letter positions. At the end, output your final guess in this format:

    Final Answer: <5-letter word>
    
    You may include as much reasoning as you like, but the end of your response must be in the above format. Nothing
    should be after the "Final Answer: <5-letter word>" line.
    Example response:
    ```
    <other reasoning...>
    Final Answer: CHARM
    ```
    
    """.trimIndent()

    private val openAiClient = OpenAI(
        token = System.getenv("OPENAI_API_KEY"),
        timeout = Timeout(socket = 60.seconds),
        logging = LoggingConfig(logLevel = LogLevel.None)
    )

    fun guessWord(gameState: GameState): String {
        val prompt = promptTemplate.replace("{gameState}", gameState.toString())
        
        // Try up to 3 times to get a valid 5-letter word
        for (attempt in 1..3) {
            val wordResponse = runBlocking { chatCompletion(prompt) }

            //println("LLM response for attempt $attempt:\n$wordResponse")

            val finalAnswerWord = wordResponse.lines()
                .firstOrNull { it.startsWith("Final Answer:") }
                ?.removePrefix("Final Answer:")?.trim() ?: ""

            // Process the word
            val processedWord = finalAnswerWord.trim().uppercase()

            // Check if it's 5 letters
            if (processedWord.length == 5) {
                println("LLM suggested word: $processedWord")
                return processedWord
            } else {
                println("Attempt $attempt: LLM returned invalid word: '$finalAnswerWord', trying again...")
            }
        }

        throw IllegalArgumentException("LLM did not return a valid 5-letter word after 3 attempts.")
    }

    suspend fun chatCompletion(prompt: String): String {
        //println("Sending prompt to OpenAI\n: $prompt")

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("o4-mini"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt,
                )
            ),
            //reasoningEffort = Effort("high"),
        )
        val completionFlow: Flow<ChatCompletionChunk> = openAiClient.chatCompletions(chatCompletionRequest)
        // stream the response to standard out and also collect into a single string
        val response = completionFlow.map {it.choices.firstOrNull()?.delta?.content ?: ""}
            .onEach { print(it) }
            .toList()
            .joinToString("")

        //val response = completion.choices.firstOrNull()?.message?.content?.trim() ?: ""
        return response
    }
}