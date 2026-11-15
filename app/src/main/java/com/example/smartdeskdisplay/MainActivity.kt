package com.example.smartdeskdisplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random

val Context.dataStore by preferencesDataStore(name = "desk_display_prefs")
val TASKS_KEY = stringSetPreferencesKey("local_tasks")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        val wallpaperDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "DeskWallpapers"
        )
        if (!wallpaperDir.exists()) wallpaperDir.mkdirs()

        setContent {
            SmartDisplayApp(
                wallpaperDir = wallpaperDir,
                onExitApp = { finishAndRemoveTask() }
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
fun SmartDisplayApp(wallpaperDir: File, onExitApp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.any { it }
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var isNightMode by remember { mutableStateOf(false) }
    var weatherText by remember { mutableStateOf("جاري جلب الطقس...") }

    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = tween(2500),
        label = "BurnInOffset"
    )

    var wallpaperUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentBgIndex by remember { mutableIntStateOf(0) }

    val tasks = remember { mutableStateListOf<String>() }

    var isCharging by remember { mutableStateOf(true) }
    var batteryLevel by remember { mutableIntStateOf(100) }

    var showControls by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val savedTasks = prefs[TASKS_KEY]
        if (savedTasks != null) {
            tasks.addAll(savedTasks)
        } else {
            tasks.addAll(listOf("مراجعة مهام اليوم", "شرب الماء بانتظام"))
        }
    }

    fun saveTasksToStorage() {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[TASKS_KEY] = tasks.toSet()
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            isNightMode = (hour >= 23 || hour < 6)

            val activity = context as? Activity
            activity?.window?.let { win ->
                val params = win.attributes
                params.screenBrightness = if (isNightMode) 0.03f else 0.75f
                win.attributes = params
            }

            currentTime = SimpleDateFormat("hh:mm", Locale.getDefault()).format(cal.time)
            currentDate = SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(cal.time)
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000L)
            targetOffset = Offset(
                Random.nextInt(-35, 35).toFloat(),
                Random.nextInt(-25, 25).toFloat()
            )
        }
    }

    LaunchedEffect(permissionsGranted) {
        while (true) {
            val files = wallpaperDir.listFiles { file ->
                file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            }?.map { Uri.fromFile(it) } ?: emptyList()

            wallpaperUris = files
            if (wallpaperUris.isNotEmpty()) {
                currentBgIndex = (currentBgIndex + 1) % wallpaperUris.size
            }
            delay(600_000L)
        }
    }

    LaunchedEffect(permissionsGranted) {
        while (true) {
            weatherText = fetchRealWeather(context)
            delay(1_800_000L)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000L)
            showControls = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )

    val primaryTextColor = if (isNightMode) Color(0xFFB71C1C) else Color.White
    val secondaryTextColor = if (isNightMode) Color(0xFF7F0000) else Color.LightGray
    val accentColor = if (isNightMode) Color(0xFF8E0000) else Color(0xFF81D4FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isCharging) Modifier.border(BorderStroke(4.dp, Color(0xFFFF6D00)))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        if (!isNightMode && wallpaperUris.isNotEmpty()) {
            Crossfade(
                targetState = wallpaperUris[currentBgIndex % wallpaperUris.size],
                animationSpec = tween(3000),
                label = "WallpaperFade"
            ) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 32.dp)
                .offset {
                    IntOffset(
                        animatedOffset.x.roundToInt(),
                        animatedOffset.y.roundToInt()
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTime,
                        fontSize = 108.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(dotAlpha)
                            .background(accentColor, CircleShape)
                    )
                }

                Text(
                    text = currentDate,
                    fontSize = 24.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = weatherText,
                    fontSize = 22.sp,
                    color = if (isNightMode) secondaryTextColor else Color(0xFFFFD54F)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isCharging) accentColor else Color(0xFFFF6D00),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCharging) "متصل بالشاحن ($batteryLevel%)" else "تنبيه: الشاحن مفصول ($batteryLevel%)",
                        fontSize = 15.sp,
                        color = if (isCharging) secondaryTextColor else Color(0xFFFF6D00)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .width(330.dp)
                    .background(
                        color = if (isNightMode) Color(0xFF0A0000) else Color(0xFF121212).copy(alpha = 0.75f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Text(
                    text = "المهام (${tasks.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (tasks.isEmpty()) {
                    Text(
                        text = "لا توجد مهام — المس الشاشة للإضافة",
                        color = secondaryTextColor,
                        fontSize = 15.sp
                    )
                } else {
                    tasks.take(6).forEach { task ->
                        Text(
                            text = "• $task",
                            color = primaryTextColor,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    tasks.remove(task)
                                    saveTasksToStorage()
                                }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(500)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { showAddTaskDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة مهمة")
                }

                Button(
                    onClick = onExitApp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF991B1B),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("خروج")
                }
            }
        }

        if (showAddTaskDialog) {
            var newTaskText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddTaskDialog = false },
                title = { Text("مهمة جديدة") },
                text = {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        placeholder = { Text("اكتب المهمة...") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newTaskText.isNotBlank()) {
                            tasks.add(newTaskText.trim())
                            saveTasksToStorage()
                        }
                        showAddTaskDialog = false
                    }) { Text("حفظ") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTaskDialog = false }) { Text("إلغاء") }
                }
            )
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun fetchRealWeather(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        var lat = 31.8359
        var lon = 47.1448

        if (hasPerm) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val task = fusedLocationClient.lastLocation
            var attempts = 0
            while (!task.isComplete && attempts < 10) {
                delay(200)
                attempts++
            }
            if (task.isSuccessful && task.result != null) {
                lat = task.result.latitude
                lon = task.result.longitude
            }
        }

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
        val response = URL(url).readText()
        val current = JSONObject(response).getJSONObject("current_weather")
        val temp = current.getDouble("temperature").roundToInt()
        val code = current.getInt("weathercode")

        val condition = when (code) {
            0 -> "☀️ صافي"
            in 1..3 -> "⛅ غائم جزئياً"
            in 45..48 -> "🌫️ ضباب"
            in 51..67 -> "🌧️ ممطر"
            in 71..77 -> "❄️ ثلوج"
            in 80..99 -> "⛈️ عواصف رعدية"
            else -> "🌤️ معتدل"
        }
        "$condition  $temp°C"
    } catch (e: Exception) {
        "🌡️ الطقس غير متاح حالياً"
    }
}
