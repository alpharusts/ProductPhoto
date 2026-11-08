package app.minisul.productphoto

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Status { Waiting, Working, Done, Error }

class PhotoItem(val uri: Uri) {
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var result by mutableStateOf<ImageBitmap?>(null)
    var status by mutableStateOf(Status.Waiting)
}

class PhotoViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = Processor(app)

    val items = mutableStateListOf<PhotoItem>()
    val saved = mutableStateListOf<Uri>()
    var preset by mutableStateOf(PRESETS[0])
    var removeBackground by mutableStateOf(true)
    var margin by mutableFloatStateOf(0.08f)
    var sku by mutableStateOf("")
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        // Заранее просим Google Play скачать модель вырезания фона
        viewModelScope.launch { processor.prepareSegmenter() }
    }

    fun setPhotos(uris: List<Uri>) {
        if (running) return
        items.clear()
        saved.clear()
        message = null
        done = 0
        uris.forEach { items.add(PhotoItem(it)) }
        viewModelScope.launch {
            items.toList().forEach { item ->
                item.thumb = processor.thumbnail(item.uri)?.asImageBitmap()
            }
        }
    }

    fun run() {
        if (running || items.isEmpty()) return
        running = true
        done = 0
        saved.clear()
        message = null
        val chosen = preset
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val base = sku.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_')
        viewModelScope.launch {
            var errors = 0
            var keptBackground = 0
            items.toList().forEachIndexed { index, item ->
                item.status = Status.Working
                try {
                    val out = processor.process(item.uri, chosen, removeBackground, margin)
                    if (removeBackground && !out.backgroundRemoved) keptBackground++
                    val name = if (base.isNotEmpty()) "${base}_${index + 1}.jpg"
                    else "${chosen.folder.lowercase()}_${stamp}_${index + 1}.jpg"
                    saved.add(processor.save(out.bitmap, name, chosen.folder))
                    item.result = processor.preview(out.bitmap).asImageBitmap()
                    out.bitmap.recycle()
                    item.status = Status.Done
                } catch (e: Exception) {
                    item.status = Status.Error
                    errors++
                }
                done = index + 1
            }
            running = false
            message = buildString {
                append("Сохранено ${saved.size} из ${items.size} в Галерею → Pictures/MiniSul/${chosen.folder}.")
                if (keptBackground > 0) {
                    append(" На $keptBackground фото фон не удалось убрать — товар просто размещён на белом поле. ")
                    append("Если только что установили приложение, Google Play может ещё скачивать модель: попробуйте через пару минут.")
                }
                if (errors > 0) append(" Не получилось обработать: $errors.")
            }
        }
    }
}
