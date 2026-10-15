@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.controlconcreto

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

// ---------- piezas comunes ----------

@Composable
private fun DialogoCompleto(
    titulo: String,
    onCerrar: () -> Unit,
    onGuardar: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar sin guardar") } },
                    title = { Text(titulo, fontWeight = FontWeight.Bold) },
                    actions = { TextButton(onClick = onGuardar) { Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AzulOscuro, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                    )
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    content = contenido
                )
            }
        }
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(texto, color = AzulMedio, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    HorizontalDivider(color = AzulClaro)
    Spacer(Modifier.height(10.dp))
}

/** Campo de solo lectura que abre un selector (fecha u hora) al tocarlo. */
@Composable
private fun CampoSelector(label: String, valor: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = valor, onValueChange = {}, label = { Text(label) },
            readOnly = true, enabled = false, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun CampoHora(label: String, valor: String, onCambio: (String) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CampoSelector(
            label = label, valor = valor,
            onClick = {
                val (h, m) = T.partes(valor)
                TimePickerDialog(ctx, { _, hh, mm -> onCambio("%02d:%02d".format(hh, mm)) }, h, m, true).show()
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = { onCambio(T.ahora()) }, modifier = Modifier.height(56.dp)) { Text("Ahora") }
        if (valor.isNotEmpty()) IconButton(onClick = { onCambio("") }) { Icon(Icons.Default.Clear, contentDescription = "Borrar $label") }
    }
}

/** Campo que solo deja ELEGIR de una lista (no se puede escribir). Trae buscador si la lista es larga. */
@Composable
private fun CampoLista(
    label: String,
    valor: String,
    opciones: List<String>,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    var abierto by remember { mutableStateOf(false) }
    CampoSelector(label = label, valor = valor, onClick = { if (habilitado) abierto = true }, modifier = modifier)
    if (abierto) {
        var filtro by remember { mutableStateOf("") }
        val visibles = if (filtro.isBlank()) opciones else opciones.filter { it.contains(filtro.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(label) },
            text = {
                Column {
                    if (opciones.size > 8) OutlinedTextField(
                        value = filtro, onValueChange = { filtro = it }, label = { Text("Buscar") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(visibles) { op ->
                            val sel = op == valor
                            Text(
                                op,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) AzulOscuro else Color.Unspecified,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onElegir(op); abierto = false }
                                    .padding(vertical = 14.dp, horizontal = 4.dp)
                            )
                            HorizontalDivider()
                        }
                        if (visibles.isEmpty()) item {
                            Text("Sin resultados", color = Color(0xFF56636C), modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { abierto = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun CampoNumero(label: String, valor: String, onCambio: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** Asentamiento en pulgadas: número entero + botones de fracción (7 1/4, 6 1/2…). */
@Composable
private fun CampoAsentamiento(label: String, valor: String, onCambio: (String) -> Unit) {
    val entero = valor.trim().split(" ", "-").firstOrNull()?.takeIf { p -> p.all { it.isDigit() } } ?: ""
    val fracActual = valor.trim().substringAfter(" ", "").trim()
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        OutlinedTextField(
            value = valor, onValueChange = onCambio, label = { Text(label) }, singleLine = true,
            supportingText = { Text("Escribe el número entero y toca la fracción. Ej: 7 1/4") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("0", "1/8", "1/4", "3/8", "1/2", "5/8", "3/4", "7/8").forEach { f ->
                val sel = if (f == "0") valor.isNotBlank() && fracActual.isEmpty() else fracActual == f
                val texto = if (f == "0") "Exacto" else f
                if (sel) Button(
                    onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro),
                    contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(40.dp)
                ) { Text(texto) }
                else OutlinedButton(
                    onClick = {
                        onCambio(
                            when {
                                f == "0" -> entero
                                entero.isEmpty() -> f
                                else -> "$entero $f"
                            }
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(40.dp)
                ) { Text(texto, color = AzulOscuro) }
            }
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
    ) { Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
private fun BotonEliminar(texto: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp).height(50.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) { Text(texto) }
}

// ---------- formulario de jornada ----------

@Composable
fun FormJornada(
    j: Jornada?,
    ultimo: Ultimo,
    cfg: Config,
    catalogo: List<ItemCatalogo>,
    hayElementos: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (Jornada) -> Unit,
    onEliminar: () -> Unit
) {
    val ctx = LocalContext.current
    val hayCatalogo = catalogo.isNotEmpty() || hayElementos
    val frentesCat = remember(catalogo) { Catalogo.frentes(catalogo) }
    // Nueva jornada con catálogo cargado: arranca en "Lista cargada". Jornadas viejas conservan su modo.
    var usarLista by rememberSaveable { mutableStateOf(hayCatalogo && (j?.usaCatalogo ?: true)) }
    var nombre by rememberSaveable { mutableStateOf(j?.nombre ?: ultimo.nombre) }
    var frente by rememberSaveable {
        mutableStateOf(
            j?.frente ?: if (usarLista && frentesCat.isNotEmpty() && ultimo.frente !in frentesCat) "" else ultimo.frente
        )
    }
    var tramo by rememberSaveable {
        mutableStateOf(
            j?.tramo ?: if (usarLista && frentesCat.isNotEmpty() && ultimo.tramo !in Catalogo.tramos(catalogo, frente)) "" else ultimo.tramo
        )
    }
    var fecha by rememberSaveable { mutableStateOf(j?.fecha ?: T.hoy()) }
    var turno by rememberSaveable { mutableStateOf(j?.turno ?: T.turnoActual()) }
    var errLista by remember { mutableStateOf<String?>(null) }
    val tramosCat = remember(catalogo, frente) { Catalogo.tramos(catalogo, frente) }
    // Frente y tramo de la lista solo si el catálogo trae frentes (puede traer solo elementos)
    val listaFrentes = usarLista && frentesCat.isNotEmpty()

    fun guardar() {
        if (listaFrentes) {
            if (frente !in frentesCat) { errLista = "Elige el ${cfg.etFrente.lowercase()} de la lista"; return }
            if (tramosCat.isNotEmpty() && tramo !in tramosCat) { errLista = "Elige el ${cfg.etTramo.lowercase()} de la lista"; return }
        }
        onGuardar(
            Jornada(
                id = j?.id ?: T.uid(), fecha = fecha, turno = turno,
                nombre = nombre.trim(), frente = frente.trim(), tramo = tramo.trim(),
                creada = j?.creada ?: T.marcaTiempo(), mixers = j?.mixers ?: emptyList(),
                usaCatalogo = usarLista
            )
        )
    }

    DialogoCompleto(if (j == null) "Nueva jornada" else "Datos de la jornada", onCerrar, ::guardar) {
        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )

        if (hayCatalogo) {
            Text("${cfg.etFrente} y ${cfg.etTramo.lowercase()}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                listOf(true to "De la lista", false to "Otro / no predefinido").forEach { (opcion, texto) ->
                    val elegir = {
                        if (opcion && !usarLista && frentesCat.isNotEmpty()) {
                            if (frente !in frentesCat) { frente = ""; tramo = "" }
                            else if (tramo !in Catalogo.tramos(catalogo, frente)) tramo = ""
                        }
                        usarLista = opcion; errLista = null
                    }
                    if (opcion == usarLista) Button(
                        onClick = elegir, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text(texto) }
                    else OutlinedButton(onClick = elegir, modifier = Modifier.weight(1f).height(48.dp)) { Text(texto, color = AzulOscuro) }
                }
            }
        }

        if (listaFrentes) {
            CampoLista(
                label = cfg.etFrente, valor = frente, opciones = frentesCat,
                onElegir = { nuevo ->
                    if (nuevo != frente) {
                        frente = nuevo
                        val ts = Catalogo.tramos(catalogo, nuevo)
                        tramo = if (ts.size == 1) ts[0] else ""
                    }
                    errLista = null
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
            CampoLista(
                label = if (frente.isNotBlank() && tramosCat.isEmpty()) "${cfg.etTramo} (sin ${cfg.etTramo.lowercase()}s cargados)" else cfg.etTramo,
                valor = tramo, opciones = tramosCat,
                onElegir = { tramo = it; errLista = null },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                habilitado = tramosCat.isNotEmpty()
            )
            errLista?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        } else {
            OutlinedTextField(
                value = frente, onValueChange = { frente = it },
                label = { Text(if (usarLista) cfg.etFrente else "${cfg.etFrente} o actividad") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            CampoSelector(
                label = "Fecha", valor = T.fechaCorta(fecha),
                onClick = {
                    val d = runCatching { LocalDate.parse(fecha) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(ctx, { _, y, m, dd -> fecha = LocalDate.of(y, m + 1, dd).toString() },
                        d.year, d.monthValue - 1, d.dayOfMonth).show()
                },
                modifier = Modifier.weight(1f)
            )
            if (!listaFrentes) OutlinedTextField(
                value = tramo, onValueChange = { tramo = it }, label = { Text(cfg.etTramo) }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Text("Turno", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Día", "Noche").forEach { t ->
                if (t == turno) Button(
                    onClick = { turno = t }, modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                ) { Text(t) }
                else OutlinedButton(onClick = { turno = t }, modifier = Modifier.weight(1f).height(50.dp)) { Text(t, color = AzulOscuro) }
            }
        }
        BotonPrincipal(if (j == null) "Crear jornada" else "Guardar cambios", ::guardar)
        if (j != null) BotonEliminar("Eliminar jornada completa", onEliminar)
    }
}

// ---------- formulario de mixer ----------

/** Botones para elegir una opción (toque de nuevo para quitarla). Incluye "Otro..." si se pide. */
@Composable
private fun BotonesOpcion(opciones: List<String>, elegido: String, conOtro: Boolean, onElegir: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (if (conOtro) opciones + Elementos.OTRO else opciones).forEach { e ->
            if (e == elegido) Button(
                onClick = { onElegir("") }, contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
            ) { Text(e) }
            else OutlinedButton(onClick = { onElegir(e) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(e, color = AzulOscuro)
            }
        }
    }
}

@Composable
fun FormMixer(
    j: Jornada,
    m: Mixer?,
    cfg: Config,
    sugerencias: List<String>,
    localizaciones: List<String>,
    onCerrar: () -> Unit,
    onGuardar: (Mixer) -> Unit,
    onEliminar: () -> Unit
) {
    val anterior = j.mixers.lastOrNull()
    val codInicial = remember { Codigos.separar(m?.codigo ?: "") }
    var codMixer by rememberSaveable { mutableStateOf(codInicial.first) }
    var remision by rememberSaveable { mutableStateOf(codInicial.second) }
    var llegada by rememberSaveable { mutableStateOf(m?.llegada ?: T.ahora()) }
    var inicio by rememberSaveable { mutableStateOf(m?.inicio ?: "") }
    var fin by rememberSaveable { mutableStateOf(m?.fin ?: "") }
    var cant by rememberSaveable { mutableStateOf(m?.cant ?: "") }
    var temp by rememberSaveable { mutableStateOf(m?.temp ?: "") }
    var asP by rememberSaveable { mutableStateOf(m?.asPlanta ?: "") }
    var asO by rememberSaveable { mutableStateOf(m?.asObra ?: "") }

    // Localización: una de la lista cargada, "Otro..." con texto propio, o texto libre si no hay lista
    val locGuardada = (m?.loc ?: anterior?.loc ?: "").trim()
    val hayLista = localizaciones.isNotEmpty()
    var locElegida by rememberSaveable {
        mutableStateOf(
            when {
                !hayLista || locGuardada.isEmpty() -> ""
                locGuardada in localizaciones -> locGuardada
                else -> Elementos.OTRO
            }
        )
    }
    var locTexto by rememberSaveable {
        mutableStateOf(if (!hayLista || locGuardada !in localizaciones) locGuardada else "")
    }
    var obs by rememberSaveable { mutableStateOf(m?.obs ?: "") }

    // Campos adicionales: un mixer nuevo arranca con los valores del anterior (aditivo, fibra, etc.)
    val extrasIniciales = m?.extras ?: anterior?.extras ?: emptyMap()
    val extras = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(extrasIniciales) } }
    val extrasOtro = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            cfg.campos.filter { it.tipo == TipoCampo.LISTA }.forEach { c ->
                val v = extrasIniciales[c.id] ?: ""
                if (v.isNotBlank() && v !in c.opciones) put(c.id, true)
            }
        }
    }

    var errCodigo by remember { mutableStateOf<String?>(null) }
    var errNumero by remember { mutableStateOf<String?>(null) }
    var errUbic by remember { mutableStateOf<String?>(null) }

    val numero = if (m == null) j.mixers.size + 1 else j.mixers.indexOfFirst { it.id == m.id } + 1

    val avisos = buildList {
        val l = T.toMin(llegada); val i = T.toMin(inicio); val f = T.toMin(fin)
        if (l != null && i != null && i < l) add("El inicio de descargue es anterior a la llegada.")
        if (i != null && f != null && f < i) add("El fin de descargue es anterior al inicio.")
    }

    fun guardar() {
        val cod = codMixer.trim()
        if (cod.isEmpty() && remision.isBlank()) { errCodigo = "Escribe el código del mixer o el número de remisión"; return }
        if (cod.isNotEmpty() && !cod.first().isLetter()) { errCodigo = "El código del mixer debe comenzar con letras (ej. MX-123)"; return }
        val malo = listOf("Cantidad" to cant, "Temperatura" to temp)
            .firstOrNull { it.second.isNotBlank() && T.num(it.second) == null }
        val maloAs = listOf("Asentamiento en planta" to asP, "Asentamiento en obra" to asO)
            .firstOrNull { it.second.isNotBlank() && T.pulgadas(it.second) == null }
        val maloExtra = cfg.campos.firstOrNull { c ->
            c.tipo == TipoCampo.NUMERO && (extras[c.id] ?: "").isNotBlank() && T.num(extras[c.id] ?: "") == null
        }
        if (malo != null) { errNumero = "${malo.first} debe ser un número"; return }
        if (maloAs != null) { errNumero = "${maloAs.first}: escríbelo como 7, 7 1/4 o 7.25"; return }
        if (maloExtra != null) { errNumero = "${maloExtra.nombre} debe ser un número"; return }
        if (hayLista && locElegida == Elementos.OTRO && locTexto.isBlank()) {
            errUbic = "Escribe la ${cfg.etLoc.lowercase()} (elegiste Otro...)"; return
        }
        val locFinal = if (hayLista && locElegida != Elementos.OTRO) locElegida else locTexto.trim()
        val extrasFinal = cfg.campos.mapNotNull { c ->
            val v = (extras[c.id] ?: "").trim()
            if (v.isEmpty()) null else c.id to (if (c.tipo == TipoCampo.NUMERO) T.limpiar(v) else v)
        }.toMap() + (m?.extras ?: emptyMap()).filterKeys { k -> cfg.campos.none { it.id == k } }
        onGuardar(
            Mixer(
                id = m?.id ?: T.uid(), jornadaId = j.id, orden = m?.orden ?: 0,
                codigo = Codigos.juntar(cod, remision), llegada = llegada, inicio = inicio, fin = fin,
                cant = T.limpiar(cant), asPlanta = T.normalizarPulg(asP), asObra = T.normalizarPulg(asO), temp = T.limpiar(temp),
                loc = locFinal, obs = obs.trim(), extras = extrasFinal
            )
        )
    }

    DialogoCompleto(if (m == null) "Mixer $numero" else "Editar mixer $numero", onCerrar, ::guardar) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = codMixer, onValueChange = { codMixer = it.uppercase(); errCodigo = null },
                label = { Text("Código mixer") }, placeholder = { Text("MX-123") },
                singleLine = true, isError = errCodigo != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = remision, onValueChange = { remision = Codigos.soloNumeros(it); errCodigo = null },
                label = { Text("N.° remisión") }, singleLine = true, isError = errCodigo != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        val vista = Codigos.juntar(codMixer, remision)
        Text(
            errCodigo ?: if (vista.isNotBlank()) "En el informe: $vista" else "El código va en mayúsculas; la remisión solo lleva números.",
            fontSize = 12.sp, color = if (errCodigo != null) MaterialTheme.colorScheme.error else AzulMedio,
            modifier = Modifier.padding(top = 4.dp)
        )

        Seccion("Horas")
        CampoHora("Hora de llegada", llegada) { llegada = it }
        CampoHora("Hora inicio descargue", inicio) { inicio = it }
        CampoHora("Hora fin descargue", fin) { fin = it }
        if (avisos.isNotEmpty()) {
            Surface(color = AvisoFondo, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    avisos.joinToString(" ") + " Si el turno cruzó la medianoche está bien; si no, revisa la hora.",
                    color = Aviso, fontSize = 14.sp, modifier = Modifier.padding(12.dp)
                )
            }
        }

        Seccion("Concreto")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            CampoNumero("Cantidad (m³)", cant, { cant = it; errNumero = null }, Modifier.weight(1f))
            CampoNumero("Temperatura (°C)", temp, { temp = it; errNumero = null }, Modifier.weight(1f))
        }
        CampoAsentamiento("Asentamiento en planta (\")", asP) { asP = it; errNumero = null }
        CampoAsentamiento("Asentamiento en obra (\")", asO) { asO = it; errNumero = null }

        if (cfg.campos.isNotEmpty()) {
            Seccion("Datos adicionales")
            cfg.campos.forEach { c ->
                when (c.tipo) {
                    TipoCampo.NUMERO -> CampoNumero(
                        c.titulo, extras[c.id] ?: "", { extras[c.id] = it; errNumero = null },
                        Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    TipoCampo.TEXTO -> OutlinedTextField(
                        value = extras[c.id] ?: "", onValueChange = { extras[c.id] = it },
                        label = { Text(c.titulo) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    TipoCampo.LISTA -> {
                        val otro = extrasOtro[c.id] == true
                        Text(c.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        BotonesOpcion(c.opciones, if (otro) Elementos.OTRO else (extras[c.id] ?: ""), true) { op ->
                            when (op) {
                                Elementos.OTRO -> { extrasOtro[c.id] = true; extras[c.id] = "" }
                                else -> { extrasOtro[c.id] = false; extras[c.id] = op }
                            }
                        }
                        if (otro) OutlinedTextField(
                            value = extras[c.id] ?: "", onValueChange = { extras[c.id] = it },
                            label = { Text("Otro: ${c.nombre.lowercase()}") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        errNumero?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

        Seccion("Ubicación")
        if (hayLista) {
            Text(cfg.etLoc, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            BotonesOpcion(localizaciones, locElegida, true) { op ->
                locElegida = op
                if (op == Elementos.OTRO && locTexto in localizaciones) locTexto = ""
                errUbic = null
            }
            if (locElegida == Elementos.OTRO) OutlinedTextField(
                value = locTexto, onValueChange = { locTexto = it; errUbic = null },
                label = { Text("Otra ${cfg.etLoc.lowercase()} o actividad") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                "Lista del catálogo cargado. Toca de nuevo para quitarla.",
                fontSize = 12.sp, color = Color(0xFF56636C), modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            OutlinedTextField(
                value = locTexto, onValueChange = { locTexto = it },
                label = { Text(cfg.etLoc) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            val opciones = sugerencias.filter { it != locTexto && (locTexto.isBlank() || it.contains(locTexto, ignoreCase = true)) }
            if (opciones.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    opciones.forEach { s -> SuggestionChip(onClick = { locTexto = s }, label = { Text(s) }) }
                }
            }
        }
        errUbic?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        OutlinedTextField(
            value = obs, onValueChange = { obs = it }, label = { Text("Observación") },
            placeholder = { Text("Si la dejas vacía, el informe dirá \"Sin observación\"") }, minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )

        BotonPrincipal(if (m == null) "Guardar mixer" else "Guardar cambios", ::guardar)
        if (m != null) BotonEliminar("Eliminar este mixer", onEliminar)
        else Spacer(Modifier.height(24.dp))
    }
}
