package dev.hailab.deeppilot.core.models

import dev.hailab.deeppilot.core.protocol.ModelCatalogFailure
import dev.hailab.deeppilot.core.protocol.ModelProviderGroup
import dev.hailab.deeppilot.core.protocol.ModelSelection
import dev.hailab.deeppilot.core.protocol.SessionModels
import java.text.Normalizer

/** The model picker's search, as the iOS `ModelPickerSheet` does it. */
object ModelSearch {
    /** Case-, accent- and width-insensitive word tokens. */
    fun tokens(value: String): List<String> =
        fold(value).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** Every query token must appear inside one word, or inside all words run together. */
    fun matches(query: List<String>, vararg values: String): Boolean {
        if (query.isEmpty()) return true
        val words = tokens(values.joinToString(" "))
        val compact = words.joinToString("")
        return query.all { token -> words.any { it.contains(token) } || compact.contains(token) }
    }

    fun groups(catalog: SessionModels, query: String): List<ModelProviderGroup> {
        val q = tokens(query)
        if (q.isEmpty()) return catalog.groups
        return catalog.groups.mapNotNull { group ->
            val models = group.models.filter { model ->
                val efforts = model.reasoning?.efforts.orEmpty()
                    .flatMap { listOf(it.id, it.name, it.description.orEmpty()) }
                    .joinToString(" ")
                matches(q, group.id, group.name, model.id, model.name, model.description.orEmpty(), efforts)
            }
            if (models.isEmpty()) null else group.copy(models = models)
        }
    }

    fun failures(catalog: SessionModels, query: String): List<ModelCatalogFailure> {
        val q = tokens(query)
        return catalog.failures.filter { matches(q, it.id, it.name, it.message) }
    }

    /**
     * The route a tap on a model row submits: the current effort when it is the
     * current route, otherwise that model's default effort.
     */
    fun selection(catalog: SessionModels, groupId: String, modelId: String): ModelSelection {
        val current = catalog.current.provider == groupId && catalog.current.model == modelId
        val model = catalog.groups.firstOrNull { it.id == groupId }?.models?.firstOrNull { it.id == modelId }
        return ModelSelection(
            provider = groupId,
            model = modelId,
            reasoningEffort = if (current) catalog.current.reasoningEffort else model?.reasoning?.defaultEffort,
        )
    }

    internal fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
}
