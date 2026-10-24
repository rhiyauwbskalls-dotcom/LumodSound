package com.lumodsound.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumodsound.app.data.AiProvider
import com.lumodsound.app.data.ApiFormat
import com.lumodsound.app.data.AiTranslator
import com.lumodsound.app.data.OpenRouterCatalog
import com.lumodsound.app.data.OpenRouterModel
import com.lumodsound.app.data.TranslateEngine
import com.lumodsound.app.data.TranslateResult
import com.lumodsound.app.data.TranslationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Ajustes → "Letras traducción": motor (ML Kit o IA con tu propia API key), proveedor, API key y modelo. */
@Composable
fun TranslationSettingsScreen(store: TranslationStore, onBack: () -> Unit) {
    val s by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }

    SettingsScaffold(title = str(R.string.lyrics_translation), onBack = onBack) {
        SettingsGroup(str(R.string.translation_engine)) {
            ChoiceRow(
                title = "ML Kit",
                subtitle = str(R.string.on_your_phone_free_and),
                selected = s.engine == TranslateEngine.MLKIT,
                onClick = { store.setEngine(TranslateEngine.MLKIT) }
            )
            ChoiceRow(
                title = str(R.string.ai_with_your_own_api),
                subtitle = str(R.string.more_natural_translations_slang_expressions),
                selected = s.engine == TranslateEngine.AI,
                onClick = { store.setEngine(TranslateEngine.AI) }
            )
        }

        if (s.engine == TranslateEngine.AI) {
            SettingsGroup(str(R.string.provider)) {
                AiProvider.values().forEach { p ->
                    ChoiceRow(
                        title = p.label,
                        subtitle = null,
                        selected = s.provider == p,
                        onClick = { store.setProvider(p) }
                    )
                }
            }

            val isCustom = s.provider == AiProvider.CUSTOM

            if (isCustom) {
                SettingsGroup(str(R.string.api_format)) {
                    ApiFormat.values().forEach { f ->
                        ChoiceRow(
                            title = f.label,
                            subtitle = null,
                            selected = s.customFormat == f,
                            onClick = { store.setCustomFormat(f) }
                        )
                    }
                }
                SettingsGroup(str(R.string.endpoint)) {
                    var endpoint by remember { mutableStateOf(s.customEndpoint) }
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            (str(R.string.paste_the_full_url_for) + " ") +
                                str(R.string.it_must_start_with_https),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = {
                                endpoint = it
                                store.setCustomEndpoint(it)
                            },
                            label = { Text(str(R.string.endpoint_url)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            SettingsGroup(if (isCustom) str(R.string.api_key_optional) else str(R.string.api_key, s.provider.label)) {
                var input by remember(s.provider) { mutableStateOf("") }
                var visible by remember(s.provider) { mutableStateOf(false) }
                val clipboard = LocalClipboardManager.current
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            s.apiKey.isNotBlank() -> str(R.string.saved_encrypted_on_your_phone)
                            isCustom -> str(R.string.only_if_your_service_asks)
                            else -> str(R.string.get_it_at, s.provider.keyHint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (s.apiKey.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (visible) s.apiKey else maskKey(s.apiKey),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(if (s.apiKey.isNotBlank()) str(R.string.paste_a_new_api_key) else str(R.string.paste_api_key)) },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            enabled = input.isNotBlank(),
                            onClick = {
                                if (store.setApiKey(s.provider, input)) {
                                    input = ""
                                    Toast.makeText(context, str(R.string.api_key_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, str(R.string.couldn_t_save_it_securely), Toast.LENGTH_LONG).show()
                                }
                            }
                        ) { Text(str(R.string.save)) }
                        TextButton(onClick = { visible = !visible }) { Text(if (visible) str(R.string.hide) else str(R.string.show)) }
                    }
                    if (s.apiKey.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(s.apiKey))
                                Toast.makeText(context, str(R.string.api_key_copied), Toast.LENGTH_SHORT).show()
                            }) { Text(str(R.string.copy)) }
                            TextButton(onClick = {
                                store.setApiKey(s.provider, "")
                                Toast.makeText(context, str(R.string.api_key_deleted), Toast.LENGTH_SHORT).show()
                            }) { Text(str(R.string.clear)) }
                        }
                    }
                }
            }

            SettingsGroup(str(R.string.model)) {
                val presets = s.provider.models
                var custom by remember(s.provider) { mutableStateOf(s.model !in presets) }
                var text by remember(s.provider) { mutableStateOf(if (s.model !in presets) s.model else "") }
                presets.forEach { m ->
                    ChoiceRow(
                        title = m,
                        subtitle = null,
                        selected = !custom && s.model == m,
                        onClick = {
                            custom = false
                            store.setModel(s.provider, m)
                        }
                    )
                }
                if (presets.isNotEmpty()) {
                    ChoiceRow(
                        title = str(R.string.other_type_manually),
                        subtitle = null,
                        selected = custom,
                        onClick = {
                            custom = true
                            if (text.isNotBlank()) store.setModel(s.provider, text)
                        }
                    )
                }
                var showCatalog by remember(s.provider) { mutableStateOf(false) }
                if (s.provider == AiProvider.OPENROUTER) {
                    TextButton(
                        onClick = { showCatalog = true },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text(str(R.string.browse_the_openrouter_catalog)) }
                }
                if (showCatalog) {
                    OpenRouterCatalogDialog(
                        onPick = { id ->
                            showCatalog = false
                            custom = id !in presets
                            text = if (id in presets) "" else id
                            store.setModel(s.provider, id)
                        },
                        onDismiss = { showCatalog = false }
                    )
                }
                if (custom) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            store.setModel(s.provider, it)
                        },
                        label = { Text(str(R.string.exact_model_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }

            Button(
                enabled = !testing && (if (isCustom) s.customEndpoint.isNotBlank() else s.apiKey.isNotBlank()),
                onClick = {
                    testing = true
                    scope.launch {
                        val r = AiTranslator.translate(listOf("I love you more than words can say"), "es", s)
                        testing = false
                        val msg = when (r) {
                            is TranslateResult.Success -> str(R.string.works, r.lines.firstOrNull().orEmpty())
                            is TranslateResult.Error -> r.message
                            else -> str(R.string.it_didn_t_work_check)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (testing) str(R.string.testing) else str(R.string.test_connection)) }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Lista de todos los modelos de OpenRouter, con búsqueda y filtro de gratis. */
@Composable
private fun OpenRouterCatalogDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var models by remember { mutableStateOf<List<OpenRouterModel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        models = null
        error = null
        try {
            models = OpenRouterCatalog.fetch(force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = str(R.string.couldn_t_load_the_catalog)
        }
    }

    val filtered = remember(models, query, freeOnly) {
        val q = query.trim().lowercase()
        models.orEmpty().filter { m ->
            (!freeOnly || m.free) && (q.isEmpty() || m.id.lowercase().contains(q) || m.name.lowercase().contains(q))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.openrouter_catalog)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(str(R.string.search_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = freeOnly,
                    onClick = { freeOnly = !freeOnly },
                    label = { Text(str(R.string.free_only)) }
                )
                Spacer(Modifier.height(8.dp))
                when {
                    error != null -> {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload++ }) { Text(str(R.string.retry)) }
                    }
                    models == null -> Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                    filtered.isEmpty() -> Text(
                        str(R.string.no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(filtered, key = { it.id }) { m ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(m.id) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    if (m.free) str(R.string.free, m.name) else m.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    m.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(R.string.close)) } }
=======

/** Ajustes → "Letras traducción": motor (ML Kit o IA con tu propia API key), proveedor, API key y modelo. */
@Composable
fun TranslationSettingsScreen(store: TranslationStore, onBack: () -> Unit) {
    val s by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }

    SettingsScaffold(title = "Letras traducción", onBack = onBack) {
        SettingsGroup("Motor de traducción") {
            ChoiceRow(
                title = "ML Kit",
                subtitle = "En el teléfono, gratis y sin cuenta. Baja el idioma la primera vez.",
                selected = s.engine == TranslateEngine.MLKIT,
                onClick = { store.setEngine(TranslateEngine.MLKIT) }
            )
            ChoiceRow(
                title = "IA con tu propia API",
                subtitle = "Traducciones más naturales (jerga, expresiones). Necesitas tu API key.",
                selected = s.engine == TranslateEngine.AI,
                onClick = { store.setEngine(TranslateEngine.AI) }
            )
        }

        if (s.engine == TranslateEngine.AI) {
            SettingsGroup("Proveedor") {
                AiProvider.values().forEach { p ->
                    ChoiceRow(
                        title = p.label,
                        subtitle = null,
                        selected = s.provider == p,
                        onClick = { store.setProvider(p) }
                    )
                }
            }

            val isCustom = s.provider == AiProvider.CUSTOM

            if (isCustom) {
                SettingsGroup("Formato de la API") {
                    ApiFormat.values().forEach { f ->
                        ChoiceRow(
                            title = f.label,
                            subtitle = null,
                            selected = s.customFormat == f,
                            onClick = { store.setCustomFormat(f) }
                        )
                    }
                }
                SettingsGroup("Endpoint") {
                    var endpoint by remember { mutableStateOf(s.customEndpoint) }
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Pega la URL completa, por ejemplo https://tu-servidor/v1/chat/completions. " +
                                "Debe empezar con https://.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = {
                                endpoint = it
                                store.setCustomEndpoint(it)
                            },
                            label = { Text("URL del endpoint") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            SettingsGroup(if (isCustom) "API key (opcional)" else "API key de ${s.provider.label}") {
                var input by remember(s.provider) { mutableStateOf("") }
                var visible by remember(s.provider) { mutableStateOf(false) }
                val clipboard = LocalClipboardManager.current
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            s.apiKey.isNotBlank() -> "Guardada, cifrada en el teléfono."
                            isCustom -> "Solo si tu servicio la pide."
                            else -> "Consíguela en ${s.provider.keyHint}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (s.apiKey.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (visible) s.apiKey else maskKey(s.apiKey),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(if (s.apiKey.isNotBlank()) "Pegar una nueva API key" else "Pegar API key") },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            enabled = input.isNotBlank(),
                            onClick = {
                                if (store.setApiKey(s.provider, input)) {
                                    input = ""
                                    Toast.makeText(context, "API key guardada", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No se pudo guardar de forma segura", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) { Text("Guardar") }
                        TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Mostrar") }
                    }
                    if (s.apiKey.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(s.apiKey))
                                Toast.makeText(context, "API key copiada", Toast.LENGTH_SHORT).show()
                            }) { Text("Copiar") }
                            TextButton(onClick = {
                                store.setApiKey(s.provider, "")
                                Toast.makeText(context, "API key borrada", Toast.LENGTH_SHORT).show()
                            }) { Text("Borrar") }
                        }
                    }
                }
            }

            SettingsGroup("Modelo") {
                val presets = s.provider.models
                var custom by remember(s.provider) { mutableStateOf(s.model !in presets) }
                var text by remember(s.provider) { mutableStateOf(if (s.model !in presets) s.model else "") }
                presets.forEach { m ->
                    ChoiceRow(
                        title = m,
                        subtitle = null,
                        selected = !custom && s.model == m,
                        onClick = {
                            custom = false
                            store.setModel(s.provider, m)
                        }
                    )
                }
                if (presets.isNotEmpty()) {
                    ChoiceRow(
                        title = "Otro (escribir manualmente)",
                        subtitle = null,
                        selected = custom,
                        onClick = {
                            custom = true
                            if (text.isNotBlank()) store.setModel(s.provider, text)
                        }
                    )
                }
                var showCatalog by remember(s.provider) { mutableStateOf(false) }
                if (s.provider == AiProvider.OPENROUTER) {
                    TextButton(
                        onClick = { showCatalog = true },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text("Ver catálogo de OpenRouter") }
                }
                if (showCatalog) {
                    OpenRouterCatalogDialog(
                        onPick = { id ->
                            showCatalog = false
                            custom = id !in presets
                            text = if (id in presets) "" else id
                            store.setModel(s.provider, id)
                        },
                        onDismiss = { showCatalog = false }
                    )
                }
                if (custom) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            store.setModel(s.provider, it)
                        },
                        label = { Text("Nombre exacto del modelo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }

            Button(
                enabled = !testing && (if (isCustom) s.customEndpoint.isNotBlank() else s.apiKey.isNotBlank()),
                onClick = {
                    testing = true
                    scope.launch {
                        val r = AiTranslator.translate(listOf("I love you more than words can say"), "es", s)
                        testing = false
                        val msg = when (r) {
                            is TranslateResult.Success -> "Funciona: “${r.lines.firstOrNull().orEmpty()}”"
                            is TranslateResult.Error -> r.message
                            else -> "No funcionó, revisa los datos de la conexión"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (testing) "Probando…" else "Probar conexión") }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Lista de todos los modelos de OpenRouter, con búsqueda y filtro de gratis. */
@Composable
private fun OpenRouterCatalogDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var models by remember { mutableStateOf<List<OpenRouterModel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        models = null
        error = null
        try {
            models = OpenRouterCatalog.fetch(force = reload > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se pudo cargar el catálogo (¿sin internet?)"
        }
    }

    val filtered = remember(models, query, freeOnly) {
        val q = query.trim().lowercase()
        models.orEmpty().filter { m ->
            (!freeOnly || m.free) && (q.isEmpty() || m.id.lowercase().contains(q) || m.name.lowercase().contains(q))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catálogo de OpenRouter") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar modelo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = freeOnly,
                    onClick = { freeOnly = !freeOnly },
                    label = { Text("Solo gratis") }
                )
                Spacer(Modifier.height(8.dp))
                when {
                    error != null -> {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload++ }) { Text("Reintentar") }
                    }
                    models == null -> Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                    filtered.isEmpty() -> Text(
                        "Sin resultados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(filtered, key = { it.id }) { m ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(m.id) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    if (m.free) "${m.name} · gratis" else m.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    m.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
    )
}

/** "sk-••••••••ab12": deja ver el principio y el final para reconocer cuál es. */
private fun maskKey(key: String): String =
    if (key.length <= 8) "•".repeat(key.length) else key.take(3) + "••••••••" + key.takeLast(4)
