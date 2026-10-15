package co.obra.controlconcreto

data class Jornada(
    val id: String,
    val fecha: String,      // yyyy-MM-dd
    val turno: String,      // "Día" | "Noche"
    val nombre: String,
    val frente: String,
    val tramo: String,
    val creada: String,
    val mixers: List<Mixer> = emptyList(),
    val usaCatalogo: Boolean = false   // true = frente/tramo elegidos del catálogo cargado
)

data class Mixer(
    val id: String,
    val jornadaId: String,
    val orden: Int,
    val codigo: String,     // "MX-123 / 456789" (código del mixer / número de remisión)
    val llegada: String,    // HH:mm
    val inicio: String,
    val fin: String,
    val cant: String,       // m³
    val asPlanta: String,   // pulgadas
    val asObra: String,
    val temp: String,       // °C
    val loc: String,
    val obs: String,
    val extras: Map<String, String> = emptyMap()   // campos adicionales configurables (id del campo -> valor)
)

data class ItemCatalogo(
    val frente: String,
    val tramo: String,
    val abscisa: String = "",
    val modulo: String = ""
)

/** Código del mixer y número de remisión: "mx123" + "456" -> "MX-123 / 456". */
object Codigos {
    const val SEP = " / "

    /** Pone el texto en mayúsculas y deja un guion entre las letras iniciales y el número. */
    fun mixer(s: String): String {
        val t = s.trim().uppercase().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return ""
        val m = Regex("^(\\p{L}+)[^\\p{L}\\d]*(\\d+)$").matchEntire(t) ?: return t
        return m.groupValues[1] + "-" + m.groupValues[2]
    }

    fun soloNumeros(s: String): String = s.filter { it.isDigit() }

    fun juntar(mixer: String, remision: String): String {
        val a = mixer(mixer); val b = soloNumeros(remision)
        return when {
            b.isEmpty() -> a
            a.isEmpty() -> "–$SEP$b"
            else -> "$a$SEP$b"
        }
    }

    /** Separa lo guardado en (código mixer, remisión). Registros antiguos quedan completos en el código. */
    fun separar(s: String): Pair<String, String> {
        val i = s.lastIndexOf(SEP)
        if (i >= 0) {
            val rem = s.substring(i + SEP.length).trim()
            if (rem.isNotEmpty() && rem.all { it.isDigit() }) {
                val cod = s.substring(0, i).trim()
                return (if (cod == "–") "" else cod) to rem
            }
        }
        return s.trim() to ""
    }
}

data class Resumen(
    val n: Int,
    val vol: Double,
    val volProm: Double?, val volMin: Double?, val volMax: Double?,
    val espera: Double?, val esperaMax: Int?, val esperasLargas: Int,
    val descargue: Double?, val descargueMax: Int?,
    val ciclo: Double?,
    val intervalo: Double?,
    val rendimiento: Double?,
    val caudal: Double?,
    val tProm: Double?, val tMin: Double?, val tMax: Double?,
    val sProm: Double?, val sMin: Double?, val sMax: Double?,
    val pProm: Double?, val pMin: Double?, val pMax: Double?,
    val perdida: Double?,
    val fueraAsent: Int?, val fueraTemp: Int?,
    val sinFin: Int,
    val primera: String,
    val ultimo: String,
    val duracion: Int?,
    val porLoc: List<Pair<String, Double>>,
    val extras: List<Pair<String, String>>
)

private fun <N : Number> List<N>.prom(): Double? = if (isEmpty()) null else sumOf { it.toDouble() } / size

