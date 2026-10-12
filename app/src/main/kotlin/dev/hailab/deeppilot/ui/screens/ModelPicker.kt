package dev.hailab.deeppilot.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.models.ModelSearch
import dev.hailab.deeppilot.core.models.ProviderIcons
import dev.hailab.deeppilot.core.protocol.ModelCatalogModel
import dev.hailab.deeppilot.core.protocol.ModelProviderGroup
import dev.hailab.deeppilot.core.protocol.ModelSelection
import dev.hailab.deeppilot.core.protocol.SessionModels
import dev.hailab.deeppilot.ui.AppRadius
import dev.hailab.deeppilot.ui.AppType
import dev.hailab.deeppilot.ui.attempt
import dev.hailab.deeppilot.ui.components.ProminentButton
import dev.hailab.deeppilot.ui.components.SmallSpinner
import dev.hailab.deeppilot.ui.components.plainClickable
import dev.hailab.deeppilot.ui.errorMessage
import dev.hailab.deeppilot.ui.palette
import kotlinx.coroutines.launch

/** Bundled brand icon for a catalog entry (lobe-icons, monochrome), or null for the letter badge. */
@DrawableRes
fun providerIcon(modelName: String?, modelId: String?, providerId: String?): Int? =
    when (ProviderIcons.key(modelName, modelId, providerId)) {
        "azure" -> R.drawable.model_icon_azure
        "baichuan" -> R.drawable.model_icon_baichuan
        "claude" -> R.drawable.model_icon_claude
        "cohere" -> R.drawable.model_icon_cohere
        "deepseek" -> R.drawable.model_icon_deepseek
        "doubao" -> R.drawable.model_icon_doubao
        "gemini" -> R.drawable.model_icon_gemini
        "grok" -> R.drawable.model_icon_grok
        "groq" -> R.drawable.model_icon_groq
        "hunyuan" -> R.drawable.model_icon_hunyuan
        "kimi" -> R.drawable.model_icon_kimi
        "longcat" -> R.drawable.model_icon_longcat
        "meta" -> R.drawable.model_icon_meta
        "minimax" -> R.drawable.model_icon_minimax
        "mistral" -> R.drawable.model_icon_mistral
        "moonshot" -> R.drawable.model_icon_moonshot
        "ollama" -> R.drawable.model_icon_ollama
        "openai" -> R.drawable.model_icon_openai
        "openrouter" -> R.drawable.model_icon_openrouter
        "perplexity" -> R.drawable.model_icon_perplexity
        "qwen" -> R.drawable.model_icon_qwen
        "stepfun" -> R.drawable.model_icon_stepfun
        "together" -> R.drawable.model_icon_together
        "vertexai" -> R.drawable.model_icon_vertexai
        "yi" -> R.drawable.model_icon_yi
        "zhipu" -> R.drawable.model_icon_zhipu
        else -> null
    }

