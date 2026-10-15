package co.obra.controlconcreto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

/** Genera la planilla de control de concreto en PDF (carta horizontal) sin conexión. Textos según la configuración. */
object Pdf {
    private const val PW = 792          // carta horizontal en puntos
    private const val PH = 612
    private const val M = 28f
    private val AZUL = Color.rgb(0, 47, 92)
    private val CLARO = Color.rgb(222, 232, 246)
    private val LINEA = Color.rgb(70, 80, 92)
    private val TINTA = Color.rgb(20, 25, 30)

    private val NORMAL = Layout.Alignment.ALIGN_NORMAL
    private val CENTRO = Layout.Alignment.ALIGN_CENTER
    private val DERECHA = Layout.Alignment.ALIGN_OPPOSITE

    private val borde = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = LINEA }
    private val relleno = Paint().apply { style = Paint.Style.FILL }

    private fun tp(size: Float, bold: Boolean = false, color: Int = TINTA) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
        }

    private fun lay(t: String, p: TextPaint, w: Float, a: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, maxOf(1, w.toInt()))
            .setAlignment(a).setIncludePad(false).build()

    private fun alto(t: String, p: TextPaint, w: Float, pad: Float = 3f): Float =
        if (t.isEmpty()) 0f else lay(t, p, w - 2 * pad, NORMAL).height + 2 * pad

    private fun celda(
        c: Canvas, x: Float, y: Float, w: Float, h: Float, t: String, p: TextPaint,
        a: Layout.Alignment = NORMAL, fondo: Int? = null, pad: Float = 3f
    ) {
        if (fondo != null) { relleno.color = fondo; c.drawRect(x, y, x + w, y + h, relleno) }
        c.drawRect(x, y, x + w, y + h, borde)
        if (t.isNotEmpty()) {
            val l = lay(t, p, w - 2 * pad, a)
            c.save()
            c.clipRect(x, y, x + w, y + h)
            c.translate(x + pad, y + (h - l.height) / 2f)
            l.draw(c)
            c.restore()
        }
    }

    fun generar(ctx: Context, j: Jornada, logo: Bitmap?, cfg: Config): File {
        val doc = PdfDocument()
        val r = resumen(j, cfg)
        val generado = "Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        var numPag = 0
        var pagina: PdfDocument.Page? = null

        fun nueva(): Canvas {
            pagina?.let { doc.finishPage(it) }
            numPag++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, numPag).create())
            pagina = p
            val cv = p.canvas
            val f = tp(7f, color = Color.GRAY)
            cv.drawText(generado, M, PH - 12f, f)
            val t = "Página $numPag"
            cv.drawText(t, PW - M - f.measureText(t), PH - 12f, f)
            return cv
        }

        var c = nueva()
        val ancho = PW - 2 * M
        var y = M
        val limite = PH - M - 16f

        // ---------- Encabezado ----------
        val hEnc = 46f
        celda(c, M, y, 120f, hEnc, if (logo == null) cfg.empresa.uppercase() else "", tp(8f, true, AZUL), CENTRO)
        if (logo != null) {
            val s = minOf((120f - 12f) / logo.width, (hEnc - 10f) / logo.height)
            val w = logo.width * s; val h = logo.height * s
            val left = M + (120f - w) / 2f; val top = y + (hEnc - h) / 2f
            c.drawBitmap(logo, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        val tituloEnc = cfg.titulo + (if (cfg.proyecto.isNotBlank()) "\n" + cfg.proyecto else "")
        celda(c, M + 120f, y, ancho - 240f, hEnc, tituloEnc, tp(if (cfg.proyecto.isNotBlank()) 11.5f else 13f, true, AZUL), CENTRO)
        celda(c, M + ancho - 120f, y, 120f, hEnc, cfg.codigoFormato, tp(10f, true, AZUL), CENTRO)
        y += hEnc

        // ---------- Datos de la jornada ----------
        val lab = tp(8f, true, AZUL); val val8 = tp(8.5f)
        val wInfo = floatArrayOf(56f, 220f, 50f, 180f, 50f, 180f)
        fun filaInfo(textos: List<String>) {
            var x = M
            textos.forEachIndexed { i, t ->
                val esEtiqueta = i % 2 == 0
                celda(c, x, y, wInfo[i], 18f, t, if (esEtiqueta) lab else val8, fondo = if (esEtiqueta) CLARO else null)
                x += wInfo[i]
            }
            y += 18f
        }
        filaInfo(listOf("Nombre:", j.nombre, "${cfg.etFrente}:", j.frente, "${cfg.etTramo}:", j.tramo))
        val turno = if (j.turno == "Noche") "Día [   ]      Noche [ X ]" else "Día [ X ]      Noche [   ]"
        filaInfo(listOf("Fecha:", "${T.fechaCorta(j.fecha)}  (${T.fechaLarga(j.fecha)})", "Turno:", turno, "", ""))
        y += 8f

        // ---------- Tabla de mixers ----------
        val cols = floatArrayOf(38f, 78f, 50f, 56f, 52f, 40f, 58f, 58f, 54f, 120f, 132f)
        val titulos = listOf(
            "Orden\nllegada", "Código mixer /\nremisión", "Hora\nllegada", "Hora inicio\ndescargue",
            "Hora fin\ndescargue", "Cant.\n(m³)", "Asentamiento\nen planta (\")", "Asentamiento\nen obra (\")",
            "Temperatura\n(°C)", cfg.etLoc, "Observación"
        )
        val pTit = tp(7f, true, Color.WHITE)
        val pCel = tp(8f)
        val centradas = setOf(0, 2, 3, 4, 5, 6, 7, 8)

        fun encabezadoTabla() {
            var x = M
            titulos.forEachIndexed { i, t -> celda(c, x, y, cols[i], 30f, t, pTit, CENTRO, AZUL); x += cols[i] }
            y += 30f
        }
        encabezadoTabla()

        val filas = j.mixers.mapIndexed { i, m ->
            listOf((i + 1).toString(), m.codigo, m.llegada, m.inicio, m.fin, T.fmt(T.num(m.cant), 2),
                m.asPlanta, m.asObra, m.temp, m.loc, m.obs.ifBlank { "Sin observación" })
        }.toMutableList()
        while (filas.size < 10) filas.add(listOf((filas.size + 1).toString()) + List(10) { "" })

        for (f in filas) {
            val h = maxOf(18f, f.indices.maxOf { alto(f[it], pCel, cols[it]) })
            if (y + h > limite) { c = nueva(); y = M; encabezadoTabla() }
            var x = M
            f.forEachIndexed { i, t -> celda(c, x, y, cols[i], h, t, pCel, if (i in centradas) CENTRO else NORMAL); x += cols[i] }
            y += h
        }

        if (y + 18f > limite) { c = nueva(); y = M }
        val pTot = tp(8f, true, AZUL)
        val w5 = cols.take(5).sum()
        val wResto = cols.drop(6).sum()
        celda(c, M, y, w5, 18f, "TOTAL VACIADO", pTot, DERECHA, CLARO)
        celda(c, M + w5, y, cols[5], 18f, T.fmt(r.vol, 2), pTot, CENTRO, CLARO)
        celda(c, M + w5 + cols[5], y, wResto, 18f, "${r.n} ${if (r.n == 1) "mixer" else "mixers"}", pTot, NORMAL, CLARO)
        y += 18f + 14f

        // ---------- Datos adicionales configurables (tabla aparte, 8 campos por tabla) ----------
        if (cfg.campos.isNotEmpty() && j.mixers.isNotEmpty()) {
            cfg.campos.chunked(8).forEach { grupo ->
                val wOrd = 38f; val wCod = 100f
                val wCampo = (ancho - wOrd - wCod) / grupo.size
                val anchos = listOf(wOrd, wCod) + List(grupo.size) { wCampo }
                val tits = listOf("Orden", "Código mixer /\nremisión") + grupo.map { it.titulo }
                val hTit = maxOf(26f, tits.indices.maxOf { alto(tits[it], pTit, anchos[it]) })
                fun encabezadoExtra() {
                    if (y + 16f + hTit + 18f > limite) { c = nueva(); y = M }
                    celda(c, M, y, ancho, 16f, "Datos adicionales por mixer", tp(8.5f, true, Color.WHITE), NORMAL, AZUL)
                    y += 16f
                    var x = M
                    tits.forEachIndexed { i, t -> celda(c, x, y, anchos[i], hTit, t, pTit, CENTRO, AZUL); x += anchos[i] }
                    y += hTit
                }
                encabezadoExtra()
                j.mixers.forEachIndexed { i, m ->
                    val f = listOf((i + 1).toString(), m.codigo) + grupo.map { m.extras[it.id] ?: "" }
                    val h = maxOf(16f, f.indices.maxOf { alto(f[it], pCel, anchos[it]) })
                    if (y + h > limite) { c = nueva(); y = M; encabezadoExtra() }
                    var x = M
                    f.forEachIndexed { k, t -> celda(c, x, y, anchos[k], h, t, pCel, if (k == 1) NORMAL else CENTRO); x += anchos[k] }
                    y += h
                }
                y += 14f
            }
        }

        // ---------- Resumen, parámetros y estadísticas + firmas ----------
        val datos = filasResumen(r, cfg)
        val pK = tp(7.6f, true, AZUL); val pV = tp(7.8f)
        val wK = 230f; val wV = 330f
        val altos = datos.map { (k, v) -> maxOf(14f, alto(k, pK, wK), alto(v, pV, wV)) }
        if (y + 16f + altos.take(6).sum() > limite) { c = nueva(); y = M }
        celda(c, M, y, wK + wV, 16f, "Resumen, parámetros y estadísticas de la jornada", tp(8.5f, true, Color.WHITE), NORMAL, AZUL)
        var yy = y + 16f
        var yIni = y
        datos.forEachIndexed { i, (k, v) ->
            val h = altos[i]
            if (yy + h > limite) { c = nueva(); yy = M; yIni = M }
            celda(c, M, yy, wK, h, k, pK, NORMAL, CLARO)
            celda(c, M + wK, yy, wV, h, v, pV)
            yy += h
        }
        if (yy - yIni < 90f) { if (yIni + 100f > limite) { c = nueva(); yIni = M }; yy = yIni + 100f }

        val sx = M + wK + wV + 20f; val sw = ancho - wK - wV - 20f; val sy = yy - 10f
        val linea = Paint().apply { color = LINEA; strokeWidth = 0.8f }
        val pf = tp(8f)
        val sy1 = maxOf(sy - 60f, yIni + 30f)
        c.drawLine(sx, sy1, sx + sw, sy1, linea)
        c.drawText("Elaboró" + if (j.nombre.isNotBlank()) ": ${j.nombre}" else "", sx, sy1 + 12f, pf)
        c.drawLine(sx, sy, sx + sw, sy, linea)
        c.drawText("Revisó / Vo. Bo.", sx, sy + 12f, pf)

        // ---------- Anexo: fotos de las remisiones (2 por página) ----------
        val conFoto = j.mixers.mapIndexedNotNull { i, m -> if (Fotos.existe(ctx, m.id)) (i + 1) to m else null }
        conFoto.chunked(2).forEach { par ->
            c = nueva()
            c.drawText("ANEXO – REMISIONES DE PLANTA", M, M + 12f, tp(12f, true, AZUL))
            c.drawText("Jornada ${T.fechaCorta(j.fecha)}, turno ${j.turno.lowercase()}" +
                (if (j.tramo.isNotBlank()) ", ${cfg.etTramo.lowercase()} ${j.tramo}" else ""), M, M + 26f, tp(8.5f, color = LINEA))
            val anchoFoto = (ancho - 16f) / 2f
            val arriba = M + 38f
            val altoFoto = PH - M - 16f - arriba - 16f
            par.forEachIndexed { k, (num, m) ->
                val b = Fotos.cargar(ctx, m.id, 1400) ?: return@forEachIndexed
                val x0 = M + k * (anchoFoto + 16f)
                val esc = min(anchoFoto / b.width, altoFoto / b.height)
                val w = b.width * esc; val h = b.height * esc
                val left = x0 + (anchoFoto - w) / 2f
                c.drawBitmap(b, null, RectF(left, arriba, left + w, arriba + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                c.drawRect(left, arriba, left + w, arriba + h, borde)
                val pie = "Mixer $num  |  ${m.codigo}" + (if (m.llegada.isNotBlank()) "  |  llegada ${m.llegada}" else "")
                c.drawText(pie, left, arriba + h + 12f, tp(8.5f, true, AZUL))
                b.recycle()
            }
        }

        pagina?.let { doc.finishPage(it) }

        val dir = File(ctx.filesDir, "pdf").apply { mkdirs() }
        val base = "Concreto_${j.fecha}_${j.turno}" + if (j.tramo.isNotBlank()) "_Tramo-${j.tramo}" else ""
        val nombre = Normalizer.normalize(base, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^A-Za-z0-9_\\-]+"), "_") + ".pdf"
        val archivo = File(dir, nombre)
        FileOutputStream(archivo).use { doc.writeTo(it) }
        doc.close()
        return archivo
    }
}
