package co.obra.controlconcreto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Fotos de las remisiones de planta, guardadas dentro del teléfono (una por mixer). */
object Fotos {
    private fun dir(ctx: Context) = File(ctx.filesDir, "remisiones").apply { mkdirs() }
    fun archivo(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")
    private fun archivoMini(ctx: Context, id: String) = File(dir(ctx), "${id}_min.jpg")
    fun existe(ctx: Context, id: String) = archivo(ctx, id).exists()
    fun captura(ctx: Context) = File(File(ctx.cacheDir, "camara").apply { mkdirs() }, "captura.jpg")

    fun miniatura(ctx: Context, id: String): Bitmap? =
        archivoMini(ctx, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun cargar(ctx: Context, id: String, maxLado: Int = 2400): Bitmap? =
        archivo(ctx, id).takeIf { it.exists() }?.let { decodificar(it.path, maxLado, 0) }

    fun eliminar(ctx: Context, id: String) {
        archivo(ctx, id).delete(); archivoMini(ctx, id).delete()
    }

    fun guardar(ctx: Context, id: String, bmp: Bitmap) {
        FileOutputStream(archivo(ctx, id)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val s = 240f / max(bmp.width, bmp.height)
        val mini = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).toInt()), max(1, (bmp.height * s).toInt()), true)
        FileOutputStream(archivoMini(ctx, id)).use { mini.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** Foto recién tomada con la cámara, con la orientación corregida. */
    fun cargarCaptura(ctx: Context): Bitmap? {
        val f = captura(ctx)
        if (!f.exists() || f.length() == 0L) return null
        return decodificar(f.path, 2600, rotacionExif(f.path))
    }

    /** Imagen elegida de la galería. */
    fun cargarUri(ctx: Context, uri: Uri): Bitmap? {
        val tmp = captura(ctx)
        ctx.contentResolver.openInputStream(uri)?.use { inp -> FileOutputStream(tmp).use { inp.copyTo(it) } } ?: return null
        return cargarCaptura(ctx)
    }

    fun rotar90(b: Bitmap): Bitmap =
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f) }, true)

    private fun rotacionExif(path: String): Int = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun decodificar(path: String, maxLado: Int, rot: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, limites)
        if (limites.outWidth <= 0) return null
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= maxLado) muestra *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return null
        val esc = min(1f, maxLado.toFloat() / max(bmp.width, bmp.height))
        if (rot == 0 && esc >= 1f) return bmp
        val m = Matrix().apply {
            if (esc < 1f) postScale(esc, esc)
            if (rot != 0) postRotate(rot.toFloat())
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}

/** Recorte con corrección de perspectiva + filtro de escaneo (todo local, sin internet). */
object Escaner {

    /** Esquinas en orden: arriba-izq, arriba-der, abajo-der, abajo-izq (coordenadas del bitmap). */
    fun enderezar(src: Bitmap, e: List<PointF>, maxLado: Int = 2200): Bitmap {
        fun d(a: PointF, b: PointF) = hypot(a.x - b.x, a.y - b.y)
        var w = max(d(e[0], e[1]), d(e[3], e[2]))
        var h = max(d(e[0], e[3]), d(e[1], e[2]))
        val s = min(1f, maxLado / max(w, h))
        w *= s; h *= s
        val ancho = max(1, w.roundToInt()); val alto = max(1, h.roundToInt())
        val out = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.setPolyToPoly(
            floatArrayOf(e[0].x, e[0].y, e[1].x, e[1].y, e[2].x, e[2].y, e[3].x, e[3].y), 0,
            floatArrayOf(0f, 0f, ancho.toFloat(), 0f, ancho.toFloat(), alto.toFloat(), 0f, alto.toFloat()), 0, 4
        )
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return out
    }

    /**
     * Filtro tipo escáner: estima la iluminación del papel, la divide (quita sombras y el color del
     * papel de las copias), lleva el papel a blanco y refuerza el texto.
     */
    fun filtroEscaneo(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height; val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(n)
        for (i in 0 until n) {
            val c = px[i]
            g[i] = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
        }

        // 1) Iluminación del papel: máximo por bloques
        val f = max(8, max(w, h) / 64)
        val gw = (w + f - 1) / f; val gh = (h + f - 1) / f
        var fondo = FloatArray(gw * gh)
        for (by in 0 until gh) for (bx in 0 until gw) {
            var m = 0
            val yFin = min(h, (by + 1) * f); val xFin = min(w, (bx + 1) * f)
            var y = by * f
            while (y < yFin) {
                var x = bx * f; val fila = y * w
                while (x < xFin) { val v = g[fila + x]; if (v > m) m = v; x += 2 }
                y += 2
            }
            fondo[by * gw + bx] = m.toFloat()
        }
        repeat(2) {
            val t = FloatArray(gw * gh)
            for (y in 0 until gh) for (x in 0 until gw) {
                var s = 0f; var k = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val yy = y + dy; val xx = x + dx
                    if (yy in 0 until gh && xx in 0 until gw) { s += fondo[yy * gw + xx]; k++ }
                }
                t[y * gw + x] = s / k
            }
            fondo = t
        }

        // 2) Normalizar cada pixel contra el fondo (interpolación bilineal)
        val norm = IntArray(n)
        val hist = IntArray(256)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / f - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(gh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / f - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(gw - 1, x0 + 1); val tx = fx - x0
                val a = fondo[y0 * gw + x0] * (1 - tx) + fondo[y0 * gw + x1] * tx
                val b = fondo[y1 * gw + x0] * (1 - tx) + fondo[y1 * gw + x1] * tx
                val bg = max(40f, a * (1 - ty) + b * ty)
                val v = min(255, (g[y * w + x] * 255f / bg).toInt())
                norm[y * w + x] = v
                hist[v]++
            }
        }

        // 3) Niveles: punto negro por percentil, papel a blanco, texto reforzado
        var acc = 0; var negro = 0
        val objetivo = n * 0.01
        for (i in 0..255) { acc += hist[i]; if (acc >= objetivo) { negro = i; break } }
        negro = negro.coerceAtMost(150)
        val blanco = 205
        val lut = IntArray(256) { v ->
            val t = ((v - negro).toFloat() / (blanco - negro)).coerceIn(0f, 1f)
            (255f * t.pow(1.5f)).roundToInt()
        }
        for (i in 0 until n) {
            val v = lut[norm[i]]
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