/**
 * iOS `ModelPickerSheet`: the host's catalog grouped by provider, with search,
 * route and per-provider failures. Tapping a model switches the session to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    catalog: SessionModels?,
    loading: Boolean,
    loadError: String?,
    onRefresh: () -> Unit,
    onSelect: suspend (ModelSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf<ModelSelection?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (catalog == null && !loading) onRefresh() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().imePadding()) {
            Text(
                stringResource(R.string.model_picker_title),
                style = AppType.headline,
                color = p.foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
            )
            Divider()
            SearchField(query, { query = it })
            Divider()

            when {
                catalog != null -> {
                    val groups = ModelSearch.groups(catalog, query)
                    val failures = ModelSearch.failures(catalog, query)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        if (!catalog.routable) {
                            item {
                                NoticeLabel(Icons.Filled.Warning, stringResource(R.string.model_route_unavailable), p.warning)
                            }
                        }
                        groups.forEach { group ->
                            item(key = "group-${group.id}") {
                                Text(
                                    group.name.uppercase(),
                                    style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = p.secondary,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                                )
                            }
                            items(group.models, key = { "model-${group.id}-${it.id}" }) { model ->
                                val selection = ModelSearch.selection(catalog, group.id, model.id)
                                val current = catalog.current.provider == group.id && catalog.current.model == model.id
                                ModelRow(
                                    group = group,
                                    model = model,
                                    current = current,
                                    busy = selecting == selection,
                                    enabled = selecting == null,
                                ) {
                                    if (current) return@ModelRow
                                    selecting = selection
                                    actionError = null
                                    scope.launch {
                                        val error = attempt { onSelect(selection) }
                                        selecting = null
                                        if (error == null) {
                                            // Applied: hand control back to the composer.
                                            state.hide()
                                            onDismiss()
                                        } else {
                                            actionError = context.errorMessage(error)
                                        }
                                    }
                                }
                            }
                        }
                        if (groups.isEmpty()) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 38.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    Icon(Icons.Filled.Search, null, tint = p.tertiary, modifier = Modifier.size(22.dp))
                                    Text(stringResource(R.string.model_search_empty), style = AppType.subheadline, color = p.secondary)
                                }
                            }
                        }
                        items(failures, key = { "failure-${it.id}" }) { failure ->
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.ErrorOutline, null, tint = p.warning, modifier = Modifier.size(16.dp))
                                    Text(
                                        stringResource(R.string.model_failure_unavailable, failure.name),
                                        style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                                        color = p.warning,
                                    )
                                }
                                Text(failure.message, style = AppType.caption, color = p.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        actionError?.let { error ->
                            item { NoticeLabel(Icons.Filled.Error, error, p.danger, padding = 20.dp) }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                loading -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    SmallSpinner(size = 20.dp)
                    Text(stringResource(R.string.model_loading), style = AppType.subheadline, color = p.secondary)
                }
                else -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = p.warning, modifier = Modifier.size(28.dp))
                    Text(
                        loadError ?: stringResource(R.string.model_catalog_missing),
                        style = AppType.subheadline,
                        color = p.secondary,
                        textAlign = TextAlign.Center,
                    )
                    ProminentButton(text = stringResource(R.string.model_reload), onClick = onRefresh, height = 44.dp, radius = AppRadius.medium)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.hairline))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val p = palette
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(AppRadius.medium))
            .background(p.secondaryFill)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = p.secondary, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = AppType.subheadline.copy(color = p.foreground),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.model_search_placeholder), style = AppType.subheadline, color = p.tertiary, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                stringResource(R.string.model_search_clear),
                tint = p.tertiary,
                modifier = Modifier.size(17.dp).plainClickable { onChange("") },
            )
        }
    }
}

@Composable
private fun NoticeLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, padding: Dp = 0.dp) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp).padding(padding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.padding(top = 1.dp).size(15.dp))
        Text(text, style = AppType.footnote.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

@Composable
private fun ModelRow(
    group: ModelProviderGroup,
    model: ModelCatalogModel,
    current: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .plainClickable(enabled = enabled || busy, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ProviderBadge(providerIcon(model.name, model.id, group.id), group.id, group.name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(model.name, style = AppType.body, color = p.foreground)
            model.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AppType.caption, color = p.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            busy -> SmallSpinner(size = 16.dp)
            current -> Icon(Icons.Filled.Check, null, tint = p.foreground, modifier = Modifier.size(18.dp))
        }
    }
}

/** 34pt rounded tile with the brand icon, or the provider's initial. */
@Composable
private fun ProviderBadge(@DrawableRes icon: Int?, providerId: String, name: String) {
    val p = palette
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(AppRadius.small)).background(p.foreground.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = p.foreground, modifier = Modifier.size(22.dp))
        } else {
            Text(
                (name.firstOrNull() ?: providerId.firstOrNull() ?: 'M').uppercase(),
                style = AppType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = p.foreground,
            )
        }
    }
}

/** Width the composer reserves for the model button, so the controls after it never shift. */
val ModelButtonWidth: Dp = 150.dp

@Composable
internal fun ModelButtonIcon(catalog: SessionModels?, loading: Boolean, tint: Color) {
    val icon = catalog?.let { providerIcon(it.displayName(), it.current.model, it.current.provider) }
    when {
        loading && catalog == null -> SmallSpinner(size = 13.dp)
        icon != null -> Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(15.dp))
        else -> Icon(Icons.Outlined.Psychology, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}
