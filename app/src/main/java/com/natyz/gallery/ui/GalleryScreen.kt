package com.natyz.gallery.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.natyz.gallery.AppViewModel
import com.natyz.gallery.data.GalleryItem
import com.natyz.gallery.data.MediaType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: AppViewModel,
    gid: String,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit
) {
    val ui by vm.ui.collectAsState()
    val gallery = remember(gid) { vm.gallery(gid) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

    var reorderMode by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val items = remember(ui.orders, gid) {
        gallery?.let { vm.effectiveItems(it, ui) } ?: emptyList()
    }
    var list by remember(items) { mutableStateOf(items) }

    // ---- export / import user data (orders, titles, favorites) ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(vm.exportJson().toByteArray())
                }
            }.onSuccess { toast("已导出") }
                .onFailure { toast("导出失败") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()?.let { text ->
                toast(if (vm.importJson(text)) "已导入" else "导入失败：文件格式不正确")
            }
        }
    }

    // ---- drag reorder state ----
    val dragging = remember { mutableStateOf(-1) }
    val dragOffset = remember { mutableStateOf(Offset.Zero) }

    fun saveOrder() {
        vm.setOrder(gid, list.map { it.id })
    }

    fun handleDrag(idx: Int, amount: Offset, posInItem: Offset) {
        if (dragging.value != idx) return
        dragOffset.value += amount
        val vis = gridState.layoutInfo.visibleItemsInfo
        val self = vis.firstOrNull { it.index == idx } ?: return
        val p = Offset(
            self.offset.x + posInItem.x + dragOffset.value.x,
            self.offset.y + posInItem.y + dragOffset.value.y
        )
        val target = vis.firstOrNull {
            p.x >= it.offset.x && p.x <= it.offset.x + it.size.width &&
                p.y >= it.offset.y && p.y <= it.offset.y + it.size.height
        } ?: return
        if (target.index != idx) {
            val ml = list.toMutableList()
            ml.add(target.index, ml.removeAt(idx))
            list = ml
            dragging.value = target.index
            dragOffset.value = Offset.Zero
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        // proportional edge auto-scroll (faster near the edge)
        val info = gridState.layoutInfo
        val edge = 170
        val distBottom = info.viewportEndOffset - p.y
        val distTop = p.y - info.viewportStartOffset
        when {
            distBottom < edge -> scope.launch {
                gridState.scrollBy((edge - distBottom).coerceAtLeast(0f) * 0.10f)
            }
            distTop < edge -> scope.launch {
                gridState.scrollBy(-(edge - distTop).coerceAtLeast(0f) * 0.10f)
            }
        }
    }

    if (gallery == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("画集不存在") }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            vm.titleOf(gallery, ui),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            buildString {
                                append("${list.size} 项")
                                if (reorderMode) append(" · 长按拖动排序")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reorderMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleGalleryFav(gid) }) {
                        Icon(
                            if (vm.isGalleryFav(gid, ui)) Icons.Filled.Favorite
                            else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏画集",
                            tint = if (vm.isGalleryFav(gid, ui)) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "重命名")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (reorderMode) "退出排序模式" else "排序模式（拖动）") },
                            onClick = { reorderMode = !reorderMode; menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("导出数据 JSON") },
                            onClick = { exportLauncher.launch("natyz_gallery.json"); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("导入数据 JSON") },
                            onClick = {
                                importLauncher.launch(arrayOf("application/json"))
                                menuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重置排序") },
                            onClick = { vm.resetOrder(gid); menuOpen = false }
                        )
                    }
                }
            )
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(list, key = { _, it -> it.id }) { idx, item ->
                Tile(
                    item = item,
                    index = idx,
                    fav = vm.isItemFav(gid, item.id, ui),
                    reorder = reorderMode,
                    draggingNow = dragging.value == idx,
                    offset = dragOffset.value,
                    onClick = { if (!reorderMode) onPlay(idx) },
                    onLongClick = { if (!reorderMode) vm.toggleItemFav(gid, item.id) },
                    onDragStart = {
                        dragging.value = idx
                        dragOffset.value = Offset.Zero
                    },
                    onDrag = { a, p -> handleDrag(idx, a, p) },
                    onDragEnd = {
                        dragging.value = -1
                        dragOffset.value = Offset.Zero
                        saveOrder()
                    }
                )
            }
        }
    }

    if (showEdit) {
        EditTitleDialog(
            current = vm.titleOf(gallery, ui),
            onDismiss = { showEdit = false },
            onOk = { vm.setTitle(gid, it); showEdit = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    item: GalleryItem,
    index: Int,
    fav: Boolean,
    reorder: Boolean,
    draggingNow: Boolean,
    offset: Offset,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentDrag by rememberUpdatedState(onDrag)
    val currentStart by rememberUpdatedState(onDragStart)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentIdx by rememberUpdatedState(index)

    Box(
        modifier = Modifier
            .aspectRatio(item.aspect)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .graphicsLayer {
                if (draggingNow) {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                    shadowElevation = 40f
                }
            }
            .border(
                width = if (reorder) 2.dp else 0.dp,
                color = if (reorder) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .pointerInput(reorder) {
                if (!reorder) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentStart()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag(amount, change.position)
                    },
                    onDragEnd = { currentEnd() },
                    onDragCancel = { currentEnd() }
                )
            }
    ) {
        AsyncImage(
            model = "file:///android_asset/${item.listPath}",
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.type == MediaType.VIDEO) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "视频",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            )
        }
        if (fav) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "已收藏",
                tint = Color(0xFFFFD54F),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
            )
        }
        if (reorder) {
            Text(
                "${currentIdx + 1}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
fun EditTitleDialog(current: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名画集") },
        text = {
            OutlinedTextField(value = t, onValueChange = { t = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (t.isNotBlank()) onOk(t.trim()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
