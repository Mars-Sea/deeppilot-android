package dev.hailab.deeppilot.core.models

/**
 * Matches a model-catalog entry to a bundled brand icon key, mirroring the iOS
 * `ProviderIcon`: brand tokens in the model name or id first (one provider can
 * serve many brands), then the provider id. Null means "use the letter badge".
 */
object ProviderIcons {
    /** Icons bundled as `model_icon_<key>` drawables. */
    val keys: Set<String> = setOf(
        "azure", "baichuan", "claude", "cohere", "deepseek", "doubao", "gemini", "grok", "groq",
        "hunyuan", "kimi", "longcat", "meta", "minimax", "mistral", "moonshot", "ollama", "openai",
        "openrouter", "perplexity", "qwen", "stepfun", "together", "vertexai", "yi", "zhipu",
    )

    private val brandTokens: List<Pair<String, String>> = listOf(
        "openai" to "openai", "chatgpt" to "openai", "davinci" to "openai", "gpt" to "openai",
        "deepseek" to "deepseek",
        "anthropic" to "claude", "claude" to "claude",
        "gemini" to "gemini",
        "vertexai" to "vertexai", "vertex" to "vertexai",
        "qwen" to "qwen", "qwq" to "qwen", "tongyi" to "qwen", "dashscope" to "qwen",
        "moonshot" to "moonshot", "kimi" to "kimi",
        "zhipu" to "zhipu", "chatglm" to "zhipu", "bigmodel" to "zhipu", "glm" to "zhipu",
        "doubao" to "doubao", "hunyuan" to "hunyuan",
        "minimax" to "minimax", "abab" to "minimax",
        "stepfun" to "stepfun", "baichuan" to "baichuan",
        "mistral" to "mistral", "mixtral" to "mistral",
        "grok" to "grok",
        "llama" to "meta", "meta" to "meta",
        "cohere" to "cohere", "command" to "cohere",
        "perplexity" to "perplexity", "groq" to "groq", "ollama" to "ollama",
        "openrouter" to "openrouter", "together" to "together",
        "azure" to "azure", "microsoft" to "azure",
    )

    private val providerAliases: Map<String, String> = mapOf(
        "deepseek" to "deepseek", "openai" to "openai",
        "anthropic" to "claude", "claude" to "claude",
        "google" to "gemini", "googleai" to "gemini", "googleaistudio" to "gemini", "gemini" to "gemini",
        "gcp" to "vertexai", "googlecloud" to "vertexai", "vertex" to "vertexai", "vertexai" to "vertexai",
        "qwen" to "qwen", "alibaba" to "qwen", "dashscope" to "qwen", "tongyi" to "qwen",
        "moonshot" to "moonshot", "moonshotai" to "kimi", "kimi" to "kimi",
        "zhipu" to "zhipu", "glm" to "zhipu", "chatglm" to "zhipu", "bigmodel" to "zhipu",
        "bytedance" to "doubao", "doubao" to "doubao", "volcengine" to "doubao",
        "tencent" to "hunyuan", "hunyuan" to "hunyuan",
        "minimax" to "minimax", "abab" to "minimax",
        "stepfun" to "stepfun", "step" to "stepfun",
        "baichuan" to "baichuan",
        "yi" to "yi", "zeroone" to "yi", "01ai" to "yi",
        "mistral" to "mistral", "mistralai" to "mistral",
        "xai" to "grok", "grok" to "grok",
        "meta" to "meta", "llama" to "meta", "metaai" to "meta",
        "cohere" to "cohere", "command" to "cohere",
        "perplexity" to "perplexity", "groq" to "groq", "ollama" to "ollama",
        "openrouter" to "openrouter", "together" to "together", "togetherai" to "together",
        "azure" to "azure", "azureopenai" to "azure", "microsoft" to "azure",
    )

    fun key(modelName: String?, modelId: String?, providerId: String?): String? {
        modelName?.let(::keyForText)?.let { return it }
        modelId?.let(::keyForText)?.let { return it }
        val provider = providerId?.let(::compact)?.takeIf { it.isNotEmpty() } ?: return null
        return (providerAliases[provider] ?: provider).takeIf { it in keys }
    }

    /** Longest brand token contained in [text]. */
    private fun keyForText(text: String): String? {
        val haystack = compact(text)
        if (haystack.isEmpty()) return null
        return brandTokens
            .filter { (token, _) -> haystack.contains(token) }
            .maxByOrNull { (token, _) -> token.length }
            ?.second
            ?.takeIf { it in keys }
    }

    /** Letters and digits only, folded, so "-", "_" and full-width forms never hide a hit. */
    private fun compact(value: String): String = ModelSearch.fold(value).filter { it.isLetterOrDigit() }
}
