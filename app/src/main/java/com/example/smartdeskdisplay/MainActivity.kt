package com.example.smartdeskdisplay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.*
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
import kotlin.math.*
import kotlin.random.Random

val Context.dataStore by preferencesDataStore(name = "desk_display_v2_prefs")
val TASKS_KEY = stringSetPreferencesKey("local_tasks")
val THEME_INDEX_KEY = intPreferencesKey("selected_theme_index")
val CHIME_ENABLED_KEY = booleanPreferencesKey("hourly_chime_enabled")
val AUTO_BRIGHTNESS_KEY = booleanPreferencesKey("auto_brightness_enabled")
val EVENT_TITLE_KEY = stringPreferencesKey("countdown_event_title")
val EVENT_TARGET_MILLIS_KEY = longPreferencesKey("countdown_event_millis")

data class DeskTheme(
    val id: Int,
    val name: String,
    val subtitle: String,
    val accent: Color,
    val bgAlpha: Float
)

val ALL_THEMES = listOf(
    DeskTheme(0, "1. الكلاسيكي المتوازن", "Classic Desk", Color(0xFF81D4FA), 0.35f),
    DeskTheme(1, "2. ساعة الأميال العصرية", "Modern Gauge Clock", Color(0xFF00E5FF), 0.25f),
    DeskTheme(2, "3. تقسيم أبل الاحتياطي", "Apple StandBy Split", Color(0xFFFF9100), 0.20f),
    DeskTheme(3, "4. الساعة القلابة", "Retro Flip Clock", Color(0xFFE0E0E0), 0.18f),
    DeskTheme(4, "5. المينيمال الهادئ", "Nest Ambient Minimal", Color(0xFFA5D6A7), 0.45f),
    DeskTheme(5, "6. لوحة القيادة التقنية", "Sci-Fi HUD", Color(0xFFFFAB00), 0.20f),
    DeskTheme(6, "7. المسار الفلكي", "Orbit 24h Ring", Color(0xFFCE93D8), 0.25f),
    DeskTheme(7, "8. البطاقات الزجاجية", "Bento Glass Grid", Color(0xFF4DD0E1), 0.35f),
    DeskTheme(8, "9. شريط السينما العريض", "Cinematic Typographic", Color(0xFFFFD54F), 0.30f),
    DeskTheme(9, "10. عداد الطيران الدائري", "Aviation Cockpit", Color(0xFF69F0AE), 0.20f),
    DeskTheme(10, "11. النبض الموجي", "Neon Pulse Wave", Color(0xFFFF4081), 0.22f),
    DeskTheme(11, "12. ساعة الحائط الاسكندنافية", "Nordic Minimal Analog", Color(0xFFECEFF1), 0.30f),
    DeskTheme(12, "13. الماتريكس الرقمي", "Cyber Terminal", Color(0xFF00E676), 0.15f),
    DeskTheme(13, "14. الذهبي الفاخر", "Luxury Gold Chrono", Color(0xFFFFD700), 0.22f),
    DeskTheme(14, "15. لوحة التركيز العميق", "Zen Focus Board", Color(0xFF80CBC4), 0.28f)
)