fun resumen(j: Jornada, cfg: Config = Config()): Resumen {
    val m = j.mixers
    val cants = m.mapNotNull { T.num(it.cant) }
    val esp = m.mapNotNull { T.diff(it.llegada, it.inicio) }
    val des = m.mapNotNull { T.diff(it.inicio, it.fin) }
    val ciclos = m.mapNotNull { T.diff(it.llegada, it.fin) }
    val llegadas = m.map { it.llegada }.filter { it.isNotBlank() }
    val intervalos = llegadas.zipWithNext().mapNotNull { (a, b) -> T.diff(a, b) }
    val temps = m.mapNotNull { T.num(it.temp) }
    val slump = m.mapNotNull { T.pulgadas(it.asObra) }
    val planta = m.mapNotNull { T.pulgadas(it.asPlanta) }
    val perdidas = m.mapNotNull { x ->
        val p = T.pulgadas(x.asPlanta); val o = T.pulgadas(x.asObra)
        if (p != null && o != null) p - o else null
    }
    val conCaudal = m.mapNotNull { x ->
        val c = T.num(x.cant); val d = T.diff(x.inicio, x.fin)
        if (c != null && d != null && d > 0) c to d else null
    }
    val primera = m.firstOrNull { it.llegada.isNotBlank() }
    val ultima = m.lastOrNull { it.fin.isNotBlank() }
    val duracion = if (primera != null && ultima != null) T.diff(primera.llegada, ultima.fin) else null
    val vol = T.volumen(j)

    val asMin = T.pulgadas(cfg.asentMin); val asMax = T.pulgadas(cfg.asentMax)
    val tMinEsp = T.num(cfg.tempMin); val tMaxEsp = T.num(cfg.tempMax)
    val fueraAsent = if (asMin == null && asMax == null) null else
        slump.count { (asMin != null && it < asMin - 1e-9) || (asMax != null && it > asMax + 1e-9) }
    val fueraTemp = if (tMinEsp == null && tMaxEsp == null) null else
        temps.count { (tMinEsp != null && it < tMinEsp) || (tMaxEsp != null && it > tMaxEsp) }

    val porLoc = m.groupBy { it.loc.trim().ifBlank { "Sin ${cfg.etLoc.lowercase()}" } }
        .map { (k, v) -> k to v.sumOf { T.num(it.cant) ?: 0.0 } }
        .sortedByDescending { it.second }

    val extras = cfg.campos.filter { it.estadistica }.mapNotNull { campo ->
        val valores = m.mapNotNull { it.extras[campo.id]?.trim()?.takeIf { v -> v.isNotEmpty() } }
        if (valores.isEmpty()) return@mapNotNull null
        val u = if (campo.unidad.isNotBlank()) " ${campo.unidad}" else ""
        val txt = if (campo.tipo == TipoCampo.NUMERO) {
            val nums = valores.mapNotNull { T.num(it) }
            if (nums.isEmpty()) return@mapNotNull null
            "${T.fmt(nums.prom(), 2)}$u prom.  (${T.rango(nums.minOrNull(), nums.maxOrNull(), u)})  ·  ${nums.size} de ${m.size} mixers"
        } else {
            valores.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                .joinToString("  ·  ") { "${it.key}: ${it.value}" }
        }
        campo.nombre to txt
    }

    return Resumen(
        n = m.size, vol = vol,
        volProm = cants.prom(), volMin = cants.minOrNull(), volMax = cants.maxOrNull(),
        espera = esp.prom(), esperaMax = esp.maxOrNull(), esperasLargas = esp.count { it > cfg.esperaMax },
        descargue = des.prom(), descargueMax = des.maxOrNull(),
        ciclo = ciclos.prom(),
        intervalo = intervalos.prom(),
        rendimiento = if (duracion != null && duracion > 0 && vol > 0) vol / (duracion / 60.0) else null,
        caudal = if (conCaudal.isNotEmpty()) conCaudal.sumOf { it.first } / (conCaudal.sumOf { it.second } / 60.0) else null,
        tProm = temps.prom(), tMin = temps.minOrNull(), tMax = temps.maxOrNull(),
        sProm = slump.prom(), sMin = slump.minOrNull(), sMax = slump.maxOrNull(),
        pProm = planta.prom(), pMin = planta.minOrNull(), pMax = planta.maxOrNull(),
        perdida = perdidas.prom(),
        fueraAsent = fueraAsent, fueraTemp = fueraTemp,
        sinFin = m.count { it.fin.isBlank() },
        primera = primera?.llegada ?: "",
        ultimo = ultima?.fin ?: "",
        duracion = duracion,
        porLoc = porLoc,
        extras = extras
    )
}

