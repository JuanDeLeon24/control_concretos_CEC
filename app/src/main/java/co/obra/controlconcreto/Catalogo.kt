package co.obra.controlconcreto

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.text.Normalizer
import java.util.TreeMap
import java.util.zip.ZipInputStream

class ResultadoCatalogo(val items: List<ItemCatalogo>, val elementos: List<String>, val omitidas: Int)

/**
 * Catálogo de frentes y tramos cargado desde una plantilla CSV o Excel (.xlsx).
 * Columnas: Frente | Tramo | Abscisa | Ubicación (módulo) | Elemento vaciado.
 * La columna Elemento vaciado es una lista aparte (uno por fila). Todo se lee sin internet ni librerías.
 */
object Catalogo {

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().trim()

    // ---------- Consultas para los formularios ----------

    /** Texto que queda en la localización del mixer: "Módulo 15 – K0+180". */
    fun etiqueta(i: ItemCatalogo): String =
        listOf(i.modulo, i.abscisa).filter { it.isNotBlank() }.joinToString(" – ")

    fun frentes(c: List<ItemCatalogo>): List<String> =
        c.map { it.frente }.filter { it.isNotBlank() }.distinct()

    fun tramos(c: List<ItemCatalogo>, frente: String): List<String> =
        c.filter { it.frente == frente }.map { it.tramo }.filter { it.isNotBlank() }.distinct()

    fun lugares(c: List<ItemCatalogo>, frente: String, tramo: String): List<String> =
        c.filter { it.frente == frente && (tramo.isBlank() || it.tramo == tramo) }
            .map { etiqueta(it) }.filter { it.isNotBlank() }.distinct()

    // ---------- Plantillas (vienen dentro de la app, en assets) ----------

    fun plantilla(ctx: Context, excel: Boolean): File {
        val nombre = if (excel) "plantilla_frentes_tramos.xlsx" else "plantilla_frentes_tramos.csv"
        val dir = File(ctx.filesDir, "exportes").apply { mkdirs() }
        val f = File(dir, nombre)
        ctx.assets.open(nombre).use { inp -> f.outputStream().use { inp.copyTo(it) } }
        return f
    }

    // ---------- Lectura del archivo ----------

    fun leer(ctx: Context, uri: Uri): ResultadoCatalogo {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("No se pudo abrir el archivo")
        if (bytes.size < 4) throw IllegalArgumentException("El archivo está vacío")
        val filas = when {
            bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> leerXlsx(bytes)
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() ->
                throw IllegalArgumentException("Es un Excel antiguo (.xls). Guárdalo como .xlsx o como CSV e inténtalo de nuevo")
            else -> leerCsv(bytes)
        }
        return aItems(filas)
    }

    private fun aItems(filas: List<List<String>>): ResultadoCatalogo {
        val limpias = filas.map { f -> f.map { it.trim() } }.filter { f -> f.any { it.isNotEmpty() } }
        if (limpias.isEmpty()) throw IllegalArgumentException("El archivo está vacío")

        // Busca la fila de encabezados (la que tiene "Frente") en las primeras filas
        val iEnc = limpias.take(10).indexOfFirst { f -> f.any { norm(it) == "frente" || norm(it) == "frentes" } }
        var cF = 0; var cT = 1; var cA = 2; var cM = 3; var cE = 4
        var inicio = 0
        if (iEnc >= 0) {
            val enc = limpias[iEnc].map { norm(it) }
            fun buscar(vararg claves: String): Int = enc.indexOfFirst { h -> claves.any { h.startsWith(it) } }
            cF = buscar("frente")
            cT = buscar("tramo")
            cA = buscar("abscisa", "absc", "pk", "progresiva")
            cM = buscar("ubicacion", "modulo", "localizacion", "lugar", "sitio")
            cE = buscar("elemento", "actividad", "tipo de vaciado")
            inicio = iEnc + 1
        }

        val items = mutableListOf<ItemCatalogo>()
        val elementos = mutableListOf<String>()
        var omitidas = 0
        var frenteAnterior = ""
        for (f in limpias.drop(inicio)) {
            fun v(i: Int): String = if (i >= 0) f.getOrElse(i) { "" } else ""
            // La columna de elementos es una lista independiente de las demás columnas
            val el = v(cE)
            if (el.isNotBlank() && !el.equals(Elementos.OTRO, ignoreCase = true)) elementos.add(el)
            if (v(cF).isBlank() && v(cT).isBlank() && v(cA).isBlank() && v(cM).isBlank()) {
                if (el.isBlank()) omitidas++
                continue
            }
            val frente = v(cF).ifBlank { frenteAnterior }
            if (frente.isBlank()) { omitidas++; continue }
            frenteAnterior = frente
            items.add(ItemCatalogo(frente = frente, tramo = v(cT), abscisa = v(cA), modulo = v(cM)))
        }
        val unicos = items.distinct()
        val elems = elementos.distinct()
        if (unicos.isEmpty() && elems.isEmpty())
            throw IllegalArgumentException("No encontré filas válidas. Revisa que la columna Frente tenga datos")
        return ResultadoCatalogo(unicos, elems, omitidas)
    }

