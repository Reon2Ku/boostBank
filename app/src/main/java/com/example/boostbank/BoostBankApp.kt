package com.example.boostbank

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.example.boostbank.ui.theme.BoostBankTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

import androidx.compose.ui.unit.IntOffset

private val LogTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

private sealed interface ImagePickerTarget {
    data object ItemEditor : ImagePickerTarget
    data object Avatar : ImagePickerTarget
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
    var itemEditorImageBiasX by remember { mutableStateOf(0f) }
    var itemEditorImageBiasY by remember { mutableStateOf(0f) }
    var itemEditorImageScale by remember { mutableStateOf(1f) }
    var pendingDeleteItem by remember { mutableStateOf<ScoreItem?>(null) }
    var imagePickerTarget by remember { mutableStateOf<ImagePickerTarget?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var avatarCropUri by remember { mutableStateOf<String?>(null) }

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
            ImagePickerTarget.ItemEditor -> {
                itemEditorImageUri = uri.toString()
                itemEditorImageBiasX = 0f
                itemEditorImageBiasY = 0f
                itemEditorImageScale = 1f
            }
            ImagePickerTarget.Avatar -> {
                avatarCropUri = uri.toString()
            }
            is ImagePickerTarget.PageBackground -> {
                coroutineScope.launch {
                    repository.setPageBackground(target.page, uri.toString())
                }
            }
        }
    }

    BoostBankTheme(forceNightMode = if (settings.nightMode) true else null) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val lang = settings.language
                MainPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = currentPage == page,
                        onClick = { currentPage = page },
                        icon = {
                            Text(
                                when (page) {
                                    MainPage.EARN -> s("赚", "E", lang)
                                    MainPage.REWARD -> s("奖", "R", lang)
                                    MainPage.OVERVIEW -> s("览", "O", lang)
                                    MainPage.ME -> s("我", "M", lang)
                                }
                            )
                        },
                        label = {
                            Text(
                                when (page) {
                                    MainPage.EARN -> s("赚取积分", "Earn", lang)
                                    MainPage.REWARD -> s("购买奖励", "Rewards", lang)
                                    MainPage.OVERVIEW -> s("分数概览", "Overview", lang)
                                    MainPage.ME -> s("我的", "Me", lang)
                                }
                            )
                        }
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
                        if (settings.nightMode) {
                            Color(0xFF1A1A2E).copy(alpha = (settings.backgroundMaskOpacity * 0.75f).coerceIn(0.1f, 0.85f))
                        } else if (settings.useWarmBackground) {
                            Color(0xFFFFF7ED).copy(alpha = settings.backgroundMaskOpacity)
                        } else {
                            Color(0xFFF8FAFC).copy(alpha = settings.backgroundMaskOpacity)
                        }
                    )
            )
            when (currentPage) {
                MainPage.EARN -> EarnPage(
                    items = earnItems,
                    totalScore = totalScore,
                    confirmBeforeEarn = settings.confirmBeforeEarn,
                    cardImageOpacity = settings.cardImageOpacity,
                    lang = settings.language,
                    onAddRequest = {
                        itemEditorState = ItemEditorState(ItemCategory.EARN)
                        itemEditorImageUri = null
                        itemEditorImageBiasX = 0f
                        itemEditorImageBiasY = 0f
                        itemEditorImageScale = 1f
                    },
                    onEditRequest = { item ->
                        itemEditorState = ItemEditorState(ItemCategory.EARN, item)
                        itemEditorImageUri = item.imageUri
                        itemEditorImageBiasX = item.imageBiasX
                        itemEditorImageBiasY = item.imageBiasY
                        itemEditorImageScale = item.imageScale
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
                    cardImageOpacity = settings.cardImageOpacity,
                    lang = settings.language,
                    onAddRequest = {
                        itemEditorState = ItemEditorState(ItemCategory.REWARD)
                        itemEditorImageUri = null
                        itemEditorImageBiasX = 0f
                        itemEditorImageBiasY = 0f
                        itemEditorImageScale = 1f
                    },
                    onEditRequest = { item ->
                        itemEditorState = ItemEditorState(ItemCategory.REWARD, item)
                        itemEditorImageUri = item.imageUri
                        itemEditorImageBiasX = item.imageBiasX
                        itemEditorImageBiasY = item.imageBiasY
                        itemEditorImageScale = item.imageScale
                    },
                    onDeleteRequest = { pendingDeleteItem = it },
                    onRedeemItem = { item ->
                        coroutineScope.launch {
                            repository.redeemReward(item.id)
                        }
                    }
                )

                MainPage.OVERVIEW -> OverviewPage(
                    totalScore = totalScore,
                    logs = scoreLogs,
                    lang = settings.language,
                    onAdjustScore = { newScore ->
                        coroutineScope.launch {
                            repository.adjustTotalScore(newScore)
                        }
                    }
                )

                MainPage.ME -> MePage(
                    settings = settings,
                    lang = settings.language,
                    onLanguageSelected = { language ->
                        coroutineScope.launch {
                            repository.setLanguage(language)
                        }
                    },
                    onConfirmBeforeEarnChanged = { enabled ->
                        coroutineScope.launch {
                            repository.setConfirmBeforeEarn(enabled)
                        }
                    },
                    onConfirmBeforeRewardChanged = { enabled ->
                        coroutineScope.launch {
                            repository.setConfirmBeforeReward(enabled)
                        }
                    },
                    onNightModeChanged = { enabled ->
                        coroutineScope.launch {
                            repository.setNightMode(enabled)
                        }
                    },
                    onWarmBackgroundChanged = { enabled ->
                        coroutineScope.launch {
                            repository.setUseWarmBackground(enabled)
                        }
                    },
                    onBackgroundMaskOpacityChanged = { opacity ->
                        coroutineScope.launch {
                            repository.setBackgroundMaskOpacity(opacity)
                        }
                    },
                    onCardImageOpacityChanged = { opacity ->
                        coroutineScope.launch {
                            repository.setCardImageOpacity(opacity)
                        }
                    },
                    onPickAvatar = {
                        imagePickerTarget = ImagePickerTarget.Avatar
                        openDocumentLauncher.launch(arrayOf("image/*"))
                    },
                    onCropAvatar = {
                        val uri = settings.avatarUri
                        if (uri != null) {
                            avatarCropUri = uri
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
            lang = settings.language,
            onDismiss = {
                itemEditorState = null
                itemEditorImageUri = null
                itemEditorImageBiasX = 0f
                itemEditorImageBiasY = 0f
                itemEditorImageScale = 1f
            },
            onPickImage = {
                imagePickerTarget = ImagePickerTarget.ItemEditor
                openDocumentLauncher.launch(arrayOf("image/*"))
            },
            imageBiasX = itemEditorImageBiasX,
            imageBiasY = itemEditorImageBiasY,
            imageScale = itemEditorImageScale,
            onImageBiasXChange = { itemEditorImageBiasX = it },
            onImageBiasYChange = { itemEditorImageBiasY = it },
            onImageScaleChange = { itemEditorImageScale = it },
            onClearImage = {
                itemEditorImageUri = null
                itemEditorImageBiasX = 0f
                itemEditorImageBiasY = 0f
                itemEditorImageScale = 1f
            },
            onConfirm = { name, points, biasX, biasY, imageScale ->
                coroutineScope.launch {
                    val current = editorState.item
                    if (current == null) {
                        repository.addItem(
                            editorState.category,
                            name,
                            points,
                            itemEditorImageUri,
                            biasX,
                            biasY,
                            imageScale
                        )
                    } else {
                        repository.updateItem(
                            current.id,
                            name,
                            points,
                            itemEditorImageUri,
                            biasX,
                            biasY,
                            imageScale
                        )
                    }
                    itemEditorState = null
                    itemEditorImageUri = null
                    itemEditorImageBiasX = 0f
                    itemEditorImageBiasY = 0f
                    itemEditorImageScale = 1f
                }
            }
        )
    }

    if (pendingDeleteItem != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(s("删除确认", "Delete Confirmation", settings.language)) },
            text = { Text(s("确定删除 ${pendingDeleteItem!!.name} 吗？已生成的积分日志不会被删除。",
                "Delete ${pendingDeleteItem!!.name}? Existing logs will not be removed.", settings.language)) },
            confirmButton = {
                TextButton(onClick = {
                    val deletingItem = pendingDeleteItem ?: return@TextButton
                    coroutineScope.launch {
                        repository.deleteItem(deletingItem.id)
                        pendingDeleteItem = null
                    }
                }) {
                    Text(s("删除", "Delete", settings.language))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(s("取消", "Cancel", settings.language))
                }
            }
        )
    }

    if (infoMessage != null) {
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            title = { Text(s("提示", "Notice", settings.language)) },
            text = { Text(infoMessage!!) },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) {
                    Text(s("知道了", "OK", settings.language))
                }
            }
        )
    }

    if (avatarCropUri != null) {
        AvatarCropDialog(
            imageUri = avatarCropUri!!,
            initialBiasX = settings.avatarBiasX,
            initialBiasY = settings.avatarBiasY,
            initialScale = settings.avatarScale,
            lang = settings.language,
            onConfirm = { biasX, biasY, scale ->
                val uri = avatarCropUri!!
                avatarCropUri = null
                coroutineScope.launch {
                    repository.setAvatarUri(uri)
                    repository.setAvatarCrop(biasX, biasY, scale)
                }
            },
            onDismiss = { avatarCropUri = null }
        )
    }
    } // BoostBankTheme
}

