import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.image.ImageCreation
import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class LLMImageGenerator {

    private val userPromptTemplate = """
    Generate an image that visually represents the following four words:
    ```
    {words}
    ```
    Make the image in the style of `{style}` and be creative.
    """.trimIndent()

    private val styleList = listOf(
        "abstract art",
        "surrealism",
        "pop art",
        "impressionism",
        "cubism",
        "modern art",
        "fantasy illustration",
        "digital art",
        "watercolor painting",
        "oil painting",
        "collage",
        "pixel art",
        "graffiti",
        "cartoon style",
        "photorealism",
        "minimalism",
        "vintage poster",
        "art deco",
        "steampunk",
        "retro style",
        "futuristic design",
        "nature scene",
        "urban landscape",
        "whimsical illustration",
        // even more
        "children's book illustration",
        "gothic art",
        "folk art",
        "expressionism",
        "baroque style",
        "renaissance painting",
        "abstract expressionism",
        "studio ghibli",
    )

    private val modelId = ModelId(System.getenv("OPENAI_IMAGE_MODEL_ID"))

    private val openAiClient = OpenAI(
        token = System.getenv("OPENAI_API_KEY"),
        timeout = Timeout(socket = 60.seconds, connect = 10.seconds, request = 300.seconds),
        logging = LoggingConfig(logLevel = LogLevel.Headers)
    )

    fun generateImageUsingWords(words: List<String>): Pair<String, String>{
        val randomStyle = styleList.random()
        val prompt = userPromptTemplate
            .replace("{words}", words.joinToString(", "))
            .replace("{style}", randomStyle)

        val response = runBlocking { openAiClient.imageURL( // or openAI.imageJSON
            creation = ImageCreation(
                prompt = prompt,
                model = modelId,
                n = 1,
                size = ImageSize.is1024x1024,
            )
        ) }

        return prompt to response.first().url
    }
}