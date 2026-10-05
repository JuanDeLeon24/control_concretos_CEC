package co.obra.controlconcreto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object T {
    val ES: Locale = Locale("es", "CO")

    fun uid(): String = UUID.randomUUID().toString()
    fun hoy(): String = LocalDate.now().toString()
    fun ahora(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    fun marcaTiempo(): String = LocalDateTime.now().toString()
    fun turnoActual(): String { val h = LocalTime.now().hour; return if (h >= 18 || h < 6) "Noche" else "Día" }

    fun toMin(t: String): Int? = runCatching {
        val p = t.split(":"); p[0].trim().toInt() * 60 + p[1].trim().toInt()
    }.getOrNull()

    /** Diferencia en minutos; si cruza la medianoche suma 24 h. */
    fun diff(a: String, b: String): Int? {
        val x = toMin(a) ?: return null
        val y = toMin(b) ?: return null
        var d = y - x
        if (d < 0) d += 1440
        return d
    }

    fun partes(t: String): Pair<Int, Int> {
        val m = toMin(t) ?: return LocalTime.now().let { it.hour to it.minute }
        return (m / 60) to (m % 60)
    }

    /** Convierte números decimales y fracciones simples/mixed (ej. 1/4, 3 1/2) a Double. */
    fun num(s: String): Double? {
        val t = s.trim().replace(',', '.')
        if (t.isBlank()) return null
        t.toDoubleOrNull()?.let { return it }

        val partes = t.split(Regex("\\s+"))
        var total = 0.0
        var encontro = false
        for (parte in partes) {
            if (!parte.contains("/")) {
                val entero = parte.toDoubleOrNull() ?: return null
                total += entero
                encontro = true
            } else {
                val f = parte.split("/")
                if (f.size != 2) return null
                val n = f[0].toDoubleOrNull() ?: return null
                val d = f[1].toDoubleOrNull() ?: return null
                if (d == 0.0) return null
                total += n / d
                encontro = true
            }
        }
        return if (encontro) total else null
    }

    fun limpiar(s: String): String = s.trim().replace(',', '.')

    fun fmt(d: Double?, dec: Int = 1): String {
        if (d == null) return ""
        val nf = NumberFormat.getNumberInstance(ES)
        nf.maximumFractionDigits = dec
        nf.minimumFractionDigits = 0
        return nf.format(d)
    }

    fun dur(min: Double?): String {
        if (min == null) return "–"
        val total = Math.round(min).toInt()
        val h = total / 60; val m = total % 60
        return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "$m min"
    }

    fun rango(a: Double?, b: Double?, u: String): String = when {
        a == null || b == null -> "–"
        a == b -> fmt(a) + u
        else -> "${fmt(a)} a ${fmt(b)}$u"
    }

    fun volumen(j: Jornada): Double = j.mixers.sumOf { num(it.cant) ?: 0.0 }

    private fun fecha(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

    fun fechaCorta(iso: String): String =
        fecha(iso)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: iso

    fun fechaLarga(iso: String): String =
        fecha(iso)?.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES))
            ?.replaceFirstChar { it.titlecase(ES) } ?: iso

    fun diaNumero(iso: String): String = fecha(iso)?.dayOfMonth?.toString() ?: ""

    fun diaResto(iso: String): String =
        fecha(iso)?.format(DateTimeFormatter.ofPattern("EEEE, MMMM 'de' yyyy", ES)) ?: iso
}

data class Ultimo(val nombre: String, val frente: String, val tramo: String)

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("preferencias", Context.MODE_PRIVATE)
    fun ultimo(ctx: Context): Ultimo = sp(ctx).let {
        Ultimo(it.getString("nombre", "") ?: "", it.getString("frente", "") ?: "", it.getString("tramo", "") ?: "")
    }
    fun guardar(ctx: Context, j: Jornada) {
        sp(ctx).edit().putString("nombre", j.nombre).putString("frente", j.frente).putString("tramo", j.tramo).apply()
    }
}

/** Logo de la empresa: se carga desde la galería del teléfono o desde assets/logo.png. */
object Logo {
    private fun archivo(ctx: Context) = File(ctx.filesDir, "logo.png")

    fun cargar(ctx: Context): Bitmap? {
        val f = archivo(ctx)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        return runCatching { ctx.assets.open("logo.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    fun guardar(ctx: Context, uri: Uri): Boolean {
        return try {
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return false
            val esc = minOf(1f, 900f / maxOf(bmp.width, bmp.height))
            val fin = if (esc < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * esc).toInt(), (bmp.height * esc).toInt(), true)
            else bmp
            FileOutputStream(archivo(ctx)).use { fin.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) { false }
    }

    fun quitar(ctx: Context) { archivo(ctx).delete() }
}

/** Copia de seguridad en JSON (compatible con la versión web de la app). */
object Respaldo {
    fun aJson(lista: List<Jornada>): String {
        val arr = JSONArray()
        lista.forEach { j ->
            val mx = JSONArray()
            j.mixers.forEach { m ->
                mx.put(
                    JSONObject().put("id", m.id).put("codigo", m.codigo).put("llegada", m.llegada)
                        .put("inicio", m.inicio).put("fin", m.fin).put("cant", m.cant)
                        .put("asPlanta", m.asPlanta).put("asObra", m.asObra).put("temp", m.temp)
                        .put("loc", m.loc).put("obs", m.obs)
                )
            }
            arr.put(
                JSONObject().put("id", j.id).put("fecha", j.fecha).put("turno", j.turno)
                    .put("nombre", j.nombre).put("frente", j.frente).put("tramo", j.tramo)
                    .put("creada", j.creada).put("origen", j.origen).put("mixers", mx)
            )
        }
        return JSONObject().put("app", "control-concreto").put("version", 1).put("jornadas", arr).toString(1)
    }

    private fun JSONObject.txt(k: String): String = if (isNull(k)) "" else optString(k, "")

    fun desdeJson(texto: String): List<Jornada> {
        val arr = JSONObject(texto).getJSONArray("jornadas")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val mx = o.optJSONArray("mixers") ?: JSONArray()
            Jornada(
                id = id, fecha = o.getString("fecha"), turno = o.txt("turno").ifBlank { "Día" },
                nombre = o.txt("nombre"), frente = o.txt("frente"), tramo = o.txt("tramo"),
                creada = o.txt("creada"),
                origen = o.txt("origen").ifBlank { Excel.ORIGEN_LOCAL },
                mixers = (0 until mx.length()).map { k ->
                    val m = mx.getJSONObject(k)
                    Mixer(
                        id = m.txt("id").ifBlank { T.uid() }, jornadaId = id, orden = k + 1,
                        codigo = m.txt("codigo"), llegada = m.txt("llegada"), inicio = m.txt("inicio"),
                        fin = m.txt("fin"), cant = m.txt("cant"), asPlanta = m.txt("asPlanta"),
                        asObra = m.txt("asObra"), temp = m.txt("temp"), loc = m.txt("loc"), obs = m.txt("obs")
                    )
                }
            )
        }
    }
}
