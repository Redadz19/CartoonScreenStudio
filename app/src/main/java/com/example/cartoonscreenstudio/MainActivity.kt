package com.example.cartoonscreenstudio
import androidx.activity.compose.setContent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.ContextCompat

private val Ink = Color(0xFF17142B)
private val Purple = Color(0xFF7657FF)
private val Pink = Color(0xFFFF5FA2)
private val Cyan = Color(0xFF43D9E8)
private val SurfaceDark = Color(0xFF24203D)

class MainActivity : ComponentActivity() {
    private var selectedVideo by mutableStateOf<Uri?>(null)
    private var recording by mutableStateOf(false)

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val service = Intent(this, ScreenRecorderService::class.java).apply {
                    action = ScreenRecorderService.ACTION_START
                    putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenRecorderService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, service)
                recording = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    background = Ink,
                    surface = SurfaceDark,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                StudioScreen(
                    recording = recording,
                    selectedVideo = selectedVideo,
                    onRecord = { requestRecording() },
                    onStop = {
                        startService(Intent(this, ScreenRecorderService::class.java).apply {
                            action = ScreenRecorderService.ACTION_STOP
                        })
                        recording = false
                    },
                    onPickVideo = { selectedVideo = it }
                )
            }
        }
    }

    private fun requestRecording() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
}

@Composable
private fun StudioScreen(
    recording: Boolean,
    selectedVideo: Uri?,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onPickVideo: (Uri) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var cartoonFrame by remember { mutableStateOf<Bitmap?>(null) }
    var processing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) onPickVideo(it)
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark, contentColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.FiberManualRecord, null) }, label = { Text("تسجيل") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("كرتون") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("مكتبتي") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مرحباً بك في", color = Color.LightGray, fontSize = 14.sp)
                        Text("Cartoon Studio", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(Modifier.size(48.dp).background(
                        Brush.linearGradient(listOf(Purple, Pink)), CircleShape
                    ), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                    }
                }
            }
            when (tab) {
                0 -> {
                    item {
                        Box(
                            Modifier.fillMaxWidth().height(190.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFF5535C8), Color(0xFFB83F9A), Color(0xFF247CBA))), RoundedCornerShape(28.dp))
                                .padding(22.dp)
                        ) {
                            Column(Modifier.align(Alignment.CenterStart)) {
                                Text("حوّل لحظاتك إلى إبداع", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("سجّل شاشتك وابدأ صناعة فيديوهاتك", color = Color.White.copy(alpha = .85f))
                                Spacer(Modifier.height(16.dp))
                                Text("HD  •  MP4  •  صوت", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    item {
                        SectionTitle("مسجل الشاشة")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("الجودة", "حتى 1080p", Icons.Default.HighQuality, Modifier.weight(1f))
                            StatCard("الصيغة", "MP4", Icons.Default.Movie, Modifier.weight(1f))
                        }
                    }
                    item {
                        Button(
                            onClick = { if (recording) onStop() else onRecord() },
                            modifier = Modifier.fillMaxWidth().height(62.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (recording) Pink else Purple),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (recording) "إيقاف التسجيل" else "بدء تسجيل الشاشة", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        if (recording) Text("التسجيل جارٍ الآن…", color = Cyan, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
                1 -> {
                    item {
                        SectionTitle("استوديو الكرتون")
                        Text("اختر فيديو من جهازك لبدء العمل عليه.", color = Color.LightGray)
                        Button(onClick = { videoPicker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.VideoLibrary, null)
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار فيديو")
                        }
                        if (selectedVideo != null) {
                            Text("تم اختيار فيديو بنجاح", color = Cyan, fontWeight = FontWeight.Bold)
                            Button(
                                enabled = !processing,
                                onClick = {
                                    scope.launch {
                                        processing = true
                                        try {
                                            cartoonFrame = withContext(Dispatchers.IO) {
                                                CartoonProcessor.createPreview(context, selectedVideo)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "حدث خطأ أثناء المعالجة", Toast.LENGTH_LONG).show()
                                        } finally {
                                            processing = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (processing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (processing) "جارٍ إنشاء المعاينة..." else "إنشاء معاينة كرتونية")
                            }
                        }
                        if (selectedVideo != null) {
                            Button(
                                enabled = !exporting && !processing,
                                onClick = {
                                    scope.launch {
                                        exporting = true
                                        exportProgress = 0
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                CartoonVideoExporter.export(context, selectedVideo) { progress ->
                                                    exportProgress = progress
                                                }
                                            }
                                            Toast.makeText(context, "تم تصدير فيديو كرتوني (${result.frames} إطار) إلى مجلد Movies", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "فشل تصدير الفيديو", Toast.LENGTH_LONG).show()
                                        } finally {
                                            exporting = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Pink)
                            ) {
                                Icon(Icons.Default.MovieFilter, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (exporting) "جارٍ تصدير MP4: $exportProgress%" else "تحويل الفيديو كاملاً إلى MP4")
                            }
                            if (exporting) {
                                LinearProgressIndicator(progress = { exportProgress / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("قد تستغرق المعالجة وقتاً حسب طول الفيديو وقوة الهاتف. الناتج صامت حالياً.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        cartoonFrame?.let { frame ->
                            Text("معاينة التأثير الكرتوني", color = Color.White, fontWeight = FontWeight.Bold)
                            Image(
                                bitmap = frame.asImageBitmap(),
                                contentDescription = "معاينة كرتونية",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                                    .background(SurfaceDark, RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Fit
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { CartoonProcessor.savePreview(context, frame) }
                                            Toast.makeText(context, "تم حفظ المعاينة في الصور", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "تعذر الحفظ", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SaveAlt, null)
                                Spacer(Modifier.width(8.dp))
                                Text("حفظ صورة المعاينة")
                            }
                        }
                    }
                    item {
                        SectionTitle("أنماط التحويل")
                        listOf(
                            "كرتون كلاسيكي" to "ألوان مسطحة وحدود واضحة",
                            "أنمي" to "مظهر رسوم يابانية",
                            "Comic" to "تأثير القصص المصورة",
                            "ألوان زاهية" to "ألوان قوية ومشبعة"
                        ).forEachIndexed { index, pair ->
                            EffectRow(pair.first, pair.second, listOf(Purple, Pink, Cyan, Color(0xFFFFB547))[index])
                        }
                        Text(
                            "المعاينة الحالية تطبق تأثيراً كرتونياً محلياً على إطار من الفيديو دون إنترنت. تحويل الفيديو كاملاً إلى MP4 ما زال خطوة التطوير التالية.",
                            color = Color(0xFFFFD18A), fontSize = 12.sp
                        )
                    }
                }
                else -> {
                    item {
                        SectionTitle("مكتبتي")
                        Box(Modifier.fillMaxWidth().height(160.dp).background(SurfaceDark, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.VideoLibrary, null, tint = Cyan, modifier = Modifier.size(38.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("ستظهر تسجيلاتك هنا", color = Color.White)
                                Text("تجد الملفات في مجلد Movies/CartoonScreenStudio", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.background(SurfaceDark, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Icon(icon, null, tint = Cyan)
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EffectRow(name: String, description: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .background(SurfaceDark, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).background(accent.copy(alpha = .2f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, null, tint = accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold)
            Text(description, color = Color.LightGray, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}
