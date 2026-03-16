package com.example.boostbank

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.boostbank.data.BoostBankRepository
import com.example.boostbank.model.AppSettings
import com.example.boostbank.model.ItemCategory
import com.example.boostbank.model.LogType
import com.example.boostbank.model.MainPage
import com.example.boostbank.model.ScoreItem
import com.example.boostbank.model.ScoreLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val LogTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

private sealed interface ImagePickerTarget {
    data object ItemEditor : ImagePickerTarget
    data class PageBackground(val page: MainPage) : ImagePickerTarget
}

private data class ItemEditorState(
    val category: ItemCategory,
    val item: ScoreItem? = null
)

@Composable
fun BoostBankApp() {
    val context = LocalContext.current
    val repository = remember { BoostBankRepository(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    val earnItems by repository.earnItems.collectAsState(initial = emptyList())
    val rewardItems by repository.rewardItems.collectAsState(initial = emptyList())
    val scoreLogs by repository.logs.collectAsState(initial = emptyList())
    val totalScore by repository.totalScore.collectAsState(initial = 0)
    val settings by repository.settings.collectAsState(initial = AppSettings())

    var currentPage by rememberSaveable { mutableStateOf(MainPage.EARN) }
    var itemEditorState by remember { mutableStateOf<ItemEditorState?>(null) }
    var itemEditorImageUri by remember { mutableStateOf<String?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<ScoreItem?>(null) }
    var imagePickerTarget by remember { mutableStateOf<ImagePickerTarget?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        repository.seedDefaultsIfNeeded()
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = imagePickerTarget
        imagePickerTarget = null
        if (uri == null || target == null) {
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }

        when (target) {
            ImagePickerTarget.ItemEditor -> itemEditorImageUri = uri.toString()
            is ImagePickerTarget.PageBackground -> {
                coroutineScope.launch {
                    repository.setPageBackground(target.page, uri.toString())
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = currentPage == page,
                        onClick = { currentPage = page },
                        icon = { Text(page.shortLabel) },
                        label = { Text(page.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val pageBackgroundUri = settings.backgroundFor(currentPage)
            if (pageBackgroundUri != null) {
                AsyncImage(
                    model = pageBackgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (settings.useWarmBackground) {
                            Color(0xE8FFF7ED)
                        } else {
                            Color(0xECF8FAFC)
                        }
                    )
            )
            when (currentPage) {
                MainPage.EARN -> EarnPage(
                    items = earnItems,
                    totalScore = totalScore,
                    onAddRequest = {
                        itemEditorState = ItemEditorState(ItemCategory.EARN)
                        itemEditorImageUri = null
                    },
                    onEditRequest = { item ->
                        itemEditorState = ItemEditorState(ItemCategory.EARN, item)
                        itemEditorImageUri = item.imageUri
                    },
                    onDeleteRequest = { pendingDeleteItem = it },
                    onCompleteItem = { item ->
                        coroutineScope.launch {
                            repository.completeEarn(item.id)
                        }
                    }
                )

                MainPage.REWARD -> RewardPage(
                    items = rewardItems,
                    totalScore = totalScore,
                    confirmBeforeReward = settings.confirmBeforeReward,
                    onAddRequest = {
                        itemEditorState = ItemEditorState(ItemCategory.REWARD)
                        itemEditorImageUri = null
                    },
                    onEditRequest = { item ->
                        itemEditorState = ItemEditorState(ItemCategory.REWARD, item)
                        itemEditorImageUri = item.imageUri
                    },
                    onDeleteRequest = { pendingDeleteItem = it },
                    onRedeemItem = { item ->
                        coroutineScope.launch {
                            val success = repository.redeemReward(item.id)
                            if (!success) {
                                infoMessage = "当前积分不足，无法兑换 ${item.name}。"
                            }
                        }
                    }
                )

                MainPage.OVERVIEW -> OverviewPage(
                    totalScore = totalScore,
                    logs = scoreLogs,
                    onAdjustScore = { newScore ->
                        coroutineScope.launch {
                            repository.adjustTotalScore(newScore)
                        }
                    }
                )

                MainPage.ME -> MePage(
                    settings = settings,
                    onLanguageSelected = { language ->
                        coroutineScope.launch {
                            repository.setLanguage(language)
                        }
                    },
                    onConfirmBeforeRewardChanged = { enabled ->
                        coroutineScope.launch {
                            repository.setConfirmBeforeReward(enabled)
                        }
                    },
                    onWarmBackgroundChanged = { enabled ->
                        coroutineScope.launch {
                            repository.setUseWarmBackground(enabled)
                        }
                    },
                    onPickPageBackground = { page ->
                        imagePickerTarget = ImagePickerTarget.PageBackground(page)
                        openDocumentLauncher.launch(arrayOf("image/*"))
                    },
                    onClearPageBackground = { page ->
                        coroutineScope.launch {
                            repository.setPageBackground(page, null)
                        }
                    }
                )
            }
        }
    }

    val editorState = itemEditorState
    if (editorState != null) {
        ItemEditorDialog(
            state = editorState,
            imageUri = itemEditorImageUri,
            onDismiss = {
                itemEditorState = null
                itemEditorImageUri = null
            },
            onPickImage = {
                imagePickerTarget = ImagePickerTarget.ItemEditor
                openDocumentLauncher.launch(arrayOf("image/*"))
            },
            onClearImage = { itemEditorImageUri = null },
            onConfirm = { name, points ->
                coroutineScope.launch {
                    val current = editorState.item
                    if (current == null) {
                        repository.addItem(editorState.category, name, points, itemEditorImageUri)
                    } else {
                        repository.updateItem(current.id, name, points, itemEditorImageUri)
                    }
                    itemEditorState = null
                    itemEditorImageUri = null
                }
            }
        )
    }

    if (pendingDeleteItem != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("删除确认") },
            text = { Text("确定删除 ${pendingDeleteItem!!.name} 吗？已生成的积分日志不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    val deletingItem = pendingDeleteItem ?: return@TextButton
                    coroutineScope.launch {
                        repository.deleteItem(deletingItem.id)
                        pendingDeleteItem = null
                    }
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (infoMessage != null) {
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            title = { Text("提示") },
            text = { Text(infoMessage!!) },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun EarnPage(
    items: List<ScoreItem>,
    totalScore: Int,
    onAddRequest: () -> Unit,
    onEditRequest: (ScoreItem) -> Unit,
    onDeleteRequest: (ScoreItem) -> Unit,
    onCompleteItem: (ScoreItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "赚取积分",
                subtitle = "点击卡片完成事务并加分，当前总积分：$totalScore",
                actionText = "新增事务",
                onActionClick = onAddRequest
            )
        }

        items(items, key = { it.id }) { item ->
            ScoreItemCard(
                item = item,
                actionLabel = "完成一次 +${item.points}",
                accentColor = Color(0xFFD9F99D),
                onPrimaryClick = { onCompleteItem(item) },
                onEditClick = { onEditRequest(item) },
                onDeleteClick = { onDeleteRequest(item) }
            )
        }
    }
}

@Composable
private fun RewardPage(
    items: List<ScoreItem>,
    totalScore: Int,
    confirmBeforeReward: Boolean,
    onAddRequest: () -> Unit,
    onEditRequest: (ScoreItem) -> Unit,
    onDeleteRequest: (ScoreItem) -> Unit,
    onRedeemItem: (ScoreItem) -> Unit
) {
    var pendingReward by remember { mutableStateOf<ScoreItem?>(null) }
    var warningText by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "购买奖励",
                subtitle = "兑换前会校验积分是否充足，当前总积分：$totalScore",
                actionText = "新增奖励",
                onActionClick = onAddRequest
            )
        }

        items(items, key = { it.id }) { item ->
            ScoreItemCard(
                item = item,
                actionLabel = "兑换奖励 -${item.points}",
                accentColor = Color(0xFFFECACA),
                onPrimaryClick = {
                    if (item.points > totalScore) {
                        warningText = "当前积分不足，无法兑换 ${item.name}。"
                    } else if (confirmBeforeReward) {
                        pendingReward = item
                    } else {
                        onRedeemItem(item)
                    }
                },
                onEditClick = { onEditRequest(item) },
                onDeleteClick = { onDeleteRequest(item) }
            )
        }
    }

    if (pendingReward != null) {
        AlertDialog(
            onDismissRequest = { pendingReward = null },
            title = { Text("确认兑换") },
            text = { Text("确定花费 ${pendingReward!!.points} 积分兑换 ${pendingReward!!.name} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val reward = pendingReward ?: return@TextButton
                    onRedeemItem(reward)
                    pendingReward = null
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReward = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (warningText != null) {
        AlertDialog(
            onDismissRequest = { warningText = null },
            title = { Text("提示") },
            text = { Text(warningText!!) },
            confirmButton = {
                TextButton(onClick = { warningText = null }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun OverviewPage(
    totalScore: Int,
    logs: List<ScoreLog>,
    onAdjustScore: (Int) -> Unit
) {
    var showAdjustDialog by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf("全部") }

    val filteredLogs = logs.filter {
        when (selectedType) {
            "获取" -> it.type == LogType.EARN
            "消耗" -> it.type == LogType.SPEND
            "校准" -> it.type == LogType.ADJUST
            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "分数概览",
                subtitle = "总分、手动校准和积分日志都集中在这里。",
                actionText = "手动校准",
                onActionClick = { showAdjustDialog = true }
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("当前总积分", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = totalScore.toString(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "获取", "消耗", "校准").forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type) }
                    )
                }
            }
        }

        item {
            Text(
                text = "积分日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(filteredLogs, key = { it.id }) { log ->
            LogCard(log = log)
        }
    }

    if (showAdjustDialog) {
        AdjustScoreDialog(
            currentScore = totalScore,
            onDismiss = { showAdjustDialog = false },
            onConfirm = {
                onAdjustScore(it)
                showAdjustDialog = false
            }
        )
    }
}

@Composable
private fun MePage(
    settings: AppSettings,
    onLanguageSelected: (String) -> Unit,
    onConfirmBeforeRewardChanged: (Boolean) -> Unit,
    onWarmBackgroundChanged: (Boolean) -> Unit,
    onPickPageBackground: (MainPage) -> Unit,
    onClearPageBackground: (MainPage) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = "我的",
                subtitle = "设置会保存到本地，重启 App 后仍然生效。"
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("通用设置", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("语言")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("简体中文", "English").forEach { language ->
                            FilterChip(
                                selected = settings.language == language,
                                onClick = { onLanguageSelected(language) },
                                label = { Text(language) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingRow(
                        title = "兑换前二次确认",
                        value = settings.confirmBeforeReward,
                        onValueChange = onConfirmBeforeRewardChanged
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingRow(
                        title = "使用暖色背景遮罩",
                        value = settings.useWarmBackground,
                        onValueChange = onWarmBackgroundChanged
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("主页面背景", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    MainPage.entries.forEachIndexed { index, page ->
                        PageBackgroundRow(
                            page = page,
                            currentUri = settings.backgroundFor(page),
                            onPick = { onPickPageBackground(page) },
                            onClear = { onClearPageBackground(page) }
                        )
                        if (index != MainPage.entries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onActionClick) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun ScoreItemCard(
    item: ScoreItem,
    actionLabel: String,
    accentColor: Color,
    onPrimaryClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPrimaryClick)
        ) {
            if (item.imageUri != null) {
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xB3FFFFFF))
                )
            }
            Column(modifier = Modifier.padding(18.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("积分值：${item.points}")
                Spacer(modifier = Modifier.height(10.dp))
                Text(actionLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditClick) {
                        Text("编辑")
                    }
                    OutlinedButton(onClick = onDeleteClick) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: ScoreLog) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(log.source, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (log.delta >= 0) "+${log.delta}" else log.delta.toString(),
                    color = when (log.type) {
                        LogType.EARN -> Color(0xFF15803D)
                        LogType.SPEND -> Color(0xFFB91C1C)
                        LogType.ADJUST -> Color(0xFF1D4ED8)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(log.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text("操作后总积分：${log.afterScore}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = LogTimeFormatter.format(Instant.ofEpochMilli(log.createdAt)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PageBackgroundRow(
    page: MainPage,
    currentUri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(page.label, fontWeight = FontWeight.Medium)
        Text(
            text = if (currentUri == null) "当前未设置背景图" else "当前已选择背景图",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick) {
                Text("选择图片")
            }
            OutlinedButton(onClick = onClear, enabled = currentUri != null) {
                Text("清除")
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

@Composable
private fun ItemEditorDialog(
    state: ItemEditorState,
    imageUri: String?,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember(state.item?.id) { mutableStateOf(state.item?.name.orEmpty()) }
    var pointsText by remember(state.item?.id) {
        mutableStateOf(state.item?.points?.toString().orEmpty())
    }
    val points = pointsText.toIntOrNull()
    val canConfirm = name.isNotBlank() && points != null && points > 0
    val isEditing = state.item != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) {
                    if (state.category == ItemCategory.EARN) "编辑赚分事务" else "编辑奖励"
                } else {
                    if (state.category == ItemCategory.EARN) "新增赚分事务" else "新增奖励"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit) },
                    label = { Text("积分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickImage) {
                        Text(if (imageUri == null) "选择背景图片" else "更换背景图片")
                    }
                    OutlinedButton(onClick = onClearImage, enabled = imageUri != null) {
                        Text("清除图片")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), points!!) },
                enabled = canConfirm
            ) {
                Text(if (isEditing) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AdjustScoreDialog(
    currentScore: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var scoreText by remember { mutableStateOf(currentScore.toString()) }
    val newScore = scoreText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动校准积分") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前总积分：$currentScore")
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it.filter(Char::isDigit) },
                    label = { Text("新的总积分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newScore!!) },
                enabled = newScore != null
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}