private fun pulgProm(d: Double?): String = if (d == null) "–" else T.fraccion(d) + "\""

private fun pulgSigno(d: Double?): String = when {
    d == null -> "–"
    d < 0 -> "−" + T.fraccion(-d) + "\" (ganó asentamiento)"
    else -> T.fraccion(d) + "\""
}

/** Filas del resumen y estadísticas de la jornada (las usan el PDF, el Excel y la pantalla de resumen). */
fun filasResumen(r: Resumen, cfg: Config = Config()): List<Pair<String, String>> = buildList {
    add("Volumen total vaciado" to "${T.fmt(r.vol, 2)} m³")
    add("Mixers recibidos" to r.n.toString() + if (r.sinFin > 0) "  (${r.sinFin} sin hora de fin)" else "")
    add("Volumen por mixer (prom. / rango)" to
        (if (r.volProm != null) "${T.fmt(r.volProm, 2)} m³  (${T.rango(r.volMin, r.volMax, " m³")})" else "–"))
    add("Primera llegada / último fin de descargue" to "${r.primera.ifBlank { "–" }} / ${r.ultimo.ifBlank { "–" }}")
    add("Duración total del vaciado" to T.dur(r.duracion?.toDouble()))
    add("Rendimiento del vaciado" to (if (r.rendimiento != null) "${T.fmt(r.rendimiento, 2)} m³/h" else "–"))
    add("Intervalo promedio entre llegadas" to T.dur(r.intervalo))
    add("Espera en obra (prom. / máxima)" to "${T.dur(r.espera)} / ${T.dur(r.esperaMax?.toDouble())}")
    add("Mixers con espera mayor a ${cfg.esperaMax} min" to r.esperasLargas.toString())
    add("Descargue por mixer (prom. / máximo)" to "${T.dur(r.descargue)} / ${T.dur(r.descargueMax?.toDouble())}")
    add("Caudal promedio de descargue" to (if (r.caudal != null) "${T.fmt(r.caudal, 2)} m³/h" else "–"))
    add("Permanencia en obra (llegada a fin)" to T.dur(r.ciclo))
    if (r.pProm != null) add("Asentamiento en planta (prom. / rango)" to "${pulgProm(r.pProm)}  (${T.rangoPulg(r.pMin, r.pMax)})")
    add("Asentamiento en obra (prom. / rango)" to "${pulgProm(r.sProm)}  (${T.rangoPulg(r.sMin, r.sMax)})")
    if (r.perdida != null) add("Pérdida de asentamiento promedio (planta − obra)" to pulgSigno(r.perdida))
    r.fueraAsent?.let {
        val esp = listOf(cfg.asentMin, cfg.asentMax).filter { s -> s.isNotBlank() }.joinToString(" a ") + "\""
        add("Mixers fuera del asentamiento especificado ($esp)" to it.toString())
    }
    add("Temperatura (prom. / rango)" to
        "${if (r.tProm != null) T.fmt(r.tProm) + " °C" else "–"}  (${T.rango(r.tMin, r.tMax, " °C")})")
    r.fueraTemp?.let {
        val esp = listOfNotNull(
            cfg.tempMin.takeIf { s -> s.isNotBlank() }?.let { s -> "mín. $s" },
            cfg.tempMax.takeIf { s -> s.isNotBlank() }?.let { s -> "máx. $s" }
        ).joinToString(", ")
        add("Mixers fuera de la temperatura especificada ($esp °C)" to it.toString())
    }
    if (r.porLoc.isNotEmpty())
        add("Volumen por ${cfg.etLoc.lowercase()}" to r.porLoc.joinToString("  ·  ") { "${it.first}: ${T.fmt(it.second, 2)} m³" })
    r.extras.forEach { add(it) }
}