// ---------------------------------------------------------------------------
// Avatar Crop Dialog — gesture-based pan/zoom with circular preview
// ---------------------------------------------------------------------------
@Composable
private fun AvatarCropDialog(
    imageUri: String,
    initialBiasX: Float,
    initialBiasY: Float,
    initialScale: Float,
    lang: String,
    onConfirm: (biasX: Float, biasY: Float, scale: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(initialScale.coerceAtLeast(1f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("裁剪头像", "Crop Avatar", lang)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s("拖动和双指缩放来调整头像", "Drag and pinch to adjust", lang),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                val maxOffset = (zoom - 1f) * 120f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val maxOffset = (zoom - 1f) * 120f
                val biasX = if (maxOffset > 0f) (-offsetX / maxOffset).coerceIn(-1f, 1f) else 0f
                val biasY = if (maxOffset > 0f) (-offsetY / maxOffset).coerceIn(-1f, 1f) else 0f
                onConfirm(biasX, biasY, zoom)
            }) {
                Text(s("确定", "Confirm", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("取消", "Cancel", lang))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// String helper — returns the zh string if lang is Chinese, else en string
// ---------------------------------------------------------------------------
private fun s(zh: String, en: String, lang: String) = if (lang == "English") en else zh

private fun sanitizeSignedIntInput(input: String): String {
    if (input.isEmpty()) return ""
    val isNegative = input.startsWith("-")
    val digits = input.filter(Char::isDigit)
    return when {
        isNegative && digits.isEmpty() -> "-"
        isNegative -> "-$digits"
        else -> digits
    }
}

@Composable
private fun EarnPage(
    items: List<ScoreItem>,
    totalScore: Int,
    confirmBeforeEarn: Boolean,
    cardImageOpacity: Float,
    lang: String,
    onAddRequest: () -> Unit,
    onEditRequest: (ScoreItem) -> Unit,
    onDeleteRequest: (ScoreItem) -> Unit,
    onCompleteItem: (ScoreItem) -> Unit
) {
    var pendingEarnItem by remember { mutableStateOf<ScoreItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader(
                title = s("赚取积分", "Earn Points", lang),
                subtitle = s("点击卡片完成事务并加分，当前总积分：$totalScore",
                    "Tap a task to earn points. Current total: $totalScore", lang),
                actionText = s("新增事务", "Add Task", lang),
                onActionClick = onAddRequest
            )
        }

        gridItems(items, key = { it.id }) { item ->
            ScoreItemCard(
                item = item,
                actionLabel = s("完成一次 +${item.points}", "Complete +${item.points}", lang),
                accentColor = Color(0xFFD9F99D),
                cardImageOpacity = cardImageOpacity,
                lang = lang,
                compact = true,
                onPrimaryClick = {
                    if (confirmBeforeEarn) {
                        pendingEarnItem = item
                    } else {
                        onCompleteItem(item)
                    }
                },
                onEditClick = { onEditRequest(item) },
                onDeleteClick = { onDeleteRequest(item) }
            )
        }
    }

    if (pendingEarnItem != null) {
        AlertDialog(
            onDismissRequest = { pendingEarnItem = null },
            title = { Text(s("确认完成", "Confirm Task", lang)) },
            text = {
                Text(
                    s("确认完成《${pendingEarnItem!!.name}》，获得 +${pendingEarnItem!!.points} 积分？",
                        "Complete \"${pendingEarnItem!!.name}\" and earn +${pendingEarnItem!!.points} points?",
                        lang)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val earn = pendingEarnItem ?: return@TextButton
                    onCompleteItem(earn)
                    pendingEarnItem = null
                }) {
                    Text(s("确认", "Confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEarnItem = null }) {
                    Text(s("取消", "Cancel", lang))
                }
            }
        )
    }
}

@Composable
private fun RewardPage(
    items: List<ScoreItem>,
    totalScore: Int,
    confirmBeforeReward: Boolean,
    cardImageOpacity: Float,
    lang: String,
    onAddRequest: () -> Unit,
    onEditRequest: (ScoreItem) -> Unit,
    onDeleteRequest: (ScoreItem) -> Unit,
    onRedeemItem: (ScoreItem) -> Unit
) {
    var pendingReward by remember { mutableStateOf<ScoreItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader(
                title = s("购买奖励", "Buy Rewards", lang),
                subtitle = s("允许透支积分（可为负数），当前总积分：$totalScore",
                    "Overdraft allowed (can go negative). Current total: $totalScore", lang),
                actionText = s("新增奖励", "Add Reward", lang),
                onActionClick = onAddRequest
            )
        }

        gridItems(items, key = { it.id }) { item ->
            ScoreItemCard(
                item = item,
                actionLabel = s("兑换奖励 -${item.points}", "Redeem -${item.points}", lang),
                accentColor = Color(0xFFFECACA),
                cardImageOpacity = cardImageOpacity,
                lang = lang,
                compact = true,
                onPrimaryClick = {
                    if (confirmBeforeReward) {
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
            title = { Text(s("确认兑换", "Confirm Redemption", lang)) },
            text = { Text(s("确定花费 ${pendingReward!!.points} 积分兑换 ${pendingReward!!.name} 吗？",
                "Spend ${pendingReward!!.points} points to redeem ${pendingReward!!.name}?", lang)) },
            confirmButton = {
                TextButton(onClick = {
                    val reward = pendingReward ?: return@TextButton
                    onRedeemItem(reward)
                    pendingReward = null
                }) {
                    Text(s("确认", "Confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReward = null }) {
                    Text(s("取消", "Cancel", lang))
                }
            }
        )
    }
}

@Composable
private fun OverviewPage(
    totalScore: Int,
    logs: List<ScoreLog>,
    lang: String,
    onAdjustScore: (Int) -> Unit
) {
    var showAdjustDialog by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf("ALL") }

    val filteredLogs = logs.filter {
        when (selectedType) {
            "EARN" -> it.type == LogType.EARN
            "SPEND" -> it.type == LogType.SPEND
            "ADJUST" -> it.type == LogType.ADJUST
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
                title = s("分数概览", "Score Overview", lang),
                subtitle = s("总分、手动校准和积分日志都集中在这里。",
                    "Total score, manual adjustments and point logs are all here.", lang),
                actionText = s("手动校准", "Adjust", lang),
                onActionClick = { showAdjustDialog = true }
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("当前总积分", "Current Total Score", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                listOf(
                    "ALL" to s("全部", "All", lang),
                    "EARN" to s("获取", "Earn", lang),
                    "SPEND" to s("消耗", "Spend", lang),
                    "ADJUST" to s("校准", "Adjust", lang)
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedType == key,
                        onClick = { selectedType = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            Text(
                text = s("积分日志", "Point Logs", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(filteredLogs, key = { it.id }) { log ->
            LogCard(log = log, lang = lang)
        }
    }

    if (showAdjustDialog) {
        AdjustScoreDialog(
            currentScore = totalScore,
            lang = lang,
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
    lang: String,
    onLanguageSelected: (String) -> Unit,
    onConfirmBeforeEarnChanged: (Boolean) -> Unit,
    onConfirmBeforeRewardChanged: (Boolean) -> Unit,
    onNightModeChanged: (Boolean) -> Unit,
    onWarmBackgroundChanged: (Boolean) -> Unit,
    onBackgroundMaskOpacityChanged: (Float) -> Unit,
    onCardImageOpacityChanged: (Float) -> Unit,
    onPickAvatar: () -> Unit,
    onCropAvatar: () -> Unit,
    onPickPageBackground: (MainPage) -> Unit,
    onClearPageBackground: (MainPage) -> Unit
) {
    var showBackgroundSettings by rememberSaveable { mutableStateOf(false) }
    var showGeneralSettings by rememberSaveable { mutableStateOf(false) }

    if (showBackgroundSettings) {
        BackgroundSettingsPage(
            settings = settings,
            lang = lang,
            onBack = { showBackgroundSettings = false },
            onWarmBackgroundChanged = onWarmBackgroundChanged,
            onBackgroundMaskOpacityChanged = onBackgroundMaskOpacityChanged,
            onCardImageOpacityChanged = onCardImageOpacityChanged,
            onPickPageBackground = onPickPageBackground,
            onClearPageBackground = onClearPageBackground
        )
        return
    }

    if (showGeneralSettings) {
        GeneralSettingsPage(
            settings = settings,
            lang = lang,
            onBack = { showGeneralSettings = false },
            onLanguageSelected = onLanguageSelected,
            onConfirmBeforeEarnChanged = onConfirmBeforeEarnChanged,
            onConfirmBeforeRewardChanged = onConfirmBeforeRewardChanged,
            onNightModeChanged = onNightModeChanged
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = s("我的", "Me", lang),
                subtitle = s("设置会保存到本地，重启 App 后仍然生效。",
                    "Settings are saved locally and persist after restart.", lang)
            )
        }

        // Avatar card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s("头像", "Avatar", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (settings.avatarUri != null) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clipToBounds()
                        ) {
                            AsyncImage(
                                model = settings.avatarUri,
                                contentDescription = s("头像", "Avatar", lang),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(settings.avatarScale.coerceAtLeast(1f)),
                                contentScale = ContentScale.Crop,
                                alignment = BiasAlignment(settings.avatarBiasX, settings.avatarBiasY)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "?",
                                fontSize = 36.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPickAvatar) {
                            Text(s("选择头像", "Choose Avatar", lang))
                        }
                        if (settings.avatarUri != null) {
                            OutlinedButton(onClick = onCropAvatar) {
                                Text(s("裁剪", "Crop", lang))
                            }
                        }
                    }
                }
            }
        }

        // General Settings entry
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("通用设置", "General Settings", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        s("语言、二次确认等偏好设置。", "Language, confirmation preferences, etc.", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { showGeneralSettings = true }) {
                        Text(s("通用设置", "General Settings", lang))
                    }
                }
            }
        }

        // Background Settings entry
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("背景设置", "Background Settings", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        s("背景图片、遮罩与可见度设置。", "Background images, mask and visibility settings.", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { showBackgroundSettings = true }) {
                        Text(s("背景设置", "Background Settings", lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsPage(
    settings: AppSettings,
    lang: String,
    onBack: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onConfirmBeforeEarnChanged: (Boolean) -> Unit,
    onConfirmBeforeRewardChanged: (Boolean) -> Unit,
    onNightModeChanged: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = s("通用设置", "General Settings", lang),
                subtitle = s("语言、确认等偏好设置。", "Language, confirmation preferences.", lang),
                actionText = s("返回", "Back", lang),
                onActionClick = onBack
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("语言", "Language", lang), fontWeight = FontWeight.SemiBold)
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
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("确认设置", "Confirmation", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingRow(
                        title = s("完成任务前二次确认", "Confirm before earning", lang),
                        value = settings.confirmBeforeEarn,
                        onValueChange = onConfirmBeforeEarnChanged
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingRow(
                        title = s("兑换前二次确认", "Confirm before redeeming", lang),
                        value = settings.confirmBeforeReward,
                        onValueChange = onConfirmBeforeRewardChanged
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("夜间模式", "Night Mode", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingRow(
                        title = s("启用夜间模式", "Enable night mode", lang),
                        value = settings.nightMode,
                        onValueChange = onNightModeChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundSettingsPage(
    settings: AppSettings,
    lang: String,
    onBack: () -> Unit,
    onWarmBackgroundChanged: (Boolean) -> Unit,
    onBackgroundMaskOpacityChanged: (Float) -> Unit,
    onCardImageOpacityChanged: (Float) -> Unit,
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
                title = s("背景设置", "Background Settings", lang),
                subtitle = s("配置背景可见度和每个页面的背景图。", "Configure visibility and per-page background images.", lang),
                actionText = s("返回", "Back", lang),
                onActionClick = onBack
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("背景可见度", "Background Visibility", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingRow(
                        title = s("使用暖色背景遮罩", "Warm background tint", lang),
                        value = settings.useWarmBackground,
                        onValueChange = onWarmBackgroundChanged
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        s("背景遮罩透明度：${(settings.backgroundMaskOpacity * 100).roundToInt()}%",
                            "Mask opacity: ${(settings.backgroundMaskOpacity * 100).roundToInt()}%", lang)
                    )
                    Slider(
                        value = settings.backgroundMaskOpacity,
                        onValueChange = onBackgroundMaskOpacityChanged,
                        valueRange = 0.1f..1f
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        s("卡片图片遮罩透明度：${(settings.cardImageOpacity * 100).roundToInt()}%",
                            "Card image mask opacity: ${(settings.cardImageOpacity * 100).roundToInt()}%", lang)
                    )
                    Slider(
                        value = settings.cardImageOpacity,
                        onValueChange = onCardImageOpacityChanged,
                        valueRange = 0f..1f
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("主页面背景", "Page Backgrounds", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    MainPage.entries.forEachIndexed { index, page ->
                        PageBackgroundRow(
                            pageLabel = when (page) {
                                MainPage.EARN -> s("赚取积分", "Earn", lang)
                                MainPage.REWARD -> s("购买奖励", "Rewards", lang)
                                MainPage.OVERVIEW -> s("分数概览", "Overview", lang)
                                MainPage.ME -> s("我的", "Me", lang)
                            },
                            currentUri = settings.backgroundFor(page),
                            lang = lang,
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
    cardImageOpacity: Float = 0.70f,
    lang: String = "简体中文",
    compact: Boolean = false,
    onPrimaryClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.heightIn(min = 220.dp) else Modifier),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(if (compact) 16.dp else 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .clickable(onClick = onPrimaryClick)
        ) {
            if (item.imageUri != null) {
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(item.imageScale),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(item.imageBiasX, item.imageBiasY)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = cardImageOpacity))
                )
            }
            Column(modifier = Modifier.padding(if (compact) 12.dp else 18.dp)) {
                Text(
                    item.name,
                    style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                Text(s("积分值：${item.points}", "Points: ${item.points}", lang))
                Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
                Text(
                    actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEditClick) {
                        Text(s("编辑", "Edit", lang))
                    }
                    TextButton(onClick = onDeleteClick) {
                        Text(s("删除", "Delete", lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: ScoreLog, lang: String = "简体中文") {
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
            Text(s("操作后总积分：${log.afterScore}", "Score after: ${log.afterScore}", lang))
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
    pageLabel: String,
    currentUri: String?,
    lang: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pageLabel, fontWeight = FontWeight.Medium)
        Text(
            text = if (currentUri == null) s("当前未设置背景图", "No background set", lang)
                   else s("当前已选择背景图", "Background image set", lang),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick) {
                Text(s("选择图片", "Choose", lang))
            }
            OutlinedButton(onClick = onClear, enabled = currentUri != null) {
                Text(s("清除", "Clear", lang))
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
    lang: String = "简体中文",
    imageBiasX: Float,
    imageBiasY: Float,
    imageScale: Float,
    onImageBiasXChange: (Float) -> Unit,
    onImageBiasYChange: (Float) -> Unit,
    onImageScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onConfirm: (String, Int, Float, Float, Float) -> Unit
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
                    if (state.category == ItemCategory.EARN) s("编辑赚分事务", "Edit Earn Task", lang) else s("编辑奖励", "Edit Reward", lang)
                } else {
                    if (state.category == ItemCategory.EARN) s("新增赚分事务", "Add Earn Task", lang) else s("新增奖励", "Add Reward", lang)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s("名称", "Name", lang)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit) },
                    label = { Text(s("积分", "Points", lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (imageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clipToBounds()
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(imageScale),
                            contentScale = ContentScale.Crop,
                            alignment = BiasAlignment(imageBiasX, imageBiasY)
                        )
                    }
                    Text(s("调整截取区域（拖动滑块）", "Adjust crop area (drag sliders)", lang))
                    Text(s("缩放", "Scale", lang))
                    Slider(
                        value = imageScale,
                        onValueChange = onImageScaleChange,
                        valueRange = 1f..3f
                    )
                    Text(s("水平位置", "Horizontal position", lang))
                    Slider(
                        value = imageBiasX,
                        onValueChange = onImageBiasXChange,
                        valueRange = -1f..1f
                    )
                    Text(s("垂直位置", "Vertical position", lang))
                    Slider(
                        value = imageBiasY,
                        onValueChange = onImageBiasYChange,
                        valueRange = -1f..1f
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickImage) {
                        Text(s(if (imageUri == null) "选择背景图片" else "更换背景图片",
                            if (imageUri == null) "Choose Image" else "Change Image", lang))
                    }
                    OutlinedButton(onClick = onClearImage, enabled = imageUri != null) {
                        Text(s("清除图片", "Clear Image", lang))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), points!!, imageBiasX, imageBiasY, imageScale) },
                enabled = canConfirm
            ) {
                Text(s(if (isEditing) "保存" else "添加", if (isEditing) "Save" else "Add", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("取消", "Cancel", lang))
            }
        }
    )
}

@Composable
private fun AdjustScoreDialog(
    currentScore: Int,
    lang: String = "简体中文",
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var scoreText by remember { mutableStateOf(currentScore.toString()) }
    val newScore = scoreText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("手动校准积分", "Adjust Score", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s("当前总积分：$currentScore", "Current total: $currentScore", lang))
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = sanitizeSignedIntInput(it) },
                    label = { Text(s("新的总积分", "New total score", lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newScore!!) },
                enabled = newScore != null
            ) {
                Text(s("保存", "Save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("取消", "Cancel", lang))
            }
        }
    )
}