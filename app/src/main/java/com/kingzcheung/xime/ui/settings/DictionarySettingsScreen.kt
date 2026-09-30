package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.settings.PersonalDictManager
import com.kingzcheung.xime.settings.UserDictIoManager
import com.kingzcheung.xime.viewmodel.CustomPhraseUiState
import com.kingzcheung.xime.viewmodel.CustomPhraseViewModel
import com.kingzcheung.xime.viewmodel.DictionarySettingsViewModel
import com.kingzcheung.xime.viewmodel.UserDictUiState
import com.kingzcheung.xime.viewmodel.UserDictViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 词库管理：三个页签分别对应三类数据 ——
 *  - 自定义短语：用户手写的 custom_phrase（可增删改，FAB 只在它这里出现）
 *  - 用户词库：打字造词产生、由 librime 写进 leveldb 的 `<词典名>.userdb`（只读）
 *  - 方案词库：随方案分发的静态 `.dict.yaml`（含 import_tables 与 translator.packs）
 *
 * 「个人词库」页签已移除：它读的是 `user_<词典名>.dict.yaml`（方案里的静态码表），
 * 与「方案词库」重复且实际设备上恒为空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val schemaVM: DictionarySettingsViewModel = viewModel()
    val schemaState by schemaVM.uiState.collectAsStateWithLifecycle()
    var selectedDictTab by remember { mutableIntStateOf(0) }
    var showSchemaMenu by remember { mutableStateOf(false) }
    val customPhraseVM: CustomPhraseViewModel = viewModel(key = "dict_custom_phrase")
    val customPhraseState by customPhraseVM.uiState.collectAsStateWithLifecycle()
    val userDictVM: UserDictViewModel = viewModel(key = "dict_user_dict")
    val userDictState by userDictVM.uiState.collectAsStateWithLifecycle()

    // 切换方案：补齐该方案的 custom_phrase 翻译器补丁，并让自定义短语跟随方案
    LaunchedEffect(schemaState.selectedSchema) {
        val schemaId = schemaState.selectedSchema
        if (schemaId.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) { PersonalDictManager.ensureSchemaPack(context, schemaId) }
        customPhraseVM.setSchema(schemaId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val schema = schemaState.availableSchemas.find { it.schemaId == schemaState.selectedSchema }
                    Text("词库管理 - ${schema?.name ?: schemaState.selectedSchema}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.clickable { showSchemaMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val schema = schemaState.availableSchemas.find { it.schemaId == schemaState.selectedSchema }
                        Text(schema?.name ?: schemaState.selectedSchema, style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showSchemaMenu,
                        onDismissRequest = { showSchemaMenu = false },
                        offset = DpOffset(0.dp, 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        for (s in schemaState.availableSchemas) {
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = { showSchemaMenu = false; schemaVM.selectSchema(s.schemaId) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            // 只有自定义短语支持增删改：用户词库是打字造词产生的（只读），
            // 方案词库是随方案分发的静态码表
            if (selectedDictTab == 0) {
                FloatingActionButton(
                    onClick = { customPhraseVM.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedDictTab) {
                Tab(
                    selected = selectedDictTab == 0,
                    onClick = { selectedDictTab = 0 },
                    text = { Text("自定义短语", maxLines = 1) }
                )
                Tab(
                    selected = selectedDictTab == 1,
                    onClick = { selectedDictTab = 1 },
                    text = { Text("用户词库", maxLines = 1) }
                )
                Tab(
                    selected = selectedDictTab == 2,
                    onClick = { selectedDictTab = 2 },
                    text = { Text("方案词库", maxLines = 1) }
                )
            }
            when (selectedDictTab) {
                0 -> CustomPhraseTabContent(viewModel = customPhraseVM, uiState = customPhraseState)
                1 -> UserDictTabContent(viewModel = userDictVM, uiState = userDictState)
                2 -> SchemaDictBrowserPanel(viewModel = schemaVM, showSchemaSwitcher = false)
            }
        }
    }
}

/**
 * 「用户词库」页签：打字造词产生的 `<词典名>.userdb`（librime leveldb）。
 * 只读：列表 → 点进某本看词条。读取需在 native 侧销毁/重建输入会话，故用
 * [UserDictUiState.isReading] 反馈等待。
 */
@Composable
private fun UserDictTabContent(
    viewModel: UserDictViewModel,
    uiState: UserDictUiState,
) {
    val opened = uiState.openedDict
    if (opened == null) {
        UserDictList(viewModel = viewModel, uiState = uiState)
    } else {
        UserDictEntries(viewModel = viewModel, uiState = uiState, dictName = opened)
    }
}

@Composable
private fun UserDictList(
    viewModel: UserDictViewModel,
    uiState: UserDictUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "用户词库由打字造词自动生成，此处只读查看；跨设备搬运请用「同步与备份」里的词库同步。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.dicts.isEmpty() -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    UsageHint(
                        title = "还没有用户词库",
                        message = "用某个方案打过字之后，引擎会自动生成对应的 .userdb",
                        action = "查看词库说明 →"
                    )
                }
            }
            else -> {
                Text(
                    "共 ${uiState.dicts.size} 本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(items = uiState.dicts, key = { _, d -> d.name }) { _, dict ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.open(dict.name) },
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dict.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        "最近更新 ${formatUserDictTime(dict.lastModified)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UserDictEntries(
    viewModel: UserDictViewModel,
    uiState: UserDictUiState,
    dictName: String,
) {
    // 导出到 / 从系统文件选择器导入：均走 librime 的文本码表（与 PC 端同格式）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.close() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回词库列表")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(dictName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (uiState.searchQuery.isEmpty()) "${uiState.entries.size} 条"
                    else "匹配 ${uiState.filteredEntries.size} / ${uiState.entries.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.isTransferring) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("导出文本码表") },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch(UserDictIoManager.exportFileName(dictName))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导入文本码表") },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }
        }
        uiState.message?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.messageIsError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                    )
                    IconButton(onClick = { viewModel.dismissMessage() }) {
                        Icon(
                            Icons.Default.Clear, contentDescription = "关闭提示",
                            modifier = Modifier.size(18.dp),
                            tint = if (uiState.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search, contentDescription = null,
                    tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = uiState.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        Box {
                            if (uiState.searchQuery.isEmpty()) {
                                Text("搜索词条或编码", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                    }
                )
                if (uiState.searchQuery.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.clearSearch() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            uiState.isReading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "正在读取词库…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            uiState.entries.isEmpty() -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    UsageHint(
                        title = "这本词库还没有词条",
                        message = "继续用该方案打字，造词会自动写进用户词库",
                        action = "返回词库列表 →"
                    )
                }
            }
            uiState.filteredEntries.isEmpty() -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("未找到匹配条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = uiState.filteredEntries,
                        key = { i, e -> "${e.word}_${e.code}_$i" }
                    ) { _, entry ->
                        // 用户词库只读：词条由引擎在打字过程中写入
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.word, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(entry.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (entry.weight != null) {
                                    Text(
                                        "频率 ${entry.weight}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val userDictTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatUserDictTime(timestamp: Long): String =
    if (timestamp <= 0L) "未知" else userDictTimeFormat.format(Date(timestamp))

@Composable
private fun UsageHint(
    title: String,
    message: String,
    action: String,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .clickable { uriHandler.openUri("https://ime.ximei.me/features/dictionary.html") }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(action,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CustomPhraseTabContent(
    viewModel: CustomPhraseViewModel,
    uiState: CustomPhraseUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(value = uiState.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        Box { if (uiState.searchQuery.isEmpty()) Text("搜索", color = MaterialTheme.colorScheme.onSurfaceVariant); innerTextField() }
                    })
                if (uiState.searchQuery.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.clearSearch() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                UsageHint(
                    title = "暂无词条",
                    message = "自定义短语用于补充方案码表之外的常用短语",
                    action = "点右下角「+」添加，然后在「输入方案」重新部署即可生效"
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(uiState.filteredEntries, key = { i, _ -> "cp_$i" }) { i, entry ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(entry.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                if (entry.weight != null) {
                                    Text("权重: ${entry.weight}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            IconButton(onClick = {
                                viewModel.setEditing(i, entry)
                                viewModel.showEditDialog()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteEntry(i) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        PhraseEditDialog(
            title = "添加快捷短语",
            word = uiState.editWord,
            code = uiState.editCode,
            weight = uiState.editWeight,
            onWordChange = viewModel::setEditWord,
            onCodeChange = viewModel::setEditCode,
            onWeightChange = viewModel::setEditWeight,
            onConfirm = { viewModel.addEntry(uiState.editWord, uiState.editCode, uiState.editWeight.toIntOrNull()); viewModel.hideAddDialog() },
            onDismiss = viewModel::hideAddDialog,
        )
    }
    if (uiState.showEditDialog) {
        PhraseEditDialog(
            title = "编辑快捷短语",
            word = uiState.editWord,
            code = uiState.editCode,
            weight = uiState.editWeight,
            onWordChange = viewModel::setEditWord,
            onCodeChange = viewModel::setEditCode,
            onWeightChange = viewModel::setEditWeight,
            onConfirm = { viewModel.updateEntry(uiState.editIndex, uiState.editWord, uiState.editCode, uiState.editWeight.toIntOrNull()); viewModel.hideEditDialog() },
            onDismiss = viewModel::hideEditDialog,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PhraseEditDialog(
    title: String,
    word: String,
    code: String,
    weight: String,
    onWordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp))
            OutlinedTextField(value = word, onValueChange = onWordChange,
                label = { Text("短语") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = code, onValueChange = onCodeChange,
                label = { Text("编码") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = weight, onValueChange = onWeightChange,
                label = { Text("权重（可选，越大越优先）") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.clickable(enabled = word.isNotBlank() && code.isNotBlank(), onClick = onConfirm),
                    shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary
                ) {
                    Text("确定", modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}