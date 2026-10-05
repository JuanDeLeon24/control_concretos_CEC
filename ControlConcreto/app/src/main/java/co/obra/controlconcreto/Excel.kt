package co.obra.controlconcreto

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Genera archivos Excel (.xlsx) directamente en el teléfono, sin conexión ni librerías externas. */
object Excel {
    // Índices de estilos (cellXfs en styles.xml)
    private const val NORMAL = 0
    private const val TITULO = 1
    private const val ENC = 2
    private const val ETIQ = 3
    private const val TEXTO = 4
    private const val DEC2 = 5
    private const val FRAC = 6
    private const val NUM = 7
    private const val TOT_ETIQ = 8
    private const val TOT_NUM = 9
    private const val CENTRO = 10
    private const val HORA = 11
    private const val FECHA = 12
    private const val SUB = 13
    private const val TOT_ENT = 14

    /** Fórmula con su resultado ya calculado (para visores que no recalculan). */
    private class Formula(val expr: String, val valor: Double)

    private class Celda(val valor: Any?, val estilo: Int)

    private class Hoja(val nombre: String, val anchos: List<Double>) {
        val filas = mutableListOf<Pair<Double?, List<Celda>>>()
        val combinadas = mutableListOf<String>()
        var filtro: String? = null
        var congelar = 0
        fun fila(celdas: List<Celda>, alto: Double? = null) { filas.add(alto to celdas) }
        val siguiente get() = filas.size + 1
    }

    private fun c(v: Any?, s: Int) = Celda(v, s)

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun esc(s: String) = s.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun hora(t: String): Celda = c(T.toMin(t)?.let { it / 1440.0 }, HORA)

    private fun pulg(s: String): Celda {
        if (s.isBlank()) return c(null, CENTRO)
        val d = T.pulgadas(s) ?: return c(s, CENTRO)
        return c(d, if (T.esOctavo(d)) FRAC else NUM)
    }

    private fun numero(s: String, estilo: Int): Celda {
        if (s.isBlank()) return c(null, estilo)
        return c(T.num(s) ?: s, if (T.num(s) == null) CENTRO else estilo)
    }

    // ================= Planilla de una jornada =================

    fun jornada(ctx: Context, j: Jornada): File {
        val h = Hoja("Jornada", listOf(8.0, 17.0, 9.0, 11.0, 10.0, 9.0, 13.0, 13.0, 12.0, 26.0, 32.0))
        val n = 11

        // Título
        h.fila(listOf(c("CONTROL LLEGADAS DE CONCRETO A OBRA", TITULO)) + List(8) { c(null, TITULO) } +
            listOf(c("PS-TUNEL 011", TITULO), c(null, TITULO)), 30.0)
        h.combinadas += "A1:I1"; h.combinadas += "J1:K1"
        h.fila(listOf(c("Construcciones El Cóndor S.A.", SUB)))
        h.combinadas += "A2:K2"

        // Datos de la jornada
        fun info(e1: String, v1: String, e2: String, v2: String, e3: String, v3: String) {
            val r = h.siguiente
            h.fila(listOf(c(e1, ETIQ), c(v1, TEXTO), c(null, TEXTO), c(null, TEXTO),
                c(e2, ETIQ), c(v2, TEXTO), c(null, TEXTO), c(null, TEXTO),
                c(e3, ETIQ), c(v3, TEXTO), c(null, TEXTO)), 18.0)
            h.combinadas += "B$r:D$r"; h.combinadas += "F$r:H$r"; h.combinadas += "J$r:K$r"
        }
        info("Nombre:", j.nombre, "Frente:", j.frente, "Tramo:", j.tramo)
        val turno = if (j.turno == "Noche") "Día [   ]    Noche [ X ]" else "Día [ X ]    Noche [   ]"
        info("Fecha:", "${T.fechaCorta(j.fecha)} (${T.fechaLarga(j.fecha)})", "Turno:", turno, "", "")
        h.fila(emptyList())

        // Encabezado de la tabla
        val titulos = listOf("Orden\nllegada", "Código mixer /\nremisión", "Hora\nllegada", "Hora inicio\ndescargue",
            "Hora fin\ndescargue", "Cant.\n(m³)", "Asentamiento\nen planta (\")", "Asentamiento\nen obra (\")",
            "Temperatura\n(°C)", "Localización", "Observación")
        h.fila(titulos.map { c(it, ENC) }, 32.0)
        val primeraFila = h.siguiente

        j.mixers.forEachIndexed { i, m ->
            h.fila(listOf(c((i + 1).toDouble(), NUM), c(m.codigo, TEXTO), hora(m.llegada), hora(m.inicio), hora(m.fin),
                numero(m.cant, DEC2), pulg(m.asPlanta), pulg(m.asObra), numero(m.temp, NUM),
                c(m.loc, TEXTO), c(m.obs, TEXTO)))
        }
        for (k in j.mixers.size until 10) {
            h.fila(listOf(c((k + 1).toDouble(), NUM)) + List(n - 1) { c(null, if (it == 0 || it >= 8) TEXTO else CENTRO) })
        }
        val ultimaFila = h.siguiente - 1

        // Total
        val r = resumen(j)
        val rt = h.siguiente
        h.fila(List(5) { c(if (it == 0) "TOTAL VACIADO" else null, TOT_ETIQ) } +
            listOf(c(Formula("SUM(F$primeraFila:F$ultimaFila)", r.vol), TOT_NUM)) +
            List(5) { c(if (it == 0) "${r.n} ${if (r.n == 1) "mixer" else "mixers"}" else null, TOT_ENT) }, 18.0)
        h.combinadas += "A$rt:E$rt"; h.combinadas += "G$rt:K$rt"
        h.fila(emptyList())

        // Resumen
        val rr = h.siguiente
        h.fila(List(6) { c(if (it == 0) "Resumen de la jornada" else null, ENC) }, 18.0)
        h.combinadas += "A$rr:F$rr"
        filasResumen(r).forEach { (k, v) ->
            val f = h.siguiente
            h.fila(listOf(c(k, ETIQ), c(null, ETIQ), c(null, ETIQ), c(null, ETIQ), c(v, TEXTO), c(null, TEXTO)))
            h.combinadas += "A$f:D$f"; h.combinadas += "E$f:F$f"
        }
        h.fila(emptyList()); h.fila(emptyList())
        h.fila(listOf(c("Elaboró: ${j.nombre}", NORMAL)) + List(6) { c(null, NORMAL) } + listOf(c("Revisó / Vo. Bo.", NORMAL)))

        val base = "Concreto_${j.fecha}_${j.turno}" + if (j.tramo.isNotBlank()) "_Tramo-${j.tramo}" else ""
        return escribir(ctx, nombreArchivo(base), listOf(h))
    }

