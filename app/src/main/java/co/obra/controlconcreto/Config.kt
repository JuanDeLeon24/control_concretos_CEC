package co.obra.controlconcreto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class TipoCampo(val nombre: String) { NUMERO("Número"), TEXTO("Texto"), LISTA("Lista de opciones") }

/** Campo adicional que se registra en cada mixer (aditivo, fibra, dosis, resistencia, etc.). */
data class CampoExtra(
    val id: String,
    val nombre: String,
    val tipo: TipoCampo,
    val unidad: String = "",
    val opciones: List<String> = emptyList(),
    val estadistica: Boolean = true
) {
    val titulo: String get() = if (unidad.isNotBlank()) "$nombre ($unidad)" else nombre
}

/** Configuración de la app y del informe. Se guarda en el teléfono. */
data class Config(
    val empresa: String = "Construcciones El Cóndor S.A.",
    val proyecto: String = "",
    val titulo: String = "CONTROL LLEGADAS DE CONCRETO A OBRA",
    val codigoFormato: String = "PS-TUNEL 011",
    val area: String = "Revestimiento del túnel",
    val etFrente: String = "Frente",
    val etTramo: String = "Tramo",
    val etLoc: String = "Localización",
    val asentMin: String = "",
    val asentMax: String = "",
    val tempMin: String = "",
    val tempMax: String = "",
    val esperaMax: Int = 30,
    val campos: List<CampoExtra> = emptyList()
)

object Ajustes {
    private const val CLAVE = "config"

    /** Campos que se pueden agregar con un toque desde la configuración. */
    val sugeridos: List<CampoExtra> = listOf(
        CampoExtra("sug_fc", "Resistencia de diseño f'c", TipoCampo.NUMERO, "MPa"),
        CampoExtra("sug_tipo", "Tipo de concreto", TipoCampo.LISTA, "", listOf("Convencional", "Lanzado", "Autocompactante", "Fluido", "Masivo")),
        CampoExtra("sug_aditivo", "Aditivo", TipoCampo.TEXTO),
        CampoExtra("sug_dosis_ad", "Dosis de aditivo", TipoCampo.NUMERO, "L/m³"),
        CampoExtra("sug_fibra", "Fibra", TipoCampo.LISTA, "", listOf("Metálica", "Sintética", "Sin fibra")),
        CampoExtra("sug_dosis_fib", "Dosis de fibra", TipoCampo.NUMERO, "kg/m³"),
        CampoExtra("sug_agua", "Agua adicionada en obra", TipoCampo.NUMERO, "L"),
        CampoExtra("sug_cilindros", "Cilindros tomados", TipoCampo.NUMERO, "und"),
        CampoExtra("sug_bomba", "Equipo de colocación", TipoCampo.LISTA, "", listOf("Bomba estacionaria", "Autobomba", "Canal", "Balde / grúa"))
    )

    fun cargar(ctx: Context): Config {
        val txt = ctx.getSharedPreferences("preferencias", Context.MODE_PRIVATE).getString(CLAVE, null) ?: return Config()
        return runCatching { desdeJson(JSONObject(txt)) }.getOrDefault(Config())
    }

    fun guardar(ctx: Context, c: Config) {
        ctx.getSharedPreferences("preferencias", Context.MODE_PRIVATE).edit().putString(CLAVE, aJson(c).toString()).apply()
    }

    fun aJson(c: Config): JSONObject {
        val arr = JSONArray()
        c.campos.forEach { f ->
            arr.put(
                JSONObject().put("id", f.id).put("nombre", f.nombre).put("tipo", f.tipo.name)
                    .put("unidad", f.unidad).put("opciones", JSONArray(f.opciones)).put("estadistica", f.estadistica)
            )
        }
        return JSONObject().put("empresa", c.empresa).put("proyecto", c.proyecto).put("titulo", c.titulo)
            .put("codigoFormato", c.codigoFormato).put("area", c.area)
            .put("etFrente", c.etFrente).put("etTramo", c.etTramo).put("etLoc", c.etLoc)
            .put("asentMin", c.asentMin).put("asentMax", c.asentMax)
            .put("tempMin", c.tempMin).put("tempMax", c.tempMax).put("esperaMax", c.esperaMax)
            .put("campos", arr)
    }

    fun desdeJson(o: JSONObject): Config {
        val d = Config()
        fun s(k: String, def: String) = if (o.has(k) && !o.isNull(k)) o.optString(k, def) else def
        val arr = o.optJSONArray("campos") ?: JSONArray()
        val campos = (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i) ?: return@mapNotNull null
            val ops = f.optJSONArray("opciones") ?: JSONArray()
            CampoExtra(
                id = f.optString("id").ifBlank { T.uid() },
                nombre = f.optString("nombre"),
                tipo = runCatching { TipoCampo.valueOf(f.optString("tipo")) }.getOrDefault(TipoCampo.TEXTO),
                unidad = f.optString("unidad"),
                opciones = (0 until ops.length()).map { ops.optString(it) }.filter { it.isNotBlank() },
                estadistica = f.optBoolean("estadistica", true)
            )
        }.filter { it.nombre.isNotBlank() }
        return Config(
            empresa = s("empresa", d.empresa), proyecto = s("proyecto", d.proyecto), titulo = s("titulo", d.titulo),
            codigoFormato = s("codigoFormato", d.codigoFormato), area = s("area", d.area),
            etFrente = s("etFrente", d.etFrente).ifBlank { d.etFrente },
            etTramo = s("etTramo", d.etTramo).ifBlank { d.etTramo },
            etLoc = s("etLoc", d.etLoc).ifBlank { d.etLoc },
            asentMin = s("asentMin", ""), asentMax = s("asentMax", ""),
            tempMin = s("tempMin", ""), tempMax = s("tempMax", ""),
            esperaMax = o.optInt("esperaMax", d.esperaMax).coerceIn(1, 600),
            campos = campos
        )
    }
}
