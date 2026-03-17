package com.example.boostbank

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.boostbank.model.StatTracker
import com.example.boostbank.ui.theme.BoostBankTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    val milestones by repository.milestones.collectAsState(initial = emptyList())
    val trackers by repository.trackers.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = AppSettings())

    var currentPage by rememberSaveable { mutableStateOf(MainPage.EARN) }
    var earnTab by rememberSaveable { mutableStateOf(0) }
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
                MainPage.EARN -> EarnTabContainer(
                    earnTab = earnTab,
                    onEarnTabChange = { earnTab = it },
                    earnItems = earnItems,
                    milestones = milestones,
                    totalScore = totalScore,
                    confirmBeforeEarn = settings.confirmBeforeEarn,
                    cardImageOpacity = settings.cardImageOpacity,
                    nightMode = settings.nightMode,
                    lang = settings.language,
                    onAddTask = {
                        itemEditorState = ItemEditorState(ItemCategory.EARN)
                        itemEditorImageUri = null
                        itemEditorImageBiasX = 0f
                        itemEditorImageBiasY = 0f
                        itemEditorImageScale = 1f
                    },
                    onEditTask = { item ->
                        itemEditorState = ItemEditorState(ItemCategory.EARN, item)
                        itemEditorImageUri = item.imageUri
                        itemEditorImageBiasX = item.imageBiasX
                        itemEditorImageBiasY = item.imageBiasY
                        itemEditorImageScale = item.imageScale
                    },
                    onDeleteTask = { pendingDeleteItem = it },
                    onCompleteTask = { item ->
                        coroutineScope.launch {
                            repository.completeEarn(item.id)
                        }
                    },
                    onAddMilestone = { name, points, uri, bx, by, scale ->
                        coroutineScope.launch {
                            repository.addMilestone(name, points, uri, bx, by, scale)
                        }
                    },
                    onEditMilestone = { item ->
                        itemEditorState = ItemEditorState(ItemCategory.EARN, item)
                        itemEditorImageUri = item.imageUri
                        itemEditorImageBiasX = item.imageBiasX
                        itemEditorImageBiasY = item.imageBiasY
                        itemEditorImageScale = item.imageScale
                    },
                    onDeleteMilestone = { pendingDeleteItem = it },
                    onCompleteMilestone = { item ->
                        coroutineScope.launch {
                            repository.completeMilestone(item.id)
                        }
                    },
                    onPickImage = {
                        imagePickerTarget = ImagePickerTarget.ItemEditor
                        openDocumentLauncher.launch(arrayOf("image/*"))
                    },
                    editorImageUri = itemEditorImageUri
                )

                MainPage.REWARD -> RewardPage(
                    items = rewardItems,
                    totalScore = totalScore,
                    confirmBeforeReward = settings.confirmBeforeReward,
                    cardImageOpacity = settings.cardImageOpacity,
                    nightMode = settings.nightMode,
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
                    earnItems = earnItems,
                    rewardItems = rewardItems,
                    trackers = trackers,
                    lang = settings.language,
                    onAdjustScore = { newScore, reason ->
                        coroutineScope.launch {
                            repository.adjustTotalScore(newScore, reason)
                        }
                    },
                    onAddTracker = { name, sources ->
                        coroutineScope.launch {
                            repository.addTracker(name, sources)
                        }
                    },
                    onDeleteTracker = { id ->
                        coroutineScope.launch {
                            repository.deleteTracker(id)
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
// Avatar Crop Dialog — full image view with circle overlay mask
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
    // zoom=1 means the image fills the container via ContentScale.Crop
    // User sees the full image first and drags/zooms to position the circle
    var zoom by remember { mutableStateOf(initialScale.coerceAtLeast(1f)) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerSizePx by remember { mutableStateOf(0) }
    var initialized by remember { mutableStateOf(false) }

    // Restore previous crop position once container is measured
    LaunchedEffect(containerSizePx) {
        if (containerSizePx > 0 && !initialized) {
            initialized = true
            val halfC = containerSizePx / 2f
            val maxOff = halfC * (zoom - 1f)
            offsetX = -initialBiasX * maxOff
            offsetY = -initialBiasY * maxOff
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    s("裁剪头像", "Crop Avatar", lang),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    s("拖动和缩放图片以调整头像区域",
                        "Drag & pinch the image to adjust", lang),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))

                // Image area — square container with circle mask
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clipToBounds()
                        .background(Color.Black)
                        .onSizeChanged { containerSizePx = it.width }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                val halfContainer = size.width / 2f
                                val maxOff = halfContainer * (zoom - 1f)
                                offsetX = (offsetX + pan.x).coerceIn(-maxOff, maxOff)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOff, maxOff)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // The full image — draggable and zoomable
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
                        contentScale = ContentScale.Fit
                    )

                    // Dark overlay with transparent circle cutout
                    val circleRadiusFraction = 0.42f // circle fills ~84% of container
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension * circleRadiusFraction

                        // Draw semi-transparent dark overlay
                        drawRect(Color.Black.copy(alpha = 0.55f))
                        // Punch out the circle
                        drawCircle(
                            color = Color.Transparent,
                            radius = r,
                            center = Offset(cx, cy),
                            blendMode = BlendMode.Clear
                        )
                        // Circle border
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(s("取消", "Cancel", lang))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val halfC = containerSizePx / 2f
                        val maxOff = (halfC * (zoom - 1f)).coerceAtLeast(0.001f)
                        val biasX = (-offsetX / maxOff).coerceIn(-1f, 1f)
                        val biasY = (-offsetY / maxOff).coerceIn(-1f, 1f)
                        onConfirm(biasX, biasY, zoom)
                    }) {
                        Text(s("确定", "Confirm", lang))
                    }
                }
            }
        }
    }
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
private fun EarnTabContainer(
    earnTab: Int,
    onEarnTabChange: (Int) -> Unit,
    earnItems: List<ScoreItem>,
    milestones: List<ScoreItem>,
    totalScore: Int,
    confirmBeforeEarn: Boolean,
    cardImageOpacity: Float,
    nightMode: Boolean,
    lang: String,
    onAddTask: () -> Unit,
    onEditTask: (ScoreItem) -> Unit,
    onDeleteTask: (ScoreItem) -> Unit,
    onCompleteTask: (ScoreItem) -> Unit,
    onAddMilestone: (String, Int, String?, Float, Float, Float) -> Unit,
    onEditMilestone: (ScoreItem) -> Unit,
    onDeleteMilestone: (ScoreItem) -> Unit,
    onCompleteMilestone: (ScoreItem) -> Unit,
    onPickImage: () -> Unit,
    editorImageUri: String?
) {
    val pagerState = rememberPagerState(initialPage = earnTab) { 2 }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(earnTab) {
        if (pagerState.currentPage != earnTab) pagerState.animateScrollToPage(earnTab)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != earnTab) onEarnTabChange(pagerState.currentPage)
    }

    if (earnTab == 1) {
        BackHandler { onEarnTabChange(0) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = earnTab == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                label = { Text(s("日常事务", "Daily Tasks", lang)) }
            )
            FilterChip(
                selected = earnTab == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                label = { Text(s("里程碑", "Milestones", lang)) }
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> EarnPage(
                    items = earnItems,
                    totalScore = totalScore,
                    confirmBeforeEarn = confirmBeforeEarn,
                    cardImageOpacity = cardImageOpacity,
                    nightMode = nightMode,
                    lang = lang,
                    onAddRequest = onAddTask,
                    onEditRequest = onEditTask,
                    onDeleteRequest = onDeleteTask,
                    onCompleteItem = onCompleteTask
                )
                1 -> MilestonePage(
                    milestones = milestones,
                    totalScore = totalScore,
                    cardImageOpacity = cardImageOpacity,
                    nightMode = nightMode,
                    lang = lang,
                    onEditRequest = onEditMilestone,
                    onDeleteRequest = onDeleteMilestone,
                    onCompleteMilestone = onCompleteMilestone,
                    onAddMilestone = onAddMilestone,
                    onPickImage = onPickImage,
                    editorImageUri = editorImageUri
                )
            }
        }
    }
}