    // ================= Historial completo =================

    fun historial(ctx: Context, jornadas: List<Jornada>): File {
        val orden = jornadas.sortedWith(compareBy({ it.fecha }, { it.turno }, { it.creada }))

        // Hoja 1: todos los mixers
        val reg = Hoja("Registros", listOf(11.0, 8.0, 16.0, 9.0, 7.0, 17.0, 9.0, 11.0, 10.0, 9.0, 12.0, 12.0, 11.0, 26.0, 32.0))
        reg.congelar = 1
        reg.fila(listOf("Fecha", "Turno", "Frente", "Tramo", "Orden", "Código mixer / remisión", "Hora llegada",
            "Hora inicio descargue", "Hora fin descargue", "Cant. (m³)", "Asent. planta (\")", "Asent. obra (\")",
            "Temp. (°C)", "Localización", "Observación").map { c(it, ENC) }, 32.0)
        orden.forEach { j ->
            j.mixers.forEachIndexed { i, m ->
                reg.fila(listOf(c(T.serialExcel(j.fecha), FECHA), c(j.turno, CENTRO), c(j.frente, TEXTO), c(j.tramo, CENTRO),
                    c((i + 1).toDouble(), NUM), c(m.codigo, TEXTO), hora(m.llegada), hora(m.inicio), hora(m.fin),
                    numero(m.cant, DEC2), pulg(m.asPlanta), pulg(m.asObra), numero(m.temp, NUM),
                    c(m.loc, TEXTO), c(m.obs, TEXTO)))
            }
        }
        reg.filtro = "A1:O${maxOf(2, reg.filas.size)}"

        // Hoja 2: resumen por jornada
        val res = Hoja("Por jornada", listOf(11.0, 8.0, 16.0, 9.0, 9.0, 12.0, 11.0, 11.0, 13.0, 13.0, 13.0, 12.0))
        res.congelar = 1
        res.fila(listOf("Fecha", "Turno", "Frente", "Tramo", "Mixers", "Volumen (m³)", "Primera llegada",
            "Último fin descargue", "Duración vaciado (min)", "Descargue prom. (min)", "Asent. obra prom. (\")",
            "Temp. prom. (°C)").map { c(it, ENC) }, 32.0)
        var totMix = 0.0; var totVol = 0.0
        orden.forEach { j ->
            val r = resumen(j)
            totMix += r.n; totVol += r.vol
            res.fila(listOf(c(T.serialExcel(j.fecha), FECHA), c(j.turno, CENTRO), c(j.frente, TEXTO), c(j.tramo, CENTRO),
                c(r.n.toDouble(), NUM), c(r.vol, DEC2), hora(r.primera), hora(r.ultimo),
                c(r.duracion?.toDouble(), NUM), c(r.descargue?.let { Math.round(it).toDouble() }, NUM),
                c(r.sProm?.let { Math.round(it * 8) / 8.0 }, FRAC), c(r.tProm?.let { Math.round(it * 10) / 10.0 }, NUM)))
        }
        val ult = res.filas.size
        res.fila(List(4) { c(if (it == 0) "TOTAL" else null, TOT_ETIQ) } +
            listOf(c(Formula("SUM(E2:E$ult)", totMix), TOT_NUM), c(Formula("SUM(F2:F$ult)", totVol), TOT_NUM)))
        val rt = res.filas.size
        res.combinadas += "A$rt:D$rt"
        res.filtro = "A1:L${maxOf(2, ult)}"

        return escribir(ctx, nombreArchivo("Historial_concreto_${T.hoy()}"), listOf(reg, res))
    }

