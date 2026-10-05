package co.obra.controlconcreto

data class Jornada(
    val id: String,
    val fecha: String,      // yyyy-MM-dd
    val turno: String,      // "Día" | "Noche"
    val nombre: String,
    val frente: String,
    val tramo: String,
    val creada: String,
    val mixers: List<Mixer> = emptyList()
)

data class Mixer(
    val id: String,
    val jornadaId: String,
    val orden: Int,
    val codigo: String,
    val llegada: String,    // HH:mm
    val inicio: String,
    val fin: String,
    val cant: String,       // m³
    val asPlanta: String,   // pulgadas
    val asObra: String,
    val temp: String,       // °C
    val loc: String,
    val obs: String
)

data class Resumen(
    val n: Int,
    val vol: Double,
    val espera: Double?,
    val descargue: Double?,
    val tProm: Double?, val tMin: Double?, val tMax: Double?,
    val sProm: Double?, val sMin: Double?, val sMax: Double?,
    val primera: String,
    val ultimo: String,
    val duracion: Int?
)

private fun <N : Number> List<N>.prom(): Double? = if (isEmpty()) null else sumOf { it.toDouble() } / size

fun resumen(j: Jornada): Resumen {
    val m = j.mixers
    val esp = m.mapNotNull { T.diff(it.llegada, it.inicio) }
    val des = m.mapNotNull { T.diff(it.inicio, it.fin) }
    val temps = m.mapNotNull { T.num(it.temp) }
    val slump = m.mapNotNull { T.pulgadas(it.asObra) }
    val primera = m.firstOrNull { it.llegada.isNotBlank() }
    val ultima = m.lastOrNull { it.fin.isNotBlank() }
    return Resumen(
        n = m.size,
        vol = T.volumen(j),
        espera = esp.prom(),
        descargue = des.prom(),
        tProm = temps.prom(), tMin = temps.minOrNull(), tMax = temps.maxOrNull(),
        sProm = slump.prom(), sMin = slump.minOrNull(), sMax = slump.maxOrNull(),
        primera = primera?.llegada ?: "",
        ultimo = ultima?.fin ?: "",
        duracion = if (primera != null && ultima != null) T.diff(primera.llegada, ultima.fin) else null
    )
}

/** Filas del resumen de la jornada (las usan el PDF y el Excel). */
fun filasResumen(r: Resumen): List<Pair<String, String>> = listOf(
    "Volumen total vaciado" to "${T.fmt(r.vol, 2)} m³",
    "Mixers recibidos" to r.n.toString(),
    "Primera llegada / último fin de descargue" to "${r.primera.ifBlank { "–" }} / ${r.ultimo.ifBlank { "–" }}",
    "Duración total del vaciado" to T.dur(r.duracion?.toDouble()),
    "Espera promedio (llegada a inicio)" to T.dur(r.espera),
    "Descargue promedio por mixer" to T.dur(r.descargue),
    "Asentamiento en obra (prom. / rango)" to
        "${if (r.sProm != null) T.fraccion(r.sProm) + "\"" else "–"}  (${T.rangoPulg(r.sMin, r.sMax)})",
    "Temperatura (prom. / rango)" to
        "${if (r.tProm != null) T.fmt(r.tProm) + " °C" else "–"}  (${T.rango(r.tMin, r.tMax, " °C")})"
)