@Composable
private fun MilestonePage(
    milestones: List<ScoreItem>,
    totalScore: Int,
    cardImageOpacity: Float,
    nightMode: Boolean,
    lang: String,
    onEditRequest: (ScoreItem) -> Unit,
    onDeleteRequest: (ScoreItem) -> Unit,
    onCompleteMilestone: (ScoreItem) -> Unit,
    onAddMilestone: (String, Int, String?, Float, Float, Float) -> Unit,
    onPickImage: () -> Unit,
    editorImageUri: String?
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingComplete by remember { mutableStateOf<ScoreItem?>(null) }

    val incomplete = milestones.filter { it.completedAt == null }
    val completed = milestones.filter { it.completedAt != null }

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
                title = s("里程碑", "Milestones", lang),
                subtitle = s("记录重大目标，完成后获得积分奖励。当前总积分：$totalScore",
                    "Track major goals. Earn points upon completion. Total: $totalScore", lang),
                actionText = s("新增里程碑", "Add Milestone", lang),
                onActionClick = { showAddDialog = true }
            )
        }

        if (incomplete.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    s("进行中", "In Progress", lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        gridItems(incomplete, key = { it.id }) { item ->
            ScoreItemCard(
                item = item,
                actionLabel = s("完成 +${item.points}", "Complete +${item.points}", lang),
                accentColor = Color(0xFFFDE68A),
                cardImageOpacity = cardImageOpacity,
                nightMode = nightMode,
                lang = lang,
                compact = true,
                onPrimaryClick = { pendingComplete = item },
                onEditClick = { onEditRequest(item) },
                onDeleteClick = { onDeleteRequest(item) }
            )
        }

        if (completed.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    s("已完成", "Completed", lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF15803D)
                )
            }
        }

        gridItems(completed, key = { it.id }) { item ->
            ScoreItemCard(
                item = item,
                actionLabel = s("✓ 已完成 +${item.points}", "✓ Done +${item.points}", lang),
                accentColor = Color(0xFFBBF7D0),
                cardImageOpacity = cardImageOpacity,
                nightMode = nightMode,
                lang = lang,
                compact = true,
                onPrimaryClick = {},
                onEditClick = {},
                onDeleteClick = { onDeleteRequest(item) }
            )
        }
    }

    if (pendingComplete != null) {
        AlertDialog(
            onDismissRequest = { pendingComplete = null },
            title = { Text(s("完成里程碑", "Complete Milestone", lang)) },
            text = {
                Text(s("确认完成「${pendingComplete!!.name}」？完成后将获得 +${pendingComplete!!.points} 积分，此操作不可撤销。",
                    "Complete \"${pendingComplete!!.name}\"? You will earn +${pendingComplete!!.points} points. This cannot be undone.",
                    lang))
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = pendingComplete ?: return@TextButton
                    onCompleteMilestone(m)
                    pendingComplete = null
                }) {
                    Text(s("确认完成", "Confirm", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingComplete = null }) {
                    Text(s("取消", "Cancel", lang))
                }
            }
        )
    }

    if (showAddDialog) {
        MilestoneEditorDialog(
            lang = lang,
            onDismiss = { showAddDialog = false },
            onPickImage = onPickImage,
            imageUri = editorImageUri,
            onConfirm = { name, points, biasX, biasY, scale ->
                onAddMilestone(name, points, editorImageUri, biasX, biasY, scale)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MilestoneEditorDialog(
    lang: String,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    imageUri: String?,
    onConfirm: (String, Int, Float, Float, Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pointsText by remember { mutableStateOf("") }
    val points = pointsText.toIntOrNull()
    val canConfirm = name.isNotBlank() && points != null && points > 0

    var zoom by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerW by remember { mutableStateOf(0) }
    var containerH by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s("新增里程碑", "Add Milestone", lang), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s("名称", "Name", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit) },
                    label = { Text(s("积分", "Points", lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (imageUri != null) {
                    Text(
                        s("拖动和缩放图片以调整位置", "Drag & pinch to adjust position", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .clipToBounds()
                            .background(Color.Black)
                            .onSizeChanged {
                                containerW = it.width
                                containerH = it.height
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    val halfW = size.width / 2f
                                    val halfH = size.height / 2f
                                    val maxOffX = halfW * (zoom - 1f)
                                    val maxOffY = halfH * (zoom - 1f)
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffX, maxOffX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffY, maxOffY)
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
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = onPickImage) {
                    Text(s(if (imageUri == null) "选择背景图片" else "更换背景图片",
                        if (imageUri == null) "Choose Image" else "Change Image", lang))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(s("取消", "Cancel", lang))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val halfW = containerW / 2f
                            val halfH = containerH / 2f
                            val maxOffX = (halfW * (zoom - 1f)).coerceAtLeast(0.001f)
                            val maxOffY = (halfH * (zoom - 1f)).coerceAtLeast(0.001f)
                            val biasX = (-offsetX / maxOffX).coerceIn(-1f, 1f)
                            val biasY = (-offsetY / maxOffY).coerceIn(-1f, 1f)
                            onConfirm(name.trim(), points!!, biasX, biasY, zoom)
                        },
                        enabled = canConfirm
                    ) {
                        Text(s("添加", "Add", lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun EarnPage(
    items: List<ScoreItem>,
    totalScore: Int,
    confirmBeforeEarn: Boolean,
    cardImageOpacity: Float,
    nightMode: Boolean = false,
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
                nightMode = nightMode,
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
    nightMode: Boolean = false,
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
                nightMode = nightMode,
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OverviewPage(
    totalScore: Int,
    logs: List<ScoreLog>,
    earnItems: List<ScoreItem>,
    rewardItems: List<ScoreItem>,
    trackers: List<StatTracker>,
    lang: String,
    onAdjustScore: (Int, String) -> Unit,
    onAddTracker: (String, List<String>) -> Unit,
    onDeleteTracker: (Long) -> Unit
) {
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showAddTrackerDialog by remember { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf("ALL") }
    var selectedTrackerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDeleteTracker by remember { mutableStateOf<StatTracker?>(null) }

    val activeTracker = trackers.find { it.id == selectedTrackerId }

    val filteredLogs = if (activeTracker != null) {
        logs.filter { it.source in activeTracker.trackedSources }
    } else {
        logs.filter {
            when (selectedType) {
                "EARN" -> it.type == LogType.EARN
                "SPEND" -> it.type == LogType.SPEND
                "ADJUST" -> it.type == LogType.ADJUST
                else -> true
            }
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "ALL" to s("全部", "All", lang),
                    "EARN" to s("获取", "Earn", lang),
                    "SPEND" to s("消耗", "Spend", lang),
                    "ADJUST" to s("校准", "Adjust", lang)
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedTrackerId == null && selectedType == key,
                        onClick = { selectedType = key; selectedTrackerId = null },
                        label = { Text(label) }
                    )
                }
                trackers.forEach { tracker ->
                    FilterChip(
                        selected = selectedTrackerId == tracker.id,
                        onClick = {
                            if (selectedTrackerId == tracker.id) {
                                selectedTrackerId = null
                                selectedType = "ALL"
                            } else {
                                selectedTrackerId = tracker.id
                                selectedType = ""
                            }
                        },
                        label = { Text(tracker.name) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showAddTrackerDialog = true },
                    label = { Text("+", fontWeight = FontWeight.Bold) }
                )
            }
        }

        // Stats card when a tracker is selected
        if (activeTracker != null) {
            item {
                val matchingLogs = logs.filter { it.source in activeTracker.trackedSources }
                val count = matchingLogs.size
                val lastTime = matchingLogs.firstOrNull()?.createdAt
                val avgInterval = if (count >= 2) {
                    val timestamps = matchingLogs.map { it.createdAt }.sorted()
                    val totalSpan = timestamps.last() - timestamps.first()
                    totalSpan / (count - 1)
                } else null

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                activeTracker.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { pendingDeleteTracker = activeTracker }) {
                                Text(s("删除", "Delete", lang), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(s("统计来源：", "Sources: ", lang) + activeTracker.trackedSources.joinToString("、"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(s("总次数", "Total", lang), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            if (lastTime != null) {
                                Column {
                                    Text(
                                        LogTimeFormatter.format(Instant.ofEpochMilli(lastTime)),
                                        fontSize = 16.sp, fontWeight = FontWeight.Medium
                                    )
                                    Text(s("最近一次", "Last Time", lang), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (avgInterval != null) {
                                val hours = avgInterval / 3_600_000.0
                                val freqText = if (hours < 24) {
                                    s("%.1f 小时".format(hours), "%.1f hrs".format(hours), lang)
                                } else {
                                    val days = hours / 24.0
                                    s("%.1f 天".format(days), "%.1f days".format(days), lang)
                                }
                                Column {
                                    Text(freqText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(s("平均间隔", "Avg Interval", lang), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (activeTracker != null) s("相关日志", "Related Logs", lang)
                       else s("积分日志", "Point Logs", lang),
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
            onConfirm = { score, reason ->
                onAdjustScore(score, reason)
                showAdjustDialog = false
            }
        )
    }

    if (showAddTrackerDialog) {
        AddTrackerDialog(
            earnItems = earnItems,
            rewardItems = rewardItems,
            lang = lang,
            onDismiss = { showAddTrackerDialog = false },
            onConfirm = { name, sources ->
                onAddTracker(name, sources)
                showAddTrackerDialog = false
            }
        )
    }

    if (pendingDeleteTracker != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTracker = null },
            title = { Text(s("删除统计项", "Delete Tracker", lang)) },
            text = { Text(s("确定删除「${pendingDeleteTracker!!.name}」吗？", "Delete \"${pendingDeleteTracker!!.name}\"?", lang)) },
            confirmButton = {
                TextButton(onClick = {
                    val tracker = pendingDeleteTracker ?: return@TextButton
                    onDeleteTracker(tracker.id)
                    pendingDeleteTracker = null
                    selectedTrackerId = null
                    selectedType = "ALL"
                }) {
                    Text(s("删除", "Delete", lang), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTracker = null }) {
                    Text(s("取消", "Cancel", lang))
                }
            }
        )
    }
}

@Composable
private fun AddTrackerDialog(
    earnItems: List<ScoreItem>,
    rewardItems: List<ScoreItem>,
    lang: String,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val allItems = earnItems + rewardItems
    val selectedNames = remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s("新增统计项", "Add Tracker", lang)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s("名称", "Name", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    s("选择要统计的事务/奖励：", "Select tasks/rewards to track:", lang),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (earnItems.isNotEmpty()) {
                        item {
                            Text(
                                s("事务", "Tasks", lang),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    items(earnItems) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedNames.value = if (item.name in selectedNames.value)
                                        selectedNames.value - item.name
                                    else selectedNames.value + item.name
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = item.name in selectedNames.value,
                                onCheckedChange = {
                                    selectedNames.value = if (it) selectedNames.value + item.name
                                    else selectedNames.value - item.name
                                }
                            )
                            Text(item.name)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("+${item.points}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rewardItems.isNotEmpty()) {
                        item {
                            Text(
                                s("奖励", "Rewards", lang),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    items(rewardItems) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedNames.value = if (item.name in selectedNames.value)
                                        selectedNames.value - item.name
                                    else selectedNames.value + item.name
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = item.name in selectedNames.value,
                                onCheckedChange = {
                                    selectedNames.value = if (it) selectedNames.value + item.name
                                    else selectedNames.value - item.name
                                }
                            )
                            Text(item.name)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("-${item.points}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), selectedNames.value.toList()) },
                enabled = name.isNotBlank() && selectedNames.value.isNotEmpty()
            ) {
                Text(s("确认", "Confirm", lang))
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
    var showUserGuide by rememberSaveable { mutableStateOf(false) }

    if (showBackgroundSettings) {
        BackHandler { showBackgroundSettings = false }
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
        BackHandler { showGeneralSettings = false }
        GeneralSettingsPage(
            settings = settings,
            lang = lang,
            onBack = { showGeneralSettings = false },
            onLanguageSelected = onLanguageSelected,
            onConfirmBeforeEarnChanged = onConfirmBeforeEarnChanged,
            onConfirmBeforeRewardChanged = onConfirmBeforeRewardChanged
        )
        return
    }

    if (showUserGuide) {
        BackHandler { showUserGuide = false }
        UserGuidePage(lang = lang, onBack = { showUserGuide = false })
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
                        val avatarZoom = settings.avatarScale.coerceAtLeast(1f)
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
                                    .graphicsLayer {
                                        scaleX = avatarZoom
                                        scaleY = avatarZoom
                                        val halfC = size.width / 2f
                                        val maxOff = halfC * (avatarZoom - 1f)
                                        translationX = -settings.avatarBiasX * maxOff
                                        translationY = -settings.avatarBiasY * maxOff
                                    },
                                contentScale = ContentScale.Fit
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

        // Night Mode toggle
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SettingRow(
                        title = s("夜间模式", "Night Mode", lang),
                        value = settings.nightMode,
                        onValueChange = onNightModeChanged
                    )
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

        // User Guide entry
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("用户指南", "User Guide", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        s("了解应用功能与使用方法。", "Learn about app features and how to use them.", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { showUserGuide = true }) {
                        Text(s("用户指南", "User Guide", lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserGuidePage(lang: String, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                title = s("用户指南", "User Guide", lang),
                subtitle = s("BoostBank 功能说明与使用方法。", "Features and usage of BoostBank.", lang),
                actionText = s("返回", "Back", lang),
                onActionClick = onBack
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("📌 基本介绍", "📌 Overview", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "BoostBank 是一款个人积分激励工具。通过完成事务获得积分，用积分兑换奖励，帮助你养成良好习惯。\n\n应用底部有 4 个导航页面：赚（赚取积分）、奖（购买奖励）、览（分数概览）、我（个人设置）。",
                        "BoostBank is a personal gamification tool. Earn points by completing tasks, spend points on rewards, and build good habits.\n\nThe app has 4 tabs: Earn, Rewards, Overview, and Me.",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("⭐ 赚取积分 — 日常事务", "⭐ Earn — Daily Tasks", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 点击「新增事务」创建任务，设置名称、积分值和背景图片\n• 点击卡片即可完成一次任务并获得积分，可反复完成\n• 常用事务会自动排到前面\n• 可在「通用设置」中开启确认弹窗防止误触",
                        "• Tap 'Add Task' to create a task with name, points, and background image\n• Tap a card to complete it and earn points (repeatable)\n• Frequently used tasks float to the top\n• Enable confirmation dialogs in General Settings to prevent accidental taps",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("🏆 赚取积分 — 里程碑", "🏆 Earn — Milestones", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 切换到「里程碑」标签页查看\n• 用于记录重大目标，每个里程碑只能完成一次\n• 完成后标记为「✓ 已完成」并移至页面底部\n• 可随时往下翻阅回顾已完成的里程碑",
                        "• Switch to the 'Milestones' tab to view\n• Track major goals — each milestone can only be completed once\n• Completed milestones are marked '✓ Done' and move to the bottom\n• Scroll down to review your completed milestones",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("🎁 购买奖励", "🎁 Rewards", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 点击「新增奖励」创建奖励项目\n• 点击卡片消耗积分兑换奖励\n• 允许透支（积分可为负数）\n• 操作方式与日常事务一致",
                        "• Tap 'Add Reward' to create a reward\n• Tap a card to spend points and redeem it\n• Overdraft is allowed (score can go negative)\n• Works the same as daily tasks",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("📊 分数概览", "📊 Score Overview", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 顶部显示当前总积分\n• 按类型筛选日志：全部 / 获取 / 消耗 / 校准\n• 「手动校准」可直接修改总积分（支持负数）\n• 所有操作均有时间戳记录",
                        "• Shows your current total score at the top\n• Filter logs by type: All / Earn / Spend / Adjust\n• Use 'Adjust' to manually set the total (supports negative)\n• All actions are timestamped",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("📈 自定义统计项", "📈 Custom Trackers", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 在分数概览页面，点击筛选栏末尾的「+」按钮\n• 输入名称并勾选要追踪的事务或奖励\n• 创建后以标签形式出现，点击可查看统计数据\n• 包括总次数、最近一次时间、平均间隔\n• 可删除不再需要的统计项",
                        "• On the Overview page, tap the '+' button at the end of the filter row\n• Enter a name and select tasks/rewards to track\n• Created trackers appear as filter chips\n• View total count, last time, and average interval\n• Delete trackers you no longer need",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("⚙️ 设置与个性化", "⚙️ Settings & Customization", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 夜间模式：在「我的」页面直接开关\n• 通用设置：语言切换、确认弹窗开关\n• 背景设置：每个页面可单独设置背景图片\n• 可调节背景遮罩和卡片图片透明度\n• 头像：选择图片后拖拽缩放调整显示区域\n• 所有设置保存在本地，重启 App 后仍然生效",
                        "• Night Mode: toggle directly on the Me page\n• General Settings: language switch, confirmation dialogs\n• Background Settings: set per-page background images\n• Adjust background mask and card image opacity\n• Avatar: pick an image, drag & pinch to crop\n• All settings are saved locally and persist after restart",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(s("💡 小提示", "💡 Tips", lang), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(s(
                        "• 卡片背景图片支持拖拽和双指缩放调整位置\n• 调整积分时可附加原因备注\n• 系统返回键/手势可从子页面返回上级页面",
                        "• Card background images support drag & pinch to adjust\n• Add a reason note when adjusting score\n• System back button/gesture navigates back from subpages",
                        lang
                    ), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onConfirmBeforeRewardChanged: (Boolean) -> Unit
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
    nightMode: Boolean = false,
    lang: String = "简体中文",
    compact: Boolean = false,
    onPrimaryClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
        colors = CardDefaults.cardColors(
            containerColor = if (nightMode) accentColor.copy(alpha = 0.12f) else accentColor.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(if (compact) 16.dp else 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .clickable(onClick = onPrimaryClick)
        ) {
            if (item.imageUri != null) {
                val imgScale = item.imageScale.coerceAtLeast(1f)
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = imgScale
                            scaleY = imgScale
                            val halfW = size.width / 2f
                            val halfH = size.height / 2f
                            translationX = -item.imageBiasX * halfW * (imgScale - 1f)
                            translationY = -item.imageBiasY * halfH * (imgScale - 1f)
                        },
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (nightMode) Color(0xFF1A1A2E).copy(alpha = cardImageOpacity)
                            else Color.White.copy(alpha = cardImageOpacity)
                        )
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

    // Local gesture state — avoids stale closure inside pointerInput
    var zoom by remember { mutableStateOf(imageScale.coerceAtLeast(1f)) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerW by remember { mutableStateOf(0) }
    var containerH by remember { mutableStateOf(0) }
    var gestureInitialized by remember { mutableStateOf(false) }

    // Restore previous crop position once container is measured
    LaunchedEffect(containerW, containerH) {
        if (containerW > 0 && containerH > 0 && !gestureInitialized) {
            gestureInitialized = true
            zoom = imageScale.coerceAtLeast(1f)
            val halfW = containerW / 2f
            val halfH = containerH / 2f
            offsetX = -imageBiasX * halfW * (zoom - 1f)
            offsetY = -imageBiasY * halfH * (zoom - 1f)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (isEditing) {
                        if (state.category == ItemCategory.EARN) s("编辑赚分事务", "Edit Earn Task", lang) else s("编辑奖励", "Edit Reward", lang)
                    } else {
                        if (state.category == ItemCategory.EARN) s("新增赚分事务", "Add Earn Task", lang) else s("新增奖励", "Add Reward", lang)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s("名称", "Name", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit) },
                    label = { Text(s("积分", "Points", lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (imageUri != null) {
                    Text(
                        s("拖动和缩放图片以调整位置", "Drag & pinch to adjust position", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .clipToBounds()
                            .background(Color.Black)
                            .onSizeChanged {
                                containerW = it.width
                                containerH = it.height
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    val halfW = size.width / 2f
                                    val halfH = size.height / 2f
                                    val maxOffX = halfW * (zoom - 1f)
                                    val maxOffY = halfH * (zoom - 1f)
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffX, maxOffX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffY, maxOffY)
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
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(s("取消", "Cancel", lang))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            // Convert local offset/zoom to bias values
                            val halfW = containerW / 2f
                            val halfH = containerH / 2f
                            val maxOffX = (halfW * (zoom - 1f)).coerceAtLeast(0.001f)
                            val maxOffY = (halfH * (zoom - 1f)).coerceAtLeast(0.001f)
                            val biasX = (-offsetX / maxOffX).coerceIn(-1f, 1f)
                            val biasY = (-offsetY / maxOffY).coerceIn(-1f, 1f)
                            onConfirm(name.trim(), points!!, biasX, biasY, zoom)
                        },
                        enabled = canConfirm
                    ) {
                        Text(s(if (isEditing) "保存" else "添加", if (isEditing) "Save" else "Add", lang))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustScoreDialog(
    currentScore: Int,
    lang: String = "简体中文",
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var scoreText by remember { mutableStateOf(currentScore.toString()) }
    var reason by remember { mutableStateOf("") }
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
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(s("调整原因（可选）", "Reason (optional)", lang)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newScore!!, reason.trim()) },
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