    // ================= Escritura del archivo =================

    private fun nombreArchivo(base: String) = Normalizer.normalize(base, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^A-Za-z0-9_\\-]+"), "_") + ".xlsx"

    private fun hojaXml(h: Hoja): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        if (h.congelar > 0) {
            sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${h.congelar}" topLeftCell="A${h.congelar + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        } else {
            sb.append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>""")
        }
        sb.append("<cols>")
        h.anchos.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sb.append("</cols><sheetData>")
        h.filas.forEachIndexed { ri, (alto, celdas) ->
            val r = ri + 1
            sb.append("<row r=\"$r\"")
            if (alto != null) sb.append(" ht=\"$alto\" customHeight=\"1\"")
            sb.append(">")
            celdas.forEachIndexed { ci, cel ->
                val ref = col(ci) + r
                when (val v = cel.valor) {
                    null -> sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"/>")
                    is Formula -> sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"><f>${esc(v.expr)}</f><v>${v.valor}</v></c>")
                    is Number -> sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"><v>${v.toDouble()}</v></c>")
                    else -> {
                        val s = v.toString()
                        if (s.isEmpty()) sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"/>")
                        else sb.append("<c r=\"$ref\" s=\"${cel.estilo}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(s)}</t></is></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        h.filtro?.let { sb.append("<autoFilter ref=\"$it\"/>") }
        if (h.combinadas.isNotEmpty()) {
            sb.append("<mergeCells count=\"${h.combinadas.size}\">")
            h.combinadas.forEach { sb.append("<mergeCell ref=\"$it\"/>") }
            sb.append("</mergeCells>")
        }
        sb.append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup paperSize="1" orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private const val ESTILOS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="# ?/?"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/></numFmts>
<fonts count="5">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><color rgb="FF002F5C"/><name val="Calibri"/></font>
<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FF002F5C"/><name val="Calibri"/></font>
<font><sz val="10"/><color rgb="FF38628E"/><name val="Calibri"/></font>
</fonts>
<fills count="4">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF002F5C"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDEE8F6"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FF46505C"/></left><right style="thin"><color rgb="FF46505C"/></right><top style="thin"><color rgb="FF46505C"/></top><bottom style="thin"><color rgb="FF46505C"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="15">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="2" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="2" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="20" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun escribir(ctx: Context, nombre: String, hojas: List<Hoja>): File {
        val dir = File(ctx.filesDir, "exportes").apply { mkdirs() }
        val f = File(dir, nombre)
        ZipOutputStream(FileOutputStream(f)).use { z ->
            fun put(ruta: String, contenido: String) {
                z.putNextEntry(ZipEntry(ruta)); z.write(contenido.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            put("[Content_Types].xml", xml +
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                """<Default Extension="xml" ContentType="application/xml"/>""" +
                """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                hojas.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
                "</Types>")
            put("_rels/.rels", xml +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                "</Relationships>")
            val definidos = hojas.mapIndexedNotNull { i, h ->
                h.filtro?.let { ref ->
                    val partes = ref.split(":")
                    val abs = partes.joinToString(":") { p -> "$" + p.takeWhile { it.isLetter() } + "$" + p.dropWhile { it.isLetter() } }
                    """<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(h.nombre)}'!$abs</definedName>"""
                }
            }
            put("xl/workbook.xml", xml +
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                hojas.mapIndexed { i, h -> """<sheet name="${esc(h.nombre)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                "</sheets>" +
                (if (definidos.isNotEmpty()) "<definedNames>" + definidos.joinToString("") + "</definedNames>" else "") +
                """<calcPr calcId="0" fullCalcOnLoad="1"/></workbook>""")
            put("xl/_rels/workbook.xml.rels", xml +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                hojas.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${hojas.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                "</Relationships>")
            put("xl/styles.xml", ESTILOS)
            hojas.forEachIndexed { i, h -> put("xl/worksheets/sheet${i + 1}.xml", hojaXml(h)) }
        }
        return f
    }
}