    // ---------- CSV ----------

    private fun leerCsv(bytes: ByteArray): List<List<String>> {
        var txt = String(bytes, Charsets.UTF_8)
        if (txt.contains('\uFFFD')) txt = String(bytes, charset("windows-1252")) // CSV guardado por Excel en español
        txt = txt.removePrefix("\uFEFF")
        val primera = txt.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val sep = listOf(';', ',', '\t').maxByOrNull { s -> primera.count { it == s } } ?: ';'

        val filas = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val campo = StringBuilder()
        var comillas = false
        var i = 0
        while (i < txt.length) {
            val ch = txt[i]
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < txt.length && txt[i + 1] == '"') { campo.append('"'); i++ } else comillas = false
                } else campo.append(ch)
            } else if (ch == '"') {
                comillas = true
            } else if (ch == sep) {
                fila.add(campo.toString()); campo.setLength(0)
            } else if (ch == '\n') {
                fila.add(campo.toString()); campo.setLength(0)
                filas.add(fila); fila = mutableListOf()
            } else if (ch != '\r') {
                campo.append(ch)
            }
            i++
        }
        if (campo.isNotEmpty() || fila.isNotEmpty()) { fila.add(campo.toString()); filas.add(fila) }
        return filas
    }

    // ---------- Excel (.xlsx) ----------

    private fun leerXlsx(bytes: ByteArray): List<List<String>> {
        val partes = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name.removePrefix("/")
                if (n == "xl/sharedStrings.xml" || n == "xl/workbook.xml" ||
                    n == "xl/_rels/workbook.xml.rels" || (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
                ) partes[n] = z.readBytes()
            }
        }
        val compartidos = partes["xl/sharedStrings.xml"]?.let { cadenas(it) } ?: emptyList()
        val hoja = primeraHoja(partes) ?: throw IllegalArgumentException("No se encontró ninguna hoja en el Excel")
        return celdas(hoja, compartidos)
    }

    /** La primera hoja según el libro (la de más a la izquierda). */
    private fun primeraHoja(p: Map<String, ByteArray>): ByteArray? {
        val wb = p["xl/workbook.xml"]?.toString(Charsets.UTF_8)
        val rels = p["xl/_rels/workbook.xml.rels"]?.toString(Charsets.UTF_8)
        if (wb != null && rels != null) {
            val hojaTag = Regex("<(?:\\w+:)?sheet\\b[^>]*>").find(wb)?.value
            val rid = hojaTag?.let { Regex("\\b\\w+:id=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            if (rid != null) {
                Regex("<(?:\\w+:)?Relationship\\b[^>]*>").findAll(rels).forEach { m ->
                    val tag = m.value
                    val id = Regex("\\bId=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)
                    val destino = Regex("\\bTarget=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)
                    if (id == rid && destino != null) {
                        val ruta = if (destino.startsWith("/")) destino.removePrefix("/") else "xl/" + destino
                        p[ruta]?.let { return it }
                    }
                }
            }
        }
        return p["xl/worksheets/sheet1.xml"]
            ?: p.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull()?.let { p[it] }
    }

    private fun parser(b: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(b), "UTF-8")
    }

    private fun local(n: String?): String = (n ?: "").substringAfter(':')

    private fun cadenas(b: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = parser(b)
        val sb = StringBuilder()
        var enT = false
        var enFonetica = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (local(p.name)) {
                    "si" -> sb.setLength(0)
                    "t" -> enT = true
                    "rPh" -> enFonetica = true
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (local(p.name)) {
                    "si" -> out.add(sb.toString())
                    "t" -> enT = false
                    "rPh" -> enFonetica = false
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (enT && !enFonetica) sb.append(p.text ?: "")
            }
            ev = p.next()
        }
        return out
    }

    private fun columna(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1)
            else break
        }
        return n - 1
    }

    /** Los números enteros guardados por Excel como "15" o "15.0" se muestran como "15". */
    private fun numeroTexto(s: String): String {
        val d = s.trim().toDoubleOrNull() ?: return s.trim()
        return if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else s.trim()
    }

    private fun celdas(b: ByteArray, compartidos: List<String>): List<List<String>> {
        val filas = TreeMap<Int, TreeMap<Int, String>>()
        val p = parser(b)
        var filaActual = 0
        var colSiguiente = 0
        var col = 0
        var tipo = ""
        var enCelda = false
        var enValor = false
        val valor = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (local(p.name)) {
                    "row" -> {
                        filaActual = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (filaActual + 1)
                        colSiguiente = 0
                    }
                    "c" -> {
                        enCelda = true
                        valor.setLength(0)
                        tipo = p.getAttributeValue(null, "t") ?: ""
                        val r = p.getAttributeValue(null, "r")
                        col = if (r != null) columna(r) else colSiguiente
                        colSiguiente = col + 1
                    }
                    "v", "t" -> if (enCelda) enValor = true
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (local(p.name)) {
                    "v", "t" -> enValor = false
                    "c" -> {
                        enCelda = false
                        val crudo = valor.toString()
                        val texto = when (tipo) {
                            "s" -> crudo.trim().toIntOrNull()?.let { compartidos.getOrNull(it) } ?: ""
                            "b" -> if (crudo.trim() == "1") "VERDADERO" else "FALSO"
                            "inlineStr", "str" -> crudo
                            else -> numeroTexto(crudo)
                        }
                        if (texto.isNotBlank() && col >= 0) filas.getOrPut(filaActual) { TreeMap() }[col] = texto
                    }
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (enValor) valor.append(p.text ?: "")
            }
            ev = p.next()
        }
        return filas.values.map { m ->
            val ultima = m.lastKey()
            (0..ultima).map { m[it] ?: "" }
        }
    }
}

/**
 * Elemento que se vacía con cada mixer (la lista se carga desde la plantilla).
 * Queda al final de la localización: "Módulo 15 – K0+180 · BV-HD".
 */
object Elementos {
    /** Opción al final de las listas para escribir un lugar o actividad que no está predefinido. */
    const val OTRO = "Otro..."
    private const val SEP = " · "

    /** Separa una localización guardada en (lugar, elemento). El elemento puede ser uno escrito con "Otro...". */
    fun separar(loc: String, lista: List<String>): Pair<String, String> {
        val t = loc.trim()
        if (t in lista) return "" to t
        val i = t.lastIndexOf(SEP)
        if (i >= 0) {
            val e = t.substring(i + SEP.length).trim()
            if (e.isNotEmpty()) return t.substring(0, i).trim() to e
        }
        return t to ""
    }

    fun juntar(lugar: String, elemento: String): String =
        listOf(lugar.trim(), elemento.trim()).filter { it.isNotEmpty() }.joinToString(SEP)
}
