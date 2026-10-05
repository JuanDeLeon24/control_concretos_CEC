@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.controlconcreto

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
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

@Composable
private fun CampoNumero(label: String, valor: String, onCambio: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
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
    onCerrar: () -> Unit,
    onGuardar: (Jornada) -> Unit,
    onEliminar: () -> Unit
) {
    val ctx = LocalContext.current
    var nombre by rememberSaveable { mutableStateOf(j?.nombre ?: ultimo.nombre) }
    var frente by rememberSaveable { mutableStateOf(j?.frente ?: ultimo.frente) }
    var tramo by rememberSaveable { mutableStateOf(j?.tramo ?: ultimo.tramo) }
    var fecha by rememberSaveable { mutableStateOf(j?.fecha ?: T.hoy()) }
    var turno by rememberSaveable { mutableStateOf(j?.turno ?: T.turnoActual()) }

    fun guardar() {
        onGuardar(
            Jornada(
                id = j?.id ?: T.uid(), fecha = fecha, turno = turno,
                nombre = nombre.trim(), frente = frente.trim(), tramo = tramo.trim(),
                creada = j?.creada ?: T.marcaTiempo(), mixers = j?.mixers ?: emptyList()
            )
        )
    }

    DialogoCompleto(if (j == null) "Nueva jornada" else "Datos de la jornada", onCerrar, ::guardar) {
        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
        OutlinedTextField(
            value = frente, onValueChange = { frente = it }, label = { Text("Frente") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
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
            OutlinedTextField(
                value = tramo, onValueChange = { tramo = it }, label = { Text("Tramo") }, singleLine = true,
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

@Composable
fun FormMixer(
    j: Jornada,
    m: Mixer?,
    sugerencias: List<String>,
    onCerrar: () -> Unit,
    onGuardar: (Mixer) -> Unit,
    onEliminar: () -> Unit
) {
    var codigo by rememberSaveable { mutableStateOf(m?.codigo ?: "") }
    var llegada by rememberSaveable { mutableStateOf(m?.llegada ?: T.ahora()) }
    var inicio by rememberSaveable { mutableStateOf(m?.inicio ?: "") }
    var fin by rememberSaveable { mutableStateOf(m?.fin ?: "") }
    var cant by rememberSaveable { mutableStateOf(m?.cant ?: "") }
    var temp by rememberSaveable { mutableStateOf(m?.temp ?: "") }
    var asP by rememberSaveable { mutableStateOf(m?.asPlanta ?: "") }
    var asO by rememberSaveable { mutableStateOf(m?.asObra ?: "") }
    var loc by rememberSaveable { mutableStateOf(m?.loc ?: (j.mixers.lastOrNull()?.loc ?: "")) }
    var obs by rememberSaveable { mutableStateOf(m?.obs ?: "") }
    var errCodigo by remember { mutableStateOf(false) }
    var errNumero by remember { mutableStateOf<String?>(null) }

    val numero = if (m == null) j.mixers.size + 1 else j.mixers.indexOfFirst { it.id == m.id } + 1

    val avisos = buildList {
        val l = T.toMin(llegada); val i = T.toMin(inicio); val f = T.toMin(fin)
        if (l != null && i != null && i < l) add("El inicio de descargue es anterior a la llegada.")
        if (i != null && f != null && f < i) add("El fin de descargue es anterior al inicio.")
    }

    fun guardar() {
        val malo = listOf("Cantidad" to cant, "Temperatura" to temp, "Asentamiento en planta" to asP, "Asentamiento en obra" to asO)
            .firstOrNull { it.second.isNotBlank() && T.num(it.second) == null }
        if (codigo.isBlank()) { errCodigo = true; return }
        if (malo != null) { errNumero = "${malo.first} debe ser un número"; return }
        onGuardar(
            Mixer(
                id = m?.id ?: T.uid(), jornadaId = j.id, orden = m?.orden ?: 0,
                codigo = codigo.trim(), llegada = llegada, inicio = inicio, fin = fin,
                cant = T.limpiar(cant), asPlanta = T.limpiar(asP), asObra = T.limpiar(asO), temp = T.limpiar(temp),
                loc = loc.trim(), obs = obs.trim()
            )
        )
    }

    DialogoCompleto(if (m == null) "Mixer $numero" else "Editar mixer $numero", onCerrar, ::guardar) {
        OutlinedTextField(
            value = codigo, onValueChange = { codigo = it; errCodigo = false },
            label = { Text("Código mixer / remisión") }, singleLine = true, isError = errCodigo,
            supportingText = { if (errCodigo) Text("Escribe el código del mixer o el número de remisión") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero("Asent. planta (\")", asP, { asP = it; errNumero = null }, Modifier.weight(1f))
            CampoNumero("Asent. obra (\")", asO, { asO = it; errNumero = null }, Modifier.weight(1f))
        }
        errNumero?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

        Seccion("Ubicación")
        OutlinedTextField(
            value = loc, onValueChange = { loc = it },
            label = { Text("Localización (módulo, abscisa, hastial…)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val opciones = sugerencias.filter { it != loc && (loc.isBlank() || it.contains(loc, ignoreCase = true)) }
        if (opciones.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                opciones.forEach { s -> SuggestionChip(onClick = { loc = s }, label = { Text(s) }) }
            }
        }
        OutlinedTextField(
            value = obs, onValueChange = { obs = it }, label = { Text("Observación") }, minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )

        BotonPrincipal(if (m == null) "Guardar mixer" else "Guardar cambios", ::guardar)
        if (m != null) BotonEliminar("Eliminar este mixer", onEliminar)
        else Spacer(Modifier.height(24.dp))
    }
}
