import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.amazonaws.services.lambda.runtime.serialization.factories.JacksonFactory

class RunConfigurationTest {
    private val environment = mapOf(
        "OPENAI_CHAT_MODEL_ID" to "default-model",
        "OPENAI_REASONING_EFFORT" to "high",
        "GENERATE_HTML_REPLAY" to "true",
    )

    @Test
    fun `Lambda JSON deserializer preserves uppercase override keys and boolean values`() {
        val requestType = LambdaRuntimeInterface::class.java.genericInterfaces.single() as java.lang.reflect.ParameterizedType
        val serializer = JacksonFactory.getInstance().getSerializer(requestType.actualTypeArguments[0])
        @Suppress("UNCHECKED_CAST")
        val input = serializer.fromJson("""{"OPENAI_CHAT_MODEL_ID":"comparison-model","OPENAI_REASONING_EFFORT":"low","GENERATE_HTML_REPLAY":false}""") as Map<String, Any?>
        assertEquals(RunConfiguration("comparison-model", "low", false), RunConfiguration.from(input, environment::get))
    }

    @Test
    fun `empty invocation preserves environment settings`() {
        assertEquals(RunConfiguration("default-model", "high", true), RunConfiguration.from(environment = environment::get))
    }

    @Test
    fun `invocation overrides model effort and replay without changing defaults for later runs`() {
        val input = mapOf(
            "OPENAI_CHAT_MODEL_ID" to "comparison-model",
            "OPENAI_REASONING_EFFORT" to "low",
            "GENERATE_HTML_REPLAY" to false,
        )
        assertEquals(RunConfiguration("comparison-model", "low", false), RunConfiguration.from(input, environment::get))
        assertEquals(RunConfiguration("default-model", "high", true), RunConfiguration.from(environment = environment::get))
    }

    @Test
    fun `partial invocation retains missing environment settings`() {
        assertEquals(
            RunConfiguration("default-model", "low", true),
            RunConfiguration.from(mapOf("OPENAI_REASONING_EFFORT" to "low"), environment::get),
        )
    }

    @Test
    fun `reasoning effort defaults to medium and replay defaults to enabled`() {
        val config = RunConfiguration.from(environment = mapOf("OPENAI_CHAT_MODEL_ID" to "model")::get)
        assertEquals("medium", config.reasoningEffort)
        assertTrue(config.generateHtmlReplay)
    }

    @Test
    fun `explicit main invocation overrides disabled environment replay`() {
        val env = environment + ("GENERATE_HTML_REPLAY" to "false")
        assertFalse(RunConfiguration.from(environment = env::get).generateHtmlReplay)
        assertTrue(RunConfiguration.from(mapOf("GENERATE_HTML_REPLAY" to true), env::get).generateHtmlReplay)
    }

    @Test
    fun `invalid invocation values are rejected before starting a game`() {
        listOf(
            mapOf("OPENAI_CHAT_MODEL_ID" to ""),
            mapOf("OPENAI_REASONING_EFFORT" to 123),
            mapOf("OPENAI_REASONING_EFFORT" to null),
            mapOf("GENERATE_HTML_REPLAY" to "false"),
            mapOf("GENERATE_HTML_REPLAY" to null),
        ).forEach { input ->
            assertFailsWith<IllegalArgumentException> { RunConfiguration.from(input, environment::get) }
        }
        assertFailsWith<IllegalArgumentException> { RunConfiguration.from(environment = { null }) }
        assertFailsWith<IllegalArgumentException> {
            RunConfiguration.from(environment = (environment + ("GENERATE_HTML_REPLAY" to "invalid"))::get)
        }
    }
}