data class DisplayState(
    val hour12: String,
    val hour24Int: Int,
    val minuteInt: Int,
    val secondInt: Int,
    val minuteStr: String,
    val amPm: String,
    val dateStr: String,
    val weatherStr: String,
    val tempOnly: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val tasks: List<String>,
    val eventTitle: String,
    val eventCountdown: String,
    val isNightMode: Boolean,
    val dotAlpha: Float,
    val onTaskClick: (String) -> Unit
)

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

    // Time & Date States
    var hour12 by remember { mutableStateOf("11") }
    var hour24Int by remember { mutableIntStateOf(11) }
    var minuteInt by remember { mutableIntStateOf(0) }
    var secondInt by remember { mutableIntStateOf(0) }
    var minuteStr by remember { mutableStateOf("00") }
    var amPm by remember { mutableStateOf("AM") }
    var dateStr by remember { mutableStateOf("") }
    var isNightMode by remember { mutableStateOf(false) }

    // Weather
    var weatherStr by remember { mutableStateOf("جاري جلب الطقس...") }
    var tempOnly by remember { mutableStateOf("--°") }

    // Settings & Data
    var selectedThemeIndex by remember { mutableIntStateOf(0) }
    var chimeEnabled by remember { mutableStateOf(true) }
    var autoBrightnessEnabled by remember { mutableStateOf(true) }
    var ambientLux by remember { mutableFloatStateOf(100f) }

    val tasks = remember { mutableStateListOf<String>() }
    var eventTitle by remember { mutableStateOf("") }
    var eventTargetMillis by remember { mutableLongStateOf(0L) }
    var eventCountdown by remember { mutableStateOf("") }

    // Battery
    var isCharging by remember { mutableStateOf(true) }
    var batteryLevel by remember { mutableIntStateOf(100) }

    // Wallpapers
    var wallpaperUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentBgIndex by remember { mutableIntStateOf(0) }

    // Burn-in Offset
    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = tween(2500),
        label = "BurnInOffset"
    )

    // Dialogs & Controls
    var showControls by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var lastChimingHour by remember { mutableIntStateOf(-1) }

    // Load saved DataStore preferences
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val savedTasks = prefs[TASKS_KEY]
        if (savedTasks != null) tasks.addAll(savedTasks)
        else tasks.addAll(listOf("مراجعة الخطة اليومية", "شرب الماء بانتظام"))

        selectedThemeIndex = (prefs[THEME_INDEX_KEY] ?: 0).coerceIn(0, ALL_THEMES.lastIndex)
        chimeEnabled = prefs[CHIME_ENABLED_KEY] ?: true
        autoBrightnessEnabled = prefs[AUTO_BRIGHTNESS_KEY] ?: true
        eventTitle = prefs[EVENT_TITLE_KEY] ?: ""
        eventTargetMillis = prefs[EVENT_TARGET_MILLIS_KEY] ?: 0L
    }

    fun saveTasks() {
        scope.launch { context.dataStore.edit { it[TASKS_KEY] = tasks.toSet() } }
    }

    // Light Sensor for Smart Auto-Brightness
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.firstOrNull()?.let { ambientLux = it }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Battery Monitor
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) batteryLevel = (level * 100) / scale
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Main Clock, Brightness, Countdown & Hourly Chime Loop
    LaunchedEffect(chimeEnabled, autoBrightnessEnabled, ambientLux, eventTargetMillis, eventTitle) {
        while (true) {
            val cal = Calendar.getInstance()
            val h24 = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val sec = cal.get(Calendar.SECOND)

            hour24Int = h24
            minuteInt = min
            secondInt = sec
            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
            hour12 = String.format(Locale.US, "%02d", h12)
            minuteStr = String.format(Locale.US, "%02d", min)
            amPm = if (h24 < 12) "AM" else "PM"
            dateStr = SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(cal.time)

            // Night mode (11 PM to 6 AM)
            isNightMode = (h24 >= 23 || h24 < 6)

            // Smart Brightness (Time + Ambient Light Sensor)
            val activity = context as? Activity
            activity?.window?.let { win ->
                val params = win.attributes
                params.screenBrightness = when {
                    isNightMode -> 0.03f
                    !autoBrightnessEnabled -> 0.75f
                    ambientLux < 15f -> 0.15f
                    ambientLux < 100f -> 0.45f
                    ambientLux < 400f -> 0.75f
                    else -> 0.95f
                }
                win.attributes = params
            }

            // Hourly Chime (9:00 AM to 10:00 PM inclusive, top of the hour)
            if (chimeEnabled && h24 in 9..22 && min == 0 && sec == 0 && lastChimingHour != h24) {
                lastChimingHour = h24
                scope.launch(Dispatchers.Default) { playSoftCrystalChime() }
            }

            // Update Event Countdown
            if (eventTitle.isNotBlank() && eventTargetMillis > System.currentTimeMillis()) {
                val diff = eventTargetMillis - System.currentTimeMillis()
                val days = diff / (1000 * 60 * 60 * 24)
                val hours = (diff / (1000 * 60 * 60)) % 24
                val mins = (diff / (1000 * 60)) % 60
                eventCountdown = when {
                    days > 0 -> "باقي $days يوم و $hours ساعة"
                    hours > 0 -> "باقي $hours ساعة و $mins دقيقة"
                    else -> "باقي $mins دقيقة"
                }
            } else if (eventTitle.isNotBlank() && eventTargetMillis > 0L) {
                eventCountdown = "حان الموعد!"
            } else {
                eventCountdown = ""
            }

            delay(1000L)
        }
    }

    // Burn-in Protection Offset every 3 minutes
    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000L)
            targetOffset = Offset(
                Random.nextInt(-32, 32).toFloat(),
                Random.nextInt(-22, 22).toFloat()
            )
        }
    }

    // Wallpaper Rotation every 10 minutes
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

    // Weather Update every 30 minutes
    LaunchedEffect(permissionsGranted) {
        while (true) {
            val (full, temp) = fetchRealWeatherDetailed(context)
            weatherStr = full
            tempOnly = temp
            delay(1_800_000L)
        }
    }

    // Auto-hide controls after 5 seconds (unless a dialog is open)
    LaunchedEffect(showControls, showAddTaskDialog, showThemePickerDialog, showEventDialog) {
        if (showControls && !showAddTaskDialog && !showThemePickerDialog && !showEventDialog) {
            delay(5000L)
            showControls = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "DotAlpha"
    )

    val currentTheme = ALL_THEMES[selectedThemeIndex]
    val displayState = DisplayState(
        hour12 = hour12,
        hour24Int = hour24Int,
        minuteInt = minuteInt,
        secondInt = secondInt,
        minuteStr = minuteStr,
        amPm = amPm,
        dateStr = dateStr,
        weatherStr = weatherStr,
        tempOnly = tempOnly,
        batteryLevel = batteryLevel,
        isCharging = isCharging,
        tasks = tasks,
        eventTitle = eventTitle,
        eventCountdown = eventCountdown,
        isNightMode = isNightMode,
        dotAlpha = dotAlpha,
        onTaskClick = { t ->
            tasks.remove(t)
            saveTasks()
        }
    )

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
        // Background Wallpapers (Disabled in Night Mode for pure OLED black)
        if (!isNightMode && wallpaperUris.isNotEmpty()) {
            Crossfade(
                targetState = wallpaperUris[currentBgIndex % wallpaperUris.size],
                animationSpec = tween(2500),
                label = "WallpaperFade"
            ) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = currentTheme.bgAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Theme Content with Burn-In Offset
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
        ) {
            ThemeRenderer(theme = currentTheme, state = displayState)
        }

        // Hidden Controls Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xE6111827), RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Theme Switcher Button
                ControlPillButton(
                    icon = Icons.Default.Palette,
                    label = "التصميم (${selectedThemeIndex + 1}/15)",
                    containerColor = Color(0xFF1E3A8A),
                    onClick = { showThemePickerDialog = true }
                )

                // Add Task Button
                ControlPillButton(
                    icon = Icons.Default.Add,
                    label = "مهمة",
                    containerColor = Color(0xFF1F2937),
                    onClick = { showAddTaskDialog = true }
                )

                // Countdown Event Button
                ControlPillButton(
                    icon = Icons.Default.Event,
                    label = "حدث قادم",
                    containerColor = Color(0xFF1F2937),
                    onClick = { showEventDialog = true }
                )

                // Next Wallpaper Button
                if (wallpaperUris.size > 1) {
                    IconButton(
                        onClick = { currentBgIndex = (currentBgIndex + 1) % wallpaperUris.size },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = "تغيير الخلفية", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Hourly Chime Toggle
                IconButton(
                    onClick = {
                        chimeEnabled = !chimeEnabled
                        scope.launch { context.dataStore.edit { it[CHIME_ENABLED_KEY] = chimeEnabled } }
                        if (chimeEnabled) {
                            scope.launch(Dispatchers.Default) { playSoftCrystalChime() }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (chimeEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "صوت الساعة",
                        tint = if (chimeEnabled) Color(0xFF38BDF8) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Auto Brightness Toggle
                IconButton(
                    onClick = {
                        autoBrightnessEnabled = !autoBrightnessEnabled
                        scope.launch { context.dataStore.edit { it[AUTO_BRIGHTNESS_KEY] = autoBrightnessEnabled } }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (autoBrightnessEnabled) Icons.Default.BrightnessAuto else Icons.Default.BrightnessMedium,
                        contentDescription = "سطوع تلقائي",
                        tint = if (autoBrightnessEnabled) Color(0xFFFACC15) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Exit App Button
                ControlPillButton(
                    icon = Icons.Default.Close,
                    label = "خروج",
                    containerColor = Color(0xFF991B1B),
                    onClick = onExitApp
                )
            }
        }

        // Dialog 1: Theme Picker (15 Themes)
        if (showThemePickerDialog) {
            AlertDialog(
                onDismissRequest = { showThemePickerDialog = false },
                title = { Text("اختر تصميم الشاشة (15 ثيم)") },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(ALL_THEMES) { index, theme ->
                            val isSelected = index == selectedThemeIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) theme.accent.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable {
                                        selectedThemeIndex = index
                                        scope.launch { context.dataStore.edit { it[THEME_INDEX_KEY] = index } }
                                        showThemePickerDialog = false
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(theme.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(theme.subtitle, fontSize = 12.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(theme.accent, CircleShape)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemePickerDialog = false }) { Text("إغلاق") }
                }
            )
        }

        // Dialog 2: Add Task
        if (showAddTaskDialog) {
            var newTaskText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddTaskDialog = false },
                title = { Text("إضافة مهمة جديدة") },
                text = {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        placeholder = { Text("اكتب المهمة هنا...") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newTaskText.isNotBlank()) {
                            tasks.add(newTaskText.trim())
                            saveTasks()
                        }
                        showAddTaskDialog = false
                    }) { Text("حفظ") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTaskDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // Dialog 3: Add/Edit Upcoming Event Countdown
        if (showEventDialog) {
            var titleInput by remember { mutableStateOf(eventTitle) }
            var daysInput by remember { mutableStateOf("3") }
            var hoursInput by remember { mutableStateOf("0") }
            AlertDialog(
                onDismissRequest = { showEventDialog = false },
                title = { Text("الحدث القادم (العد التنازلي)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("اسم المناسبة (مثلاً: سفر، اجتماع)") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = daysInput,
                                onValueChange = { daysInput = it.filter { c -> c.isDigit() } },
                                label = { Text("بعد كم يوم؟") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = hoursInput,
                                onValueChange = { hoursInput = it.filter { c -> c.isDigit() } },
                                label = { Text("وكم ساعة؟") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val d = daysInput.toLongOrNull() ?: 0L
                        val h = hoursInput.toLongOrNull() ?: 0L
                        val target = System.currentTimeMillis() + (d * 86400_000L) + (h * 3600_000L)
                        eventTitle = titleInput.trim()
                        eventTargetMillis = if (eventTitle.isNotEmpty()) target else 0L
                        scope.launch {
                            context.dataStore.edit {
                                it[EVENT_TITLE_KEY] = eventTitle
                                it[EVENT_TARGET_MILLIS_KEY] = eventTargetMillis
                            }
                        }
                        showEventDialog = false
                    }) { Text("تفعيل العداد") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        eventTitle = ""
                        eventTargetMillis = 0L
                        scope.launch {
                            context.dataStore.edit {
                                it[EVENT_TITLE_KEY] = ""
                                it[EVENT_TARGET_MILLIS_KEY] = 0L
                            }
                        }
                        showEventDialog = false
                    }) { Text("مسح الحدث") }
                }
            )
        }
    }
}

@Composable
fun ControlPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// =========================================================================
// 15 THEMES RENDERER
// =========================================================================
@Composable
fun ThemeRenderer(theme: DeskTheme, state: DisplayState) {
    val primaryColor = if (state.isNightMode) Color(0xFFB71C1C) else Color.White
    val secondaryColor = if (state.isNightMode) Color(0xFF7F0000) else Color(0xFFB0BEC5)
    val accent = if (state.isNightMode) Color(0xFF9A0007) else theme.accent
    val cardBg = if (state.isNightMode) Color(0xFF0A0000) else Color(0xFF10141D).copy(alpha = 0.78f)

    when (theme.id) {
        // 1. Classic Desk
        0 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.2f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.hour12}:${state.minuteStr}", fontSize = 104.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Box(Modifier.size(10.dp).alpha(state.dotAlpha).background(accent, CircleShape))
                            Spacer(Modifier.height(6.dp))
                            Text(state.amPm, fontSize = 20.sp, color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(state.dateStr, fontSize = 23.sp, color = secondaryColor)
                    Spacer(Modifier.height(14.dp))
                    Text(state.weatherStr, fontSize = 21.sp, color = if (state.isNightMode) secondaryColor else Color(0xFFFFD54F))
                    Spacer(Modifier.height(10.dp))
                    BatteryStatusRow(state, accent, secondaryColor)
                    EventBanner(state, accent, cardBg, primaryColor)
                }
                TasksCard(state, accent, primaryColor, secondaryColor, cardBg, Modifier.width(320.dp))
            }
        }

        // 2. Modern Gauge Clock (ساعة الأميال العصرية بعقارب + لوحة بطاقات جانبية)
        1 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AnalogGaugeClock(
                        hour = state.hour24Int % 12,
                        minute = state.minuteInt,
                        second = state.secondInt,
                        accent = accent,
                        primaryColor = primaryColor,
                        showNumbers = true,
                        modifier = Modifier.size(275.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1.15f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Date + Digital Time Header
                    Row(
                        modifier = Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.dateStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        Text("${state.hour12}:${state.minuteStr} ${state.amPm}", fontSize = 18.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                    // Weather & Battery Row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f).background(cardBg, RoundedCornerShape(14.dp)).padding(12.dp)) {
                            Text(state.weatherStr, fontSize = 16.sp, color = primaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(modifier = Modifier.background(cardBg, RoundedCornerShape(14.dp)).padding(12.dp)) {
                            Text("⚡ ${state.batteryLevel}%", fontSize = 16.sp, color = if (state.isCharging) Color(0xFF4ADE80) else Color(0xFFFF6D00), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (state.eventCountdown.isNotEmpty()) {
                        EventBanner(state, accent, cardBg, primaryColor)
                    }
                    TasksCard(state, accent, primaryColor, secondaryColor, cardBg, Modifier.fillMaxWidth(), maxItems = 3)
                }
            }
        }

        // 3. Apple StandBy Split
        2 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.amPm, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
                    Text(
                        "${state.hour12}:${state.minuteStr}",
                        fontSize = 112.sp,
                        fontWeight = FontWeight.Black,
                        color = accent,
                        letterSpacing = (-2).sp
                    )
                    Text(state.weatherStr, fontSize = 20.sp, color = primaryColor)
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(cardBg, RoundedCornerShape(24.dp)).padding(22.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(state.dateStr, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
                        EventBanner(state, accent, Color.White.copy(alpha = 0.05f), primaryColor)
                    }
                    Column {
                        state.tasks.take(4).forEach { t ->
                            Text("• $t", color = primaryColor, fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp).clickable { state.onTaskClick(t) })
                        }
                    }
                    BatteryStatusRow(state, accent, secondaryColor)
                }
            }
        }

        // 4. Retro Flip Clock
        3 -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlipCard(state.hour12, primaryColor, cardBg)
                    Text(":", fontSize = 80.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.alpha(state.dotAlpha))
                    FlipCard(state.minuteStr, primaryColor, cardBg)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.dateStr}  |  ${state.weatherStr}", color = primaryColor, fontSize = 16.sp)
                    Text(
                        text = if (state.eventCountdown.isNotEmpty()) "${state.eventTitle}: ${state.eventCountdown}"
                        else state.tasks.firstOrNull()?.let { "• $it" } ?: "لا توجد مهام",
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Nest Ambient Minimal
        4 -> {
            Box(modifier = Modifier.fillMaxSize().padding(36.dp)) {
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text("${state.hour12}:${state.minuteStr}", fontSize = 96.sp, fontWeight = FontWeight.Light, color = primaryColor)
                    Text("${state.dateStr}  •  ${state.weatherStr}", fontSize = 20.sp, color = accent)
                }
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).width(280.dp).background(cardBg.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(16.dp)
                ) {
                    if (state.eventCountdown.isNotEmpty()) {
                        Text("⏳ ${state.eventTitle}: ${state.eventCountdown}", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.tasks.take(4).forEach { t ->
                        Text("• $t", color = primaryColor, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp).clickable { state.onTaskClick(t) })
                    }
                }
            }
        }

        // 6. Sci-Fi HUD
        5 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1.1f).fillMaxHeight().border(2.dp, accent, RoundedCornerShape(topStart = 28.dp, bottomEnd = 28.dp)).background(cardBg, RoundedCornerShape(topStart = 28.dp, bottomEnd = 28.dp)).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SYS.TIME // ${state.amPm}", color = accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("${state.hour12}:${state.minuteStr}", fontSize = 94.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = primaryColor)
                        Text(state.dateStr, color = secondaryColor, fontSize = 18.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).background(cardBg, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text("PWR: ${state.batteryLevel}%", color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1.3f).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).background(cardBg, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(state.tempOnly, color = primaryColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                    EventBanner(state, accent, cardBg, primaryColor)
                    Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).background(cardBg, RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Column {
                            Text("ACTIVE_TASKS", color = accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(6.dp))
                            state.tasks.take(4).forEach { t ->
                                Text("> $t", color = primaryColor, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp).clickable { state.onTaskClick(t) })
                            }
                        }
                    }
                }
            }
        }

        // 7. Orbit 24h Ring (المسار الفلكي)
        6 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                    val dayProgress = ((state.hour24Int * 60 + state.minuteInt) / 1440f) * 360f
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = accent.copy(alpha = 0.2f), style = Stroke(width = 14.dp.toPx()))
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = dayProgress,
                            useCenter = false,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.hour12}:${state.minuteStr}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        Text(state.amPm, fontSize = 16.sp, color = accent)
                    }
                }
                Column(modifier = Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.dateStr, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                    Text(state.weatherStr, fontSize = 18.sp, color = accent)
                    EventBanner(state, accent, cardBg, primaryColor)
                    TasksCard(state, accent, primaryColor, secondaryColor, cardBg, Modifier.fillMaxWidth(), maxItems = 3)
                }
            }
        }

        // 8. Bento Glass Grid (البطاقات الزجاجية)
        7 -> {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.weight(1.2f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1.3f).fillMaxHeight().background(cardBg, RoundedCornerShape(22.dp)).padding(20.dp), contentAlignment = Alignment.CenterStart) {
                        Column {
                            Text("${state.hour12}:${state.minuteStr} ${state.amPm}", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                            Text(state.dateStr, fontSize = 18.sp, color = accent)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth().background(cardBg, RoundedCornerShape(22.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) {
                            Text(state.weatherStr, fontSize = 19.sp, color = primaryColor)
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().background(cardBg, RoundedCornerShape(22.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) {
                            BatteryStatusRow(state, accent, primaryColor)
                        }
                    }
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1.4f).fillMaxHeight().background(cardBg, RoundedCornerShape(22.dp)).padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            state.tasks.take(3).forEach { t ->
                                Text("• $t", color = primaryColor, fontSize = 15.sp, modifier = Modifier.weight(1f).clickable { state.onTaskClick(t) })
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().background(cardBg, RoundedCornerShape(22.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            if (state.eventCountdown.isNotEmpty()) "⏳ ${state.eventTitle}
${state.eventCountdown}" else "لا يوجد حدث قادم",
                            color = accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 9. Cinematic Typographic (شريط السينما العريض)
        8 -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.dateStr, fontSize = 22.sp, color = accent, letterSpacing = 2.sp)
                Text("${state.hour12}:${state.minuteStr}", fontSize = 132.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.weatherStr, fontSize = 18.sp, color = primaryColor)
                    Text("•", color = accent)
                    Text("البطارية ${state.batteryLevel}%", fontSize = 18.sp, color = secondaryColor)
                    if (state.eventCountdown.isNotEmpty()) {
                        Text("•", color = accent)
                        Text("${state.eventTitle}: ${state.eventCountdown}", fontSize = 18.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 10. Aviation Cockpit (عداد الطيران الدائري)
        9 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery Dial
                MiniDialGauge("BAT", "${state.batteryLevel}%", state.batteryLevel / 100f, accent, primaryColor)
                // Main Clock Gauge
                AnalogGaugeClock(
                    hour = state.hour24Int % 12,
                    minute = state.minuteInt,
                    second = state.secondInt,
                    accent = accent,
                    primaryColor = primaryColor,
                    showNumbers = true,
                    modifier = Modifier.size(255.dp)
                )
                // Tasks & Date Column
                Column(modifier = Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(state.dateStr, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(state.weatherStr, color = primaryColor, fontSize = 16.sp)
                    EventBanner(state, accent, cardBg, primaryColor)
                    TasksCard(state, accent, primaryColor, secondaryColor, cardBg, Modifier.fillMaxWidth(), maxItems = 3)
                }
            }
        }

        // 11. Neon Pulse Wave (النبض الموجي)
        10 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${state.hour12}:${state.minuteStr}", fontSize = 110.sp, fontWeight = FontWeight.Bold, color = accent)
                    Box(Modifier.width(240.dp).height(4.dp).background(accent, CircleShape))
                    Spacer(Modifier.height(12.dp))
                    Text("${state.dateStr}  |  ${state.weatherStr}", fontSize = 20.sp, color = primaryColor)
                    EventBanner(state, accent, cardBg, primaryColor)
                }
                TasksCard(state, accent, primaryColor, secondaryColor, cardBg, Modifier.width(310.dp))
            }
        }

        // 12. Nordic Minimal Analog (ساعة الحائط الاسكندنافية)
        11 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalogGaugeClock(
                    hour = state.hour24Int % 12,
                    minute = state.minuteInt,
                    second = state.secondInt,
                    accent = accent,
                    primaryColor = primaryColor,
                    showNumbers = false,
                    modifier = Modifier.size(265.dp)
                )
                Column(modifier = Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.dateStr, fontSize = 24.sp, fontWeight = FontWeight.Light, color = primaryColor)
                    Text(state.weatherStr, fontSize = 19.sp, color = secondaryColor)
                    EventBanner(state, accent, cardBg, primaryColor)
                    TasksCard(state, accent, primaryColor, secondaryColor, cardBg, Modifier.fillMaxWidth(), maxItems = 4)
                }
            }
        }

        // 13. Cyber Terminal (الماتريكس الرقمي)
        12 -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ROOT@DESK-HUB:~$ uptime --status=ACTIVE | BAT=${state.batteryLevel}%", color = accent, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("[ ${state.hour12}:${state.minuteStr}:${String.format(Locale.US, "%02d", state.secondInt)} ]", fontSize = 68.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = accent)
                        Text("DATE: ${state.dateStr}", fontFamily = FontFamily.Monospace, color = primaryColor, fontSize = 18.sp)
                        Text("WTHR: ${state.weatherStr}", fontFamily = FontFamily.Monospace, color = secondaryColor, fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.width(320.dp).border(1.dp, accent).padding(16.dp)) {
                        Text("// TODO_LIST", color = accent, fontFamily = FontFamily.Monospace)
                        state.tasks.take(5).forEach { t ->
                            Text("[*] $t", color = primaryColor, fontFamily = FontFamily.Monospace, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp).clickable { state.onTaskClick(t) })
                        }
                    }
                }
                Text(
                    if (state.eventCountdown.isNotEmpty()) "TARGET_EVENT >> ${state.eventTitle} [${state.eventCountdown}]" else "STATUS >> ALL SYSTEMS NOMINAL",
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                )
            }
        }

        // 14. Luxury Gold Chrono (الذهبي الفاخر)
        13 -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalogGaugeClock(
                    hour = state.hour24Int % 12,
                    minute = state.minuteInt,
                    second = state.secondInt,
                    accent = accent,
                    primaryColor = primaryColor,
                    showNumbers = true,
                    modifier = Modifier.size(265.dp)
                )
                Column(
                    modifier = Modifier.width(350.dp).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).background(cardBg, RoundedCornerShape(20.dp)).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(state.dateStr, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = accent)
                    Text(state.weatherStr, fontSize = 18.sp, color = primaryColor)
                    HorizontalDivider(color = accent.copy(alpha = 0.3f))
                    EventBanner(state, accent, Color.Transparent, primaryColor)
                    state.tasks.take(4).forEach { t ->
                        Text("◆ $t", color = primaryColor, fontSize = 16.sp, modifier = Modifier.clickable { state.onTaskClick(t) })
                    }
                }
            }
        }

        // 15. Zen Focus Board (لوحة التركيز العميق)
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(36.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(state.dateStr, color = secondaryColor, fontSize = 18.sp)
                    Text("${state.weatherStr}  •  ${state.batteryLevel}%", color = accent, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.hour12}:${state.minuteStr}", fontSize = 102.sp, fontWeight = FontWeight.SemiBold, color = primaryColor)
                    if (state.eventCountdown.isNotEmpty()) {
                        Text("⏳ ${state.eventTitle} — ${state.eventCountdown}", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.tasks.take(3).forEach { t ->
                        Surface(
                            onClick = { state.onTaskClick(t) },
                            shape = RoundedCornerShape(50),
                            color = cardBg,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                        ) {
                            Text(t, color = primaryColor, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// REUSABLE COMPONENTS (Analog Gauge Clock, Flip Card, Tasks, Event Banner)
// =========================================================================
@Composable
fun AnalogGaugeClock(
    hour: Int,
    minute: Int,
    second: Int,
    accent: Color,
    primaryColor: Color,
    showNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Outer Glow Ring
            drawCircle(
                color = accent.copy(alpha = 0.25f),
                radius = radius,
                style = Stroke(width = 6.dp.toPx())
            )
            drawCircle(
                color = accent,
                radius = radius - 4.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )

            // 60 Precision Gauge Ticks
            for (i in 0 until 60) {
                val angle = Math.toRadians((i * 6 - 90).toDouble())
                val isMajor = i % 5 == 0
                val tickLength = if (isMajor) 14.dp.toPx() else 7.dp.toPx()
                val strokeW = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx()
                val startR = radius - 8.dp.toPx()
                val endR = startR - tickLength

                drawLine(
                    color = if (isMajor) accent else accent.copy(alpha = 0.55f),
                    start = Offset(center.x + cos(angle).toFloat() * startR, center.y + sin(angle).toFloat() * startR),
                    end = Offset(center.x + cos(angle).toFloat() * endR, center.y + sin(angle).toFloat() * endR),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            // Hour Hand
            val hourAngle = (hour + minute / 60f) * 30f
            rotate(hourAngle, pivot = center) {
                drawLine(
                    color = primaryColor,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.48f),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Minute Hand
            val minuteAngle = (minute + second / 60f) * 6f
            rotate(minuteAngle, pivot = center) {
                drawLine(
                    color = primaryColor,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.70f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Second Hand (Orange/Accent Sweep)
            val secondAngle = second * 6f
            rotate(secondAngle, pivot = center) {
                drawLine(
                    color = Color(0xFFFF5722),
                    start = Offset(center.x, center.y + radius * 0.15f),
                    end = Offset(center.x, center.y - radius * 0.78f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center Cap
            drawCircle(color = Color(0xFFFF5722), radius = 6.dp.toPx(), center = center)
        }

        // Optional Hour Numbers (1..12)
        if (showNumbers) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val r = (maxWidth.value / 2f) - 38f
                for (num in 1..12) {
                    val angleRad = Math.toRadians((num * 30 - 90).toDouble())
                    val x = (cos(angleRad) * r).dp
                    val y = (sin(angleRad) * r).dp
                    Text(
                        text = num.toString(),
                        color = primaryColor.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center).offset(x = x, y = y)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniDialGauge(label: String, valueText: String, progress: Float, accent: Color, primaryColor: Color) {
    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(accent.copy(alpha = 0.2f), 135f, 270f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawArc(accent, 135f, 270f * progress.coerceIn(0f, 1f), false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valueText, color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = accent, fontSize = 12.sp)
        }
    }
}

@Composable
fun FlipCard(value: String, primaryColor: Color, cardBg: Color) {
    Box(
        modifier = Modifier
            .size(width = 190.dp, height = 210.dp)
            .background(cardBg, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(value, fontSize = 124.sp, fontWeight = FontWeight.Bold, color = primaryColor)
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.Black.copy(alpha = 0.6f)))
    }
}

@Composable
fun TasksCard(
    state: DisplayState,
    accent: Color,
    primaryColor: Color,
    secondaryColor: Color,
    cardBg: Color,
    modifier: Modifier = Modifier,
    maxItems: Int = 5
) {
    Column(
        modifier = modifier
            .background(cardBg, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("المهام (${state.tasks.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(8.dp))
        if (state.tasks.isEmpty()) {
            Text("لا توجد مهام حالياً", color = secondaryColor, fontSize = 14.sp)
        } else {
            state.tasks.take(maxItems).forEach { task ->
                Text(
                    text = "• $task",
                    color = primaryColor,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { state.onTaskClick(task) }
                )
            }
        }
    }
}

@Composable
fun EventBanner(state: DisplayState, accent: Color, cardBg: Color, primaryColor: Color) {
    if (state.eventCountdown.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⏳ ${state.eventTitle}", color = primaryColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(state.eventCountdown, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BatteryStatusRow(state: DisplayState, accent: Color, secondaryColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (state.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
            contentDescription = null,
            tint = if (state.isCharging) accent else Color(0xFFFF6D00),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (state.isCharging) "متصل بالشاحن (${state.batteryLevel}%)" else "تنبيه: الشاحن مفصول (${state.batteryLevel}%)",
            fontSize = 14.sp,
            color = if (state.isCharging) secondaryColor else Color(0xFFFF6D00)
        )
    }
}

// =========================================================================
// SYNTHESIZED CRYSTAL HOURLY CHIME (No external audio file needed)
// =========================================================================
fun playSoftCrystalChime() {
    try {
        val sampleRate = 22050
        val durationSec = 1.2
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val freq1 = 880.0 // A5 soft bell harmonic
        val freq2 = 1318.5 // E6 crystal overtone

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-3.5 * t) * min(1.0, t * 40.0)
            val wave = (0.6 * sin(2.0 * PI * freq1 * t) + 0.4 * sin(2.0 * PI * freq2 * t)) * envelope
            samples[i] = (wave * Short.MAX_VALUE * 0.35).toInt().toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()
        Thread.sleep(1300)
        audioTrack.release()
    } catch (_: Exception) {
    }
}

@SuppressLint("MissingPermission")
suspend fun fetchRealWeatherDetailed(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
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
        Pair("$condition  $temp°C", "$temp°C")
    } catch (e: Exception) {
        Pair("🌡️ الطقس غير متاح", "--°")
    }
}
