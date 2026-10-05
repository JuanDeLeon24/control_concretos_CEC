package co.obra.controlconcreto

import android.content.Context
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Exportador e importador XLSX nativo, sin dependencias externas. */
object Excel {
    const val ORIGEN_LOCAL = "Mis datos"
    const val ORIGEN_TODOS = "Todos"

    fun generarJornada(ctx: Context, j: Jornada): File =
        generar(ctx, listOf(j), "control_concreto_${j.fecha}_${j.turno.lowercase()}.xlsx")

    fun generarHistorial(ctx: Context, jornadas: List<Jornada>): File =
        generar(ctx, jornadas, "control_concreto_historial.xlsx")

    fun generarOrigen(ctx: Context, jornadas: List<Jornada>, origen: String): File {
        val filtradas = if (origen == ORIGEN_TODOS) jornadas else jornadas.filter { it.origen == origen }
        val nombre = "control_concreto_${nombreArchivo(origen)}.xlsx"
        return generar(ctx, filtradas, nombre)
    }

    /** Lee todos los XLSX de la carpeta seleccionada. Los datos quedan identificados por origen. */
    fun importarCarpeta(ctx: Context, folderUri: android.net.Uri, origen: String): List<Jornada> {
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri, android.provider.DocumentsContract.getTreeDocumentId(folderUri)
        )
        val resolver = ctx.contentResolver
        val out = mutableListOf<Jornada>()
        resolver.query(children, arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { c ->
            val idIx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val name = c.getString(nameIx) ?: ""
                val mime = c.getString(mimeIx) ?: ""
                if (!name.lowercase().endsWith(".xlsx") &&
                    mime != "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") continue
                val child = android.provider.DocumentsContract.buildDocumentUriUsingTree(folderUri, c.getString(idIx))
                resolver.openInputStream(child)?.use { input -> out += leerXlsx(input, origen, name) }
            }
        }
        // Si la carpeta contiene varios Excel con jornadas repetidas, las consolidamos
        // por origen + fecha + turno + frente + tramo para no duplicarlas.
        return out.groupBy { listOf(it.origen, it.fecha, it.turno, it.nombre, it.frente, it.tramo) }
            .map { (key, jornadasMismoGrupo) ->
                val base = jornadasMismoGrupo.first()
                val mixers = jornadasMismoGrupo.flatMap { it.mixers }
                    .distinctBy { listOf(it.codigo, it.llegada, it.inicio, it.fin, it.cant, it.asPlanta, it.asObra, it.loc) }
                    .sortedWith(compareBy<Mixer> { it.orden }.thenBy { it.codigo })
                    .mapIndexed { i, m -> m.copy(id = "${base.id}:m${i + 1}", jornadaId = base.id, orden = i + 1) }
                base.copy(mixers = mixers)
            }
    }

    private fun generar(ctx: Context, jornadas: List<Jornada>, nombre: String): File {
        val dir = File(ctx.filesDir, "excel")
        dir.mkdirs()
        val out = File(dir, nombre)
        val headers = listOf(
            "Origen", "Fecha", "Turno", "Nombre", "Frente", "Tramo", "N° mixer",
            "Código / remisión", "Llegada", "Inicio descargue", "Fin descargue",
            "Cantidad (m³)", "Asentamiento planta (pulg)", "Asentamiento obra (pulg)",
            "Temperatura (°C)", "Localización", "Observación"
        )
        val rows = ArrayList<List<Any?>>()
        jornadas.sortedWith(compareBy<Jornada> { it.origen }.thenByDescending { it.fecha }.thenBy { it.turno }).forEach { j ->
            if (j.mixers.isEmpty()) {
                rows.add(listOf(j.origen, j.fecha, j.turno, j.nombre, j.frente, j.tramo, "", "", "", "", "", "", "", "", "", "", ""))
            } else {
                j.mixers.sortedBy { it.orden }.forEachIndexed { idx, m ->
                    rows.add(listOf(j.origen, j.fecha, j.turno, j.nombre, j.frente, j.tramo, idx + 1,
                        m.codigo, m.llegada, m.inicio, m.fin, m.cant, m.asPlanta, m.asObra, m.temp, m.loc, m.obs))
                }
            }
        }
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes())
            entry(zip, "_rels/.rels", rootRels())
            entry(zip, "xl/workbook.xml", workbook())
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            entry(zip, "xl/worksheets/sheet1.xml", worksheet(headers, rows))
        }
        return out
    }

    private fun leerXlsx(input: InputStream, origen: String, nombre: String): List<Jornada> {
        val tmp = File.createTempFile("cc_import_", ".xlsx")
        input.use { it.copyTo(FileOutputStream(tmp)) }
        try {
            ZipFile(tmp).use { zip ->
                val shared = zip.getEntry("xl/sharedStrings.xml")?.let { parseShared(zip.getInputStream(it)) } ?: emptyList()
                val sheetEntry = zip.entries().asSequence().firstOrNull { it.name.startsWith("xl/worksheets/") && it.name.endsWith(".xml") }
                    ?: throw IllegalArgumentException("El Excel no contiene una hoja válida")
                val doc = parseXml(zip.getInputStream(sheetEntry))
                val rows = doc.getElementsByTagName("row")
                if (rows.length == 0) throw IllegalArgumentException("El Excel no contiene filas")
                val matrix = mutableListOf<Map<Int, String>>()
                for (i in 0 until rows.length) {
                    val row = rows.item(i) as Element
                    val cells = row.getElementsByTagName("c")
                    val map = mutableMapOf<Int, String>()
                    for (k in 0 until cells.length) {
                        val cell = cells.item(k) as Element
                        val ref = cell.getAttribute("r")
                        val col = columna(ref)
                        map[col] = valorCelda(cell, shared)
                    }
                    matrix += map
                }
                if (matrix.isEmpty()) return emptyList()
                val headers = matrix.first().mapValues { normalizar(it.value) }
                fun idx(vararg names: String): Int? = headers.entries.firstOrNull { names.any { n -> it.value == normalizar(n) } }?.key
                val iFecha = idx("Fecha") ?: throw IllegalArgumentException("Falta la columna Fecha")
                val iTurno = idx("Turno") ?: throw IllegalArgumentException("Falta la columna Turno")
                val iNombre = idx("Nombre")
                val iFrente = idx("Frente")
                val iTramo = idx("Tramo")
                val iN = idx("N° mixer", "Nº mixer", "No mixer")
                val iCodigo = idx("Código / remisión", "Codigo / remision", "Código", "Codigo")
                val iLlegada = idx("Llegada")
                val iInicio = idx("Inicio descargue")
                val iFin = idx("Fin descargue")
                val iCant = idx("Cantidad (m³)", "Cantidad")
                val iAsP = idx("Asentamiento planta (pulg)", "Asentamiento planta")
                val iAsO = idx("Asentamiento obra (pulg)", "Asentamiento obra")
                val iTemp = idx("Temperatura (°C)", "Temperatura")
                val iLoc = idx("Localización", "Localizacion")
                val iObs = idx("Observación", "Observacion")
                val iOrigen = idx("Origen")
                val effectiveOrigin = origen.ifBlank { "Importado" }
                data class R(val fecha:String,val turno:String,val nombre:String,val frente:String,val tramo:String,val orden:Int,val codigo:String,val llegada:String,val inicio:String,val fin:String,val cant:String,val asp:String,val aso:String,val temp:String,val loc:String,val obs:String)
                val records = matrix.drop(1).mapNotNull { r ->
                    val fecha = r[iFecha].orEmpty().trim()
                    if (fecha.isBlank()) return@mapNotNull null
                    R(fecha, r[iTurno].orEmpty().ifBlank { "Día" }, r[iNombre].orEmpty(), r[iFrente].orEmpty(), r[iTramo].orEmpty(),
                        r[iN ?: -1].orEmpty().toIntOrNull() ?: 0, r[iCodigo ?: -1].orEmpty(), r[iLlegada ?: -1].orEmpty(),
                        r[iInicio ?: -1].orEmpty(), r[iFin ?: -1].orEmpty(), r[iCant ?: -1].orEmpty(), r[iAsP ?: -1].orEmpty(),
                        r[iAsO ?: -1].orEmpty(), r[iTemp ?: -1].orEmpty(), r[iLoc ?: -1].orEmpty(), r[iObs ?: -1].orEmpty())
                }
                return records.groupBy { listOf(it.fecha,it.turno,it.nombre,it.frente,it.tramo) }.map { (key, rs) ->
                    val base = "${effectiveOrigin}|${key.joinToString("|")}".hashCode().toUInt().toString(16)
                    val jid = "imp:$base"
                    Jornada(jid, key[0], key[1], key[2], key[3], key[4], T.marcaTiempo(),
                        rs.sortedWith(compareBy<R> { if (it.orden > 0) it.orden else Int.MAX_VALUE }.thenBy { it.codigo }).mapIndexed { n, r ->
                            Mixer("$jid:m${n+1}", jid, n + 1, r.codigo, r.llegada, r.inicio, r.fin, r.cant, r.asp, r.aso, r.temp, r.loc, r.obs)
                        },
                        origen = effectiveOrigin
                    )
                }
            }
        } finally { tmp.delete() }
    }

    private fun parseShared(input: InputStream): List<String> {
        val doc = parseXml(input)
        val nodes = doc.getElementsByTagName("si")
        return (0 until nodes.length).map { i ->
            val texts = (nodes.item(i) as Element).getElementsByTagName("t")
            (0 until texts.length).joinToString("") { texts.item(it).textContent }
        }
    }

    private fun parseXml(input: InputStream) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
    }.newDocumentBuilder().parse(input)

    private fun valorCelda(c: Element, shared: List<String>): String {
        val tipo = c.getAttribute("t")
        return when (tipo) {
            "inlineStr" -> c.getElementsByTagName("t").let { n -> (0 until n.length).joinToString("") { n.item(it).textContent } }
            "s" -> c.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()?.let { shared.getOrNull(it) ?: "" } ?: ""
            else -> c.getElementsByTagName("v").item(0)?.textContent ?: ""
        }
    }

    private fun columna(ref: String): Int {
        var n = 0
        for (ch in ref.takeWhile { it.isLetter() }) n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        return n - 1
    }

    private fun normalizar(s: String) = s.trim().lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u").replace("ñ","n").replace(Regex("\\s+"), " ")
    private fun nombreArchivo(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "todos" }
    private fun entry(zip: ZipOutputStream, name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun esc(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun cell(value: Any?): String = if (value == null) "<c/>" else if (value is Number) "<c t=\"n\"><v>$value</v></c>" else "<c t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value.toString())}</t></is></c>"
    private fun worksheet(headers: List<String>, rows: List<List<Any?>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        (listOf(headers) + rows).forEachIndexed { r, row -> append("<row r=\"${r+1}\">"); row.forEachIndexed { c, v -> append("<c r=\"${letra(c)}${r+1}\">"); append(cellBody(v)); append("</c>") }; append("</row>") }
        append("</sheetData></worksheet>")
    }
    private fun cellBody(value: Any?): String = if (value == null) "" else if (value is Number) "<v>$value</v>" else "<is><t xml:space=\"preserve\">${esc(value.toString())}</t></is>"
    private fun letra(i: Int): String { var n=i+1; var s=""; while(n>0){ val r=(n-1)%26; s=('A'.code+r).toChar()+s; n=(n-1)/26 }; return s }
    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Control concreto" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}
