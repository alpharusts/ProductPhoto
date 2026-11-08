package app.minisul.productphoto

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ProcessResult(val bitmap: Bitmap, val backgroundRemoved: Boolean)

/**
 * Вся обработка — на телефоне: вырезание товара (ML Kit), белый фон,
 * центровка с полями, нужный размер площадки и сохранение в Галерею.
 */
class Processor(private val context: Context) {

    private val resolver get() = context.contentResolver

    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
    }

    /** Просим Google Play скачать модель вырезания фона, если её ещё нет. */
    suspend fun prepareSegmenter(): Boolean = try {
        val client = ModuleInstall.getClient(context)
        val available = client.areModulesAvailable(segmenter).await().areModulesAvailable()
        if (!available) {
            client.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).await()
        }
        available
    } catch (e: Exception) {
        false
    }

    suspend fun thumbnail(uri: Uri, side: Int = 360): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(uri, side) }.getOrNull()
    }

    suspend fun process(uri: Uri, preset: Preset, removeBackground: Boolean, margin: Float): ProcessResult {
        val source = withContext(Dispatchers.IO) { decode(uri, max(preset.width, preset.height) * 6 / 5) }

        var subject = source
        var removed = false
        if (removeBackground) {
            val foreground = runCatching {
                segmenter.process(InputImage.fromBitmap(source, 0)).await().foregroundBitmap
            }.getOrNull()
            if (foreground != null) {
                val cropped = withContext(Dispatchers.Default) { cropToContent(foreground) }
                if (cropped != null) {
                    subject = cropped
                    removed = true
                }
            }
        }

        val out = withContext(Dispatchers.Default) { compose(subject, preset, margin) }
        if (subject !== source) subject.recycle()
        source.recycle()
        return ProcessResult(out, removed)
    }

    /** Белый холст нужного размера, товар по центру с одинаковыми полями. */
    private fun compose(subject: Bitmap, preset: Preset, margin: Float): Bitmap {
        val out = Bitmap.createBitmap(preset.width, preset.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val availW = preset.width * (1f - 2f * margin)
        val availH = preset.height * (1f - 2f * margin)
        // Не растягиваем маленькие фото больше чем в 3 раза — иначе будет мыло
        val scale = min(min(availW / subject.width, availH / subject.height), 3f)
        val w = subject.width * scale
        val h = subject.height * scale
        val left = (preset.width - w) / 2f
        val top = (preset.height - h) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(subject, null, RectF(left, top, left + w, top + h), paint)
        return out
    }

    /** Обрезает прозрачные края вокруг вырезанного товара. */
    private fun cropToContent(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 24) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (right < 0) return null
        val r = Rect(left, top, right + 1, bottom + 1)
        // Слишком маленький объект — скорее всего ошибка вырезания
        if (r.width() < w / 20 || r.height() < h / 20) return null
        return Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
    }

    /** Открывает фото с учётом поворота камеры и уменьшает до maxSide. */
    private fun decode(uri: Uri, maxSide: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val s = max(w, h).toFloat() / maxSide
                if (s > 1f) decoder.setTargetSize((w / s).roundToInt(), (h / s).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.copy(Bitmap.Config.ARGB_8888, false)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Не удалось открыть фото")
        val rotation = resolver.openInputStream(uri).use { stream ->
            if (stream == null) 0 else ExifInterface(stream).rotationDegrees
        }
        val s = max(raw.width, raw.height).toFloat() / maxSide
        val m = Matrix()
        if (rotation != 0) m.postRotate(rotation.toFloat())
        if (s > 1f) m.postScale(1f / s, 1f / s)
        if (m.isIdentity) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
    }

    /** Сохраняет JPEG в Галерею: Pictures/MiniSul/<площадка>. */
    fun save(bitmap: Bitmap, displayName: String, folder: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MiniSul/$folder")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в Галерее")
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { "Нет доступа к Галерее" }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun preview(bitmap: Bitmap, side: Int = 360): Bitmap {
        val s = max(bitmap.width, bitmap.height).toFloat() / side
        if (s <= 1f) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width / s).roundToInt(), (bitmap.height / s).roundToInt(), true)
    }
}
