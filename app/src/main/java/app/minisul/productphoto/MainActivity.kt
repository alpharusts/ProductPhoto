package app.minisul.productphoto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { MiniSulTheme { App() } }
    }
}

@Composable
fun App(vm: PhotoViewModel = viewModel()) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) vm.setPhotos(uris)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.run() else vm.message = "Без доступа к памяти фото не сохранить. Разрешите доступ и попробуйте снова."
    }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val start = {
        val needPermission = Build.VERSION.SDK_INT < 29 &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.run()
    }
    val share = {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(vm.saved))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить фото"))
    }

    Scaffold(
        containerColor = Surface,
        bottomBar = { BottomBar(vm, onStart = start) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header() }
            item(span = { GridItemSpan(maxLineSpan) }) { PresetPicker(vm) }
            item(span = { GridItemSpan(maxLineSpan) }) { Options(vm) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PickButton(count = vm.items.size, enabled = !vm.running, onClick = pickPhotos)
            }
            items(vm.items) { PhotoTile(it, vm.preset) }
            vm.message?.let { text ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ResultCard(text, canShare = vm.saved.isNotEmpty(), onShare = share, onNew = pickPhotos)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Footer() }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Фото товара", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink, letterSpacing = (-0.5).sp)
            Box(
                Modifier
                    .padding(start = 4.dp, bottom = 9.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Белый фон, нужный размер и формат для маркетплейсов. Сразу пачкой и прямо на телефоне.",
            fontSize = 15.sp, color = InkMuted, lineHeight = 21.sp,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkMuted, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun PresetPicker(vm: PhotoViewModel) {
    Column {
        Label("ПЛОЩАДКА")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESETS.forEach { p ->
                val selected = p.id == vm.preset.id
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Indigo else Color.White)
                        .border(1.dp, if (selected) Indigo else Line, RoundedCornerShape(14.dp))
                        .clickable(enabled = !vm.running) { vm.preset = p }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(p.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (selected) Color.White else Ink)
                    Text(
                        "${p.ratio}, ${p.width}×${p.height}",
                        fontSize = 12.sp,
                        color = if (selected) IndigoTint else InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Options(vm: PhotoViewModel) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Убрать фон", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                    Text(
                        "Товар вырезается автоматически и ставится на белый фон",
                        fontSize = 13.sp, color = InkMuted, lineHeight = 18.sp,
                    )
                }
                Switch(
                    checked = vm.removeBackground,
                    onCheckedChange = { vm.removeBackground = it },
                    enabled = !vm.running,
                    colors = SwitchDefaults.colors(checkedTrackColor = Indigo),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Поля вокруг товара: ${(vm.margin * 100).roundToInt()}%",
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink,
            )
            Slider(
                value = vm.margin,
                onValueChange = { vm.margin = it },
                valueRange = 0f..0.2f,
                steps = 19,
                enabled = !vm.running,
                colors = SliderDefaults.colors(thumbColor = Indigo, activeTrackColor = Indigo),
            )
            OutlinedTextField(
                value = vm.sku,
                onValueChange = { vm.sku = it.take(40) },
                enabled = !vm.running,
                singleLine = true,
                label = { Text("Артикул для имени файлов (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PickButton(count: Int, enabled: Boolean, onClick: () -> Unit) {
    if (count == 0) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Выбрать фото", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("Выбрано фото: $count", fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp)) {
                Text("Выбрать другие")
            }
        }
    }
}

@Composable
private fun PhotoTile(item: PhotoItem, preset: Preset) {
    val image = item.result ?: item.thumb
    Box(
        Modifier
            .aspectRatio(preset.width.toFloat() / preset.height)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = if (item.result != null) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (item.status) {
            Status.Working -> Box(
                Modifier.fillMaxSize().background(Color(0x99FFFFFF)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Indigo, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }
            Status.Done -> Badge("✓", Accent, Ink)
            Status.Error -> Badge("!", Danger, Color.White)
            Status.Waiting -> {}
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = fg, fontWeight = FontWeight.Black, fontSize = 13.sp) }
    }
}

@Composable
private fun ResultCard(text: String, canShare: Boolean, onShare: () -> Unit, onNew: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = IndigoTint, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Готово", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 14.sp, color = Ink, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canShare) {
                    Button(
                        onClick = onShare,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    ) { Text("Отправить") }
                }
                OutlinedButton(onClick = onNew, shape = RoundedCornerShape(12.dp)) { Text("Новые фото") }
            }
        }
    }
}

@Composable
private fun BottomBar(vm: PhotoViewModel, onStart: () -> Unit) {
    if (vm.items.isEmpty()) return
    Surface(color = Color.White, shadowElevation = 12.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (vm.running) {
                Text("Обработано ${vm.done} из ${vm.items.size}", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { vm.done.toFloat() / vm.items.size },
                    color = Indigo,
                    trackColor = IndigoTint,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
            } else {
                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(
                        "Обработать для ${vm.preset.title} (${vm.items.size})",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer() {
    Text(
        "MiniSul — маленькие решения для настоящих проблем.\nБесплатно и без рекламы.",
        fontSize = 12.sp, color = InkMuted, textAlign = TextAlign.Center, lineHeight = 17.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
    )
}
