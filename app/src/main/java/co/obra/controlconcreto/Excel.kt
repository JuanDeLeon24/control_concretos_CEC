package co.obra.controlconcreto

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exportador XLSX nativo, sin dependencias externas. */
object Excel {

    fun generarJornada(ctx: Context, j: Jornada): File =
        generar(ctx, listOf(j), "control_concreto_${j.fecha}_${j.turno.lowercase()}.xlsx")

    fun generarHistorial(ctx: Context, jornadas: List<Jornada>): File =
        generar(ctx, jornadas, "control_concreto_historial.xlsx")

    private fun generar(ctx: Context, jornadas: List<Jornada>, nombre: String): File {
        val dir = File(ctx.filesDir, "excel")
        dir.mkdirs()
        val out = File(dir, nombre)

        val headers = listOf(
            "Fecha", "Turno", "Nombre", "Frente", "Tramo", "N° mixer",
            "Código / remisión", "Llegada", "Inicio descargue", "Fin descargue",
            "Cantidad (m³)", "Asentamiento planta (pulg)", "Asentamiento obra (pulg)",
            "Temperatura (°C)", "Localización", "Observación"
        )

        val rows = ArrayList<List<Any?>>()
        jornadas.sortedWith(compareByDescending<Jornada> { it.fecha }.thenBy { it.turno }).forEach { j ->
            if (j.mixers.isEmpty()) {
                rows.add(listOf(
                    j.fecha, j.turno, j.nombre, j.frente, j.tramo, "",
                    "", "", "", "", "", "", "", "", "", ""
                ))
            } else {
                j.mixers.sortedBy { it.orden }.forEachIndexed { idx, m ->
                    rows.add(listOf(
                        j.fecha, j.turno, j.nombre, j.frente, j.tramo, idx + 1,
                        m.codigo, m.llegada, m.inicio, m.fin,
                        m.cant, m.asPlanta, m.asObra, m.temp, m.loc, m.obs
                    ))
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

    private fun entry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun esc(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun cell(value: Any?): String {
        if (value == null) return "<c/>"
        return when (value) {
            is Number -> "<c t=\"n\"><v>${value}</v></c>"
            else -> "<c t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value.toString())}</t></is></c>"
        }
    }

    private fun worksheet(headers: List<String>, rows: List<List<Any?>>): String {
        val all = ArrayList<List<Any?>>()
        all.add(headers)
        all.addAll(rows)
        val lastRow = maxOf(1, all.size)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<sheetData>""")
            all.forEachIndexed { r, row ->
                append("""<row r="${r + 1}">""")
                row.forEach { append(cell(it)) }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
    }

    private fun contentTypes() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRels() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbook() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="Control concreto" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRels() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        </Relationships>
    """.trimIndent()
}
