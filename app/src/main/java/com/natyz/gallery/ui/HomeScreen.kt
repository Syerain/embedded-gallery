package com.natyz.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.natyz.gallery.AppViewModel
import com.natyz.gallery.data.Gallery
import com.natyz.gallery.data.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (Gallery) -> Unit, onSettings: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var onlyFav by remember { mutableStateOf(false) }
    val list = remember(ui.favGalleries, onlyFav) {
        vm.galleries.filter { !onlyFav || ui.favGalleries.contains(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery Natyz", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onlyFav = !onlyFav }) {
                        Icon(
                            if (onlyFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏筛选"
                        )
                    }
                    IconButton(onClick = {
                        vm.setViewMode(if (ui.viewMode == "waterfall") "list" else "waterfall")
                    }) {
                        Icon(
                            if (ui.viewMode == "waterfall") Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "切换视图"
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("无收藏画集", color = MaterialTheme.colorScheme.outline)
            }
            return@Scaffold
        }
        if (ui.viewMode == "waterfall") {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                items(list, key = { it.id }) { g ->
                    GalleryCard(g, vm.titleOf(g, ui), onClick = { onOpen(g) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(list, key = { it.id }) { g ->
                    GalleryRow(g, vm.titleOf(g, ui), onClick = { onOpen(g) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryCard(g: Gallery, title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = "file:///android_asset/${g.cover}",
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(g.coverW.toFloat() / g.coverH.toFloat())
            )
            if (g.items.any { it.type == MediaType.VIDEO }) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "含视频",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(50))
                        .padding(1.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (g.titleZh.isNotBlank()) {
                Text(
                    g.titleZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${g.items.size} 项 · ${g.source}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun GalleryRow(g: Gallery, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = "file:///android_asset/${g.cover}",
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(140.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (g.titleZh.isNotBlank()) {
                Text(
                    g.titleZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                "${g.items.size} 项 · ${g.source}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
