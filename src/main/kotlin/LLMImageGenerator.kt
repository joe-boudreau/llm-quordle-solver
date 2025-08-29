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
    Make the image in the style of `{style}`. 
    The words don't *need* to be directly represented, but the overall theme and mood of the image should reflect them. 
    Use colors, shapes, and composition to evoke the essence of any words you can't incorporate directly. 
    The image should be visually striking and imaginative.
    """.trimIndent()

    private val styleList = listOf(
        "3D Rendering",
        "Abstract Expressionism",
        "Acrylic Painting",
        "Airbrush",
        "Anime",
        "Aquatint",
        "Art Brut",
        "Art Deco",
        "Art Nouveau",
        "Baroque",
        "Bauhaus",
        "Blinn-Phong Shading",
        "Cartoon Style",
        "Cel Shading",
        "Chiaroscuro",
        "Children's Book Illustration",
        "Cloisonnism",
        "Collage",
        "Comic Book Art",
        "Cross Processing",
        "Cross-hatching",
        "Cubism",
        "Cyanotype",
        "Dadaism",
        "Daguerreotype",
        "Digital Art",
        "Digital Painting",
        "Disney Animation",
        "Double Exposure",
        "Encaustic",
        "Expressionism",
        "Fantasy Illustration",
        "Fauvism",
        "Film Noir",
        "Folk Art",
        "Fresco",
        "Futurism",
        "Glitch Art",
        "Gothic Art",
        "Graffiti",
        "Gum Bichromate",
        "HDR Photography",
        "Hard-edge Painting",
        "Hyperrealism",
        "Impressionism",
        "Infrared Photography",
        "Kinetic Art",
        "Linocut",
        "Low Poly",
        "Magic Realism",
        "Manga",
        "Memphis Design",
        "Modern Art",
        "Monoprint",
        "Oil Painting",
        "Orphism",
        "Pixar Animation",
        "Pixel Art",
        "Pointillism",
        "Pop Art",
        "Post-Impressionism",
        "Precisionism",
        "Ray Tracing",
        "Rayonism",
        "Relief Print",
        "Renaissance Painting",
        "Scratchboard",
        "Sfumato",
        "Silver Gelatin Print",
        "Steampunk",
        "Stencil Art",
        "Street Art",
        "Studio Ghibli",
        "Sumi-e",
        "Suprematism",
        "Surrealism",
        "Swiss Design",
        "Synthwave",
        "Tilt-shift",
        "Toon Shading",
        "Trompe-l'oeil",
        "Ukiyo-e",
        "Vaporwave",
        "Vector Art",
        "Volumetric Rendering",
        "Voxel Art",
        "Watercolor Painting",
        "Woodcut",
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