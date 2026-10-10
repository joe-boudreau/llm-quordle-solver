data class RunConfiguration(
    val chatModelId: String,
    val reasoningEffort: String,
    val generateHtmlReplay: Boolean,
) {
    companion object {
        fun from(
            input: Map<String, Any?> = emptyMap(),
            environment: (String) -> String? = System::getenv,
        ): RunConfiguration {
            fun stringValue(key: String, default: String? = null): String {
                val value = if (input.containsKey(key)) input[key] else environment(key) ?: default
                require(value is String && value.isNotBlank()) { "$key must be a nonblank string" }
                return value
            }

            val generateHtmlReplay = if (input.containsKey("GENERATE_HTML_REPLAY")) {
                val value = input["GENERATE_HTML_REPLAY"]
                require(value is Boolean) { "GENERATE_HTML_REPLAY must be a JSON boolean" }
                value
            } else {
                val value = environment("GENERATE_HTML_REPLAY") ?: "true"
                require(value == "true" || value == "false") { "GENERATE_HTML_REPLAY must be true or false" }
                value.toBoolean()
            }

            return RunConfiguration(
                chatModelId = stringValue("OPENAI_CHAT_MODEL_ID"),
                reasoningEffort = stringValue("OPENAI_REASONING_EFFORT", "medium"),
                generateHtmlReplay = generateHtmlReplay,
            )
        }
    }
}
