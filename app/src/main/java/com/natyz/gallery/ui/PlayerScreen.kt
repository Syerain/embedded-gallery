@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.natyz.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.natyz.gallery.AppViewModel
import com.natyz.gallery.data.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    vm: AppViewModel,
    gid: String,
    startIndex: Int,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val gallery = remember(gid) { vm.gallery(gid) }
    val items = remember(ui.orders, gid) {
        gallery?.let { vm.effectiveItems(it, ui) } ?: emptyList()
    }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("无内容", color = Color.White)
        }
        return
    }
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.size - 1)
    ) { items.size }
    var speed by remember { mutableFloatStateOf(1f) }
    val current = items.getOrNull(pager.currentPage)

    // lock orientation once per viewing session based on gyro/rotation-vector
    OrientationOnceLock()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("${pager.currentPage + 1} / ${items.size}", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    current?.let { item ->
                        IconButton(onClick = { vm.toggleItemFav(gid, item.id) }) {
                            Icon(
                                if (vm.isItemFav(gid, item.id, ui)) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (vm.isItemFav(gid, item.id, ui)) Color(0xFFFF8A80) else Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (current?.type == MediaType.VIDEO) {
                SpeedBar(speed = speed, onSelect = { speed = it })
            }
        }
    ) { pad ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(pad)
        ) { page ->
            val item = items[page]
            if (item.type == MediaType.IMAGE) {
                ZoomableImage(
                    path = item.path,
                    aspect = item.aspect,
                    animSpeed = ui.animSpeed,
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            } else {
                VideoPane(path = item.path, speed = speed, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Sample the rotation-vector sensor once when entering the player; lock the
 * activity to portrait / landscape(-reverse) accordingly for the whole visit.
 */
@Composable
private fun OrientationOnceLock() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var settled = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (settled) return
                settled = true
                val rm = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rm, event.values)
                val ori = FloatArray(3)
                SensorManager.getOrientation(rm, ori)
                val roll = Math.toDegrees(ori[2].toDouble())
                activity?.requestedOrientation =
                    if (abs(roll) > 45) {
                        if (roll > 0) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            runCatching { sm.unregisterListener(listener) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
private fun SpeedBar(speed: Float, onSelect: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SPEEDS.forEach { s ->
            FilterChip(
                selected = abs(speed - s) < 0.01f,
                onClick = { onSelect(s) },
                label = { Text(formatSpeed(s), color = Color.White) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0x22FFFFFF),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black
                )
            )
        }
    }
}

private fun formatSpeed(s: Float): String = if (s == s.toInt().toFloat()) "${s.toInt()}.0x" else "${s}x"

/**
 * Image viewer with:
 *  - discrete rotation (0/90/180/270) with snapping by default
 *  - long-press toggles free-rotation mode (no snapping); long-press again
 *    returns to discrete mode and snaps to the nearest 90° step
 *  - adaptive fill: at 90°/270° the image scales up to fill the viewport
 *  - pinch zoom / pan (pan only when zoomed in so horizontal swipes keep
 *    working for prev/next paging), scale snap-back near 1x
 *  - double tap toggles between 1x and 2.5x
 */
@Composable
fun ZoomableImage(path: String, aspect: Float, animSpeed: Float, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val rotation = remember { Animatable(0f) }          // actual displayed angle
    val rotIndex = remember { mutableIntStateOf(0) }    // discrete step 0..3
    val freeRotate = remember { mutableStateOf(false) }
    val fill = remember { Animatable(1f) }              // adaptive fill factor
    val dur = { base: Int -> (base / animSpeed).toInt().coerceAtLeast(1) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        // ContentScale.Fit size of the image inside this box
        val containerAspect = if (ch > 0) cw / ch else 0.75f
        val fitW: Float
        val fitH: Float
        if (aspect > containerAspect) {
            fitW = cw; fitH = if (aspect > 0) cw / aspect else ch
        } else {
            fitH = ch; fitW = ch * aspect
        }
        // fill factor needed to cover the box after a 90° rotation
        val rotFill = if (fitH > 0 && fitW > 0) max(cw / fitH, ch / fitW) else 1f

        fun animateToTargets(tRot: Float, tFill: Float, tScale: Float, tOff: Offset) {
            scope.launch {
                launch { rotation.animateTo(tRot, tween(dur(140))) }
                launch { fill.animateTo(tFill, tween(dur(140))) }
                launch { scale.animateTo(tScale, tween(dur(140))) }
                launch { offset.animateTo(tOff, tween(dur(140))) }
            }
        }

        AsyncImage(
            model = "file:///android_asset/$path",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val f = scale.value * fill.value
                    scaleX = f
                    scaleY = f
                    translationX = offset.value.x
                    translationY = offset.value.y
                    rotationZ = rotation.value
                }
                .pointerInput(path, freeRotate.value) {
                    awaitEachGesture {
                        var rotAccum = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val zm = event.calculateZoom()
                            val pan = event.calculatePan()
                            val rot = event.calculateRotation()
                            val canPan = scale.value > 1.05f
                            if (zm != 1f || rot != 0f || (pan != Offset.Zero && canPan)) {
                                val ns = (scale.value * zm).coerceIn(0.3f, 10f)
                                scope.launch {
                                    scale.snapTo(ns)
                                    if (canPan) offset.snapTo(offset.value + pan)
                                    if (freeRotate.value) {
                                        // free rotation: continuous, no snap
                                        rotation.snapTo(rotation.value + rot)
                                    } else if (rot != 0f) {
                                        // discrete rotation: step 90° per threshold
                                        rotAccum += rot
                                        if (rotAccum > 40f) {
                                            rotIndex.value = (rotIndex.value + 1) % 4
                                            rotAccum = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            launch {
                                                rotation.animateTo(rotIndex.value * 90f, tween(dur(140)))
                                                fill.animateTo(
                                                    if (rotIndex.value % 2 == 1) rotFill else 1f,
                                                    tween(dur(140))
                                                )
                                            }
                                        } else if (rotAccum < -40f) {
                                            rotIndex.value = (rotIndex.value + 3) % 4
                                            rotAccum = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            launch {
                                                rotation.animateTo(rotIndex.value * 90f, tween(dur(140)))
                                                fill.animateTo(
                                                    if (rotIndex.value % 2 == 1) rotFill else 1f,
                                                    tween(dur(140))
                                                )
                                            }
                                        }
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                            if (event.changes.all { !it.pressed }) break
                        }
                        // gesture end: snap scale back near 1x (rotation already discrete)
                        if (!freeRotate.value && abs(scale.value - 1f) < 0.15f) {
                            animateToTargets(
                                rotIndex.value * 90f,
                                if (rotIndex.value % 2 == 1) rotFill else 1f,
                                1f,
                                Offset.Zero
                            )
                        }
                    }
                }
                .pointerInput(path) {
                    detectTapGestures(
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (freeRotate.value) {
                                // exit free mode: snap to nearest 90°
                                freeRotate.value = false
                                val nearest = (rotation.value / 90f).roundToInt()
                                rotIndex.value = ((nearest % 4) + 4) % 4
                                animateToTargets(
                                    rotIndex.value * 90f,
                                    if (rotIndex.value % 2 == 1) rotFill else 1f,
                                    scale.value,
                                    offset.value
                                )
                            } else {
                                freeRotate.value = true
                            }
                        },
                        onDoubleTap = {
                            scope.launch {
                                if (scale.value > 1.4f) {
                                    scale.animateTo(1f, tween(dur(100)))
                                    offset.animateTo(Offset.Zero, tween(dur(100)))
                                } else {
                                    scale.animateTo(2.5f, tween(dur(100)))
                                }
                            }
                        }
                    )
                }
        )
    }
}

@Composable
fun VideoPane(path: String, speed: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///$path"))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // active slow-motion scrub (long-press center, slide horizontally)
    var scrubSpeed by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(path) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(playing, path) {
        while (playing) {
            position = player.currentPosition
            delay(250)
        }
    }
    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
    }

    Column(modifier = modifier.background(Color.Black)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(path) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = {
                            if (playing) player.pause() else player.play()
                        }
                    )
                }
                .pointerInput(path) {
                    // long-press center -> slow-motion speed scrub while held
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val lp = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        lp.consume()
                        val startX = lp.position.x
                        val startSpeed = player.playbackParameters.speed
                        scrubSpeed = startSpeed
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val dx = change.position.x - startX
                            val f = (startSpeed + dx / 220f).coerceIn(0.1f, 3f)
                            scrubSpeed = f
                            player.setPlaybackSpeed(f)
                            change.consume()
                        }
                        scrubSpeed = null
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            scrubSpeed?.let { f ->
                Column(
                    Modifier
                        .align(Alignment.Center)
                       background(Color(0xB3000000), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (f == f.toInt().toFloat()) "${f.toInt()}.0x" else "%.2fx".format(f),
                        color = Color.White,
                        fontSize = 30.sp
                    )
                    Text("慢放 · 左右滑动调整", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                }
            }
        }
        if (showControls) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6101010))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White
                    )
                }
                Text(fmtTime(position), color = Color.White, fontSize = 12.sp)
                Slider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { f ->
                        val ms = (f * duration).toLong()
                        player.seekTo(ms)
                        position = ms
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFE53935),
                        inactiveTrackColor = Color(0x55FFFFFF)
                    )
                )
                Text(fmtTime(duration), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun fmtTime(ms: Long): String =
    "%d:%02d".format(ms / 60000, (ms % 60000) / 1000)
