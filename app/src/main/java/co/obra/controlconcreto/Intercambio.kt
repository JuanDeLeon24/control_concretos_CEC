package co.obra.controlconcreto

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Jornada que llega de otro teléfono, con las fotos de sus remisiones (id del mixer → bytes JPG). */
class Paquete(val jornada: Jornada, val fotos: Map<String, ByteArray>)

/**
 * Archivo para pasar UNA jornada completa a otro teléfono: datos, mixers y fotos en un solo .zip.
 * Dentro van "jornada.json" y la carpeta "fotos/" con una imagen por mixer.
 */
object Intercambio {
    private const val TIPO = "jornada-concreto-cec"
    const val MIME = "application/zip"

    fun exportar(ctx: Context, j: Jornada): File {
        val dir = File(ctx.filesDir, "exportes").apply { mkdirs() }
        val base = "Jornada ${j.fecha} ${j.turno} ${j.frente} ${j.tramo}".trim()
        val limpio = Normalizer.normalize(base, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .replace(Regex("[^A-Za-z0-9+._-]+"), "_").trim('_').take(80)
        val f = File(dir, "$limpio.zip")
        val json = JSONObject()
            .put("tipo", TIPO).put("version", 1)
            .put("exportada", T.marcaTiempo())
            .put("jornada", Respaldo.jornadaAJson(j))
        ZipOutputStream(FileOutputStream(f)).use { z ->
            z.putNextEntry(ZipEntry("jornada.json"))
            z.write(json.toString(1).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            j.mixers.forEach { m ->
                val foto = Fotos.archivo(ctx, m.id)
                if (foto.exists()) {
                    z.putNextEntry(ZipEntry("fotos/${m.id}.jpg"))
                    foto.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return f
    }

    fun leer(ctx: Context, uri: Uri): Paquete {
        var texto: String? = null
        val fotos = HashMap<String, ByteArray>()
        val entrada = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el archivo")
        entrada.use { inp ->
            ZipInputStream(inp.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name.removePrefix("/")
                    if (n == "jornada.json") texto = z.readBytes().toString(Charsets.UTF_8)
                    else if (n.startsWith("fotos/") && n.endsWith(".jpg"))
                        fotos[n.removePrefix("fotos/").removeSuffix(".jpg")] = z.readBytes()
                }
            }
        }
        val o = JSONObject(texto ?: throw IllegalArgumentException("Ese archivo no es una jornada exportada desde esta app"))
        if (o.optString("tipo") != TIPO) throw IllegalArgumentException("Ese archivo no es una jornada exportada desde esta app")
        val j = Respaldo.jornadaDesdeJson(o.getJSONObject("jornada"))
        val ids = j.mixers.map { it.id }.toSet()
        return Paquete(j, fotos.filterKeys { it in ids })   // solo fotos de mixers de la jornada
    }

    /** Jornada del teléfono que choca con la que llega: mismo identificador interno, o misma fecha, turno, frente y tramo. */
    fun conflicto(existentes: List<Jornada>, j: Jornada): Jornada? =
        existentes.find { it.id == j.id }
            ?: existentes.find { it.fecha == j.fecha && it.turno == j.turno && it.frente == j.frente && it.tramo == j.tramo }

    /**
     * Guarda la jornada recibida.
     * - reemplazar = jornada local que se borra antes (con sus fotos) para dejar la que llega.
     * - conservarAmbas = la que llega entra como jornada aparte, con identificadores nuevos.
     * Devuelve la jornada tal como quedó guardada.
     */
    fun instalar(ctx: Context, db: Db, p: Paquete, reemplazar: Jornada?, conservarAmbas: Boolean): Jornada {
        var j = p.jornada
        var fotos = p.fotos
        if (conservarAmbas) {
            val nuevoId = T.uid()
            val mapa = HashMap<String, String>()
            val mixers = j.mixers.map { m -> val nid = T.uid(); mapa[m.id] = nid; m.copy(id = nid, jornadaId = nuevoId) }
            fotos = p.fotos.mapKeys { (k, _) -> mapa[k] ?: k }
            j = j.copy(id = nuevoId, mixers = mixers, creada = T.marcaTiempo())
        }
        if (reemplazar != null && !conservarAmbas) {
            reemplazar.mixers.forEach { Fotos.eliminar(ctx, it.id) }
            db.eliminarJornada(reemplazar.id)
        }
        // Por si la misma jornada ya estaba con otros mixers: se quitan sus fotos viejas
        if (!conservarAmbas) j.mixers.forEach { Fotos.eliminar(ctx, it.id) }
        db.importar(listOf(j))
        fotos.forEach { (id, bytes) -> Fotos.guardarBytes(ctx, id, bytes) }
        return j
    }
}
