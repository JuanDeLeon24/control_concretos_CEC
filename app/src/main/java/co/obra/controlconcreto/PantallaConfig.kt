@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.controlconcreto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Gris = Color(0xFF56636C)

@Composable
private fun PantallaCompleta(titulo: String, onCerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Fondo) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar") } },
                    title = { Text(titulo, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AzulOscuro, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                contenido()
            }
        }
    }
}

@Composable
private fun Subtitulo(t: String) {
    Text(t, color = AzulMedio, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
    HorizontalDivider(color = AzulClaro)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Campo(label: String, valor: String, ayuda: String? = null, numero: Boolean = false, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(label) }, singleLine = true,
        supportingText = if (ayuda != null) { { Text(ayuda) } } else null,
        keyboardOptions = if (numero) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )
}

/** Configuración de la app y del informe: textos del encabezado, nombres de campos, especificaciones y campos adicionales. */
@Composable
fun PantallaConfig(inicial: Config, onCerrar: () -> Unit, onGuardar: (Config) -> Unit) {
    var empresa by remember { mutableStateOf(inicial.empresa) }
    var proyecto by remember { mutableStateOf(inicial.proyecto) }
    var titulo by remember { mutableStateOf(inicial.titulo) }
    var codigo by remember { mutableStateOf(inicial.codigoFormato) }
    var area by remember { mutableStateOf(inicial.area) }
    var etFrente by remember { mutableStateOf(inicial.etFrente) }
    var etTramo by remember { mutableStateOf(inicial.etTramo) }
    var etLoc by remember { mutableStateOf(inicial.etLoc) }
    var asMin by remember { mutableStateOf(inicial.asentMin) }
    var asMax by remember { mutableStateOf(inicial.asentMax) }
    var tMin by remember { mutableStateOf(inicial.tempMin) }
    var tMax by remember { mutableStateOf(inicial.tempMax) }
    var espera by remember { mutableStateOf(inicial.esperaMax.toString()) }
    var campos by remember { mutableStateOf(inicial.campos) }
    var editando by remember { mutableStateOf<CampoExtra?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun guardar() {
        val malos = listOf("Asentamiento mínimo" to asMin, "Asentamiento máximo" to asMax)
            .firstOrNull { it.second.isNotBlank() && T.pulgadas(it.second) == null }
        if (malos != null) { error = "${malos.first}: escríbelo como 6, 6 1/2 o 6.5"; return }
        val malaT = listOf("Temperatura mínima" to tMin, "Temperatura máxima" to tMax)
            .firstOrNull { it.second.isNotBlank() && T.num(it.second) == null }
        if (malaT != null) { error = "${malaT.first} debe ser un número"; return }
        val esp = espera.trim().toIntOrNull()
        if (esp == null || esp < 1) { error = "La espera máxima debe ser un número entero de minutos"; return }
        onGuardar(
            Config(
                empresa = empresa.trim(), proyecto = proyecto.trim(), titulo = titulo.trim().ifBlank { Config().titulo },
                codigoFormato = codigo.trim(), area = area.trim(),
                etFrente = etFrente.trim().ifBlank { "Frente" }, etTramo = etTramo.trim().ifBlank { "Tramo" },
                etLoc = etLoc.trim().ifBlank { "Localización" },
                asentMin = T.normalizarPulg(asMin), asentMax = T.normalizarPulg(asMax),
                tempMin = T.limpiar(tMin), tempMax = T.limpiar(tMax), esperaMax = esp, campos = campos
            )
        )
    }

    PantallaCompleta("Configuración", onCerrar) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Adapta la app a cualquier obra: túnel, puente, obras hidráulicas, taludes, edificaciones. " +
                    "Los cambios aplican a los informes que generes desde ahora; lo ya registrado no se modifica.",
                color = Gris, fontSize = 14.sp
            )

            Subtitulo("Encabezado del informe")
            Campo("Empresa", empresa) { empresa = it }
            Campo("Proyecto u obra", proyecto, "Opcional. Aparece bajo el título") { proyecto = it }
            Campo("Título del formato", titulo) { titulo = it }
            Campo("Código del formato", codigo, "Ej. PS-TUNEL 011, PS-PUENTE 004") { codigo = it }
            Campo("Área o actividad", area, "Se muestra en la pantalla de inicio") { area = it }

            Subtitulo("Nombres de los campos")
            Campo("En vez de \"Frente\"", etFrente, "Ej. Frente, Estructura, Obra") { etFrente = it }
            Campo("En vez de \"Tramo\"", etTramo, "Ej. Tramo, Pila, Eje, Módulo") { etTramo = it }
            Campo("En vez de \"Localización\"", etLoc, "Ej. Localización, Elemento, Ubicación") { etLoc = it }

            Subtitulo("Especificaciones (para las estadísticas)")
            Text("Déjalas vacías si no aplican. El resumen cuenta los mixers fuera de rango.", color = Gris, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) { Campo("Asent. mínimo (\")", asMin) { asMin = it; error = null } }
                Column(Modifier.weight(1f)) { Campo("Asent. máximo (\")", asMax) { asMax = it; error = null } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Campo("Temp. mínima (°C)", tMin, numero = true) { tMin = it; error = null } }
                Column(Modifier.weight(1f)) { Campo("Temp. máxima (°C)", tMax, numero = true) { tMax = it; error = null } }
            }
            Campo("Espera máxima aceptable (min)", espera, "Mixers con más espera se cuentan aparte", numero = true) { espera = it; error = null }

            Subtitulo("Campos adicionales por mixer")
            Text(
                "Aditivos, fibras, dosis, resistencia, agua adicionada, cilindros… Aparecen en el formulario del mixer, " +
                    "en una tabla aparte del PDF, en el Excel y en las estadísticas.",
                color = Gris, fontSize = 13.sp
            )
            if (campos.isEmpty()) Text("No hay campos adicionales.", color = Gris, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            campos.forEach { c ->
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(c.titulo, fontWeight = FontWeight.SemiBold, color = AzulOscuro)
                            Text(
                                c.tipo.nombre + (if (c.tipo == TipoCampo.LISTA) ": " + c.opciones.joinToString(", ") else "") +
                                    if (c.estadistica) "  ·  en estadísticas" else "",
                                fontSize = 12.sp, color = Gris
                            )
                        }
                        IconButton(onClick = { editando = c }) { Icon(Icons.Default.Edit, contentDescription = "Editar campo") }
                        IconButton(onClick = { campos = campos.filter { it.id != c.id } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Quitar campo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                OutlinedButton(
                    onClick = { editando = CampoExtra(T.uid(), "", TipoCampo.NUMERO) },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Agregar campo", color = AzulOscuro) }
                OutlinedButton(
                    onClick = {
                        val nuevos = Ajustes.sugeridos.filter { s -> campos.none { it.id == s.id || it.nombre.equals(s.nombre, true) } }
                        campos = campos + nuevos
                        error = if (nuevos.isEmpty()) "Los campos sugeridos ya están agregados" else null
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Agregar sugeridos", color = AzulOscuro) }
            }
            Text(
                "Sugeridos: resistencia f'c, tipo de concreto, aditivo y dosis, fibra y dosis, agua adicionada, cilindros y equipo de colocación. " +
                    "Quita los que no uses.",
                color = Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            Button(
                onClick = ::guardar,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
            ) { Text("Guardar configuración", fontWeight = FontWeight.SemiBold) }
            TextButton(
                onClick = {
                    val d = Config()
                    empresa = d.empresa; proyecto = d.proyecto; titulo = d.titulo; codigo = d.codigoFormato; area = d.area
                    etFrente = d.etFrente; etTramo = d.etTramo; etLoc = d.etLoc
                    asMin = ""; asMax = ""; tMin = ""; tMax = ""; espera = d.esperaMax.toString()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) { Text("Restablecer textos originales (no borra los campos)", color = AzulMedio) }
        }
    }

    editando?.let { c ->
        EditorCampo(
            inicial = c,
            onCancelar = { editando = null },
            onListo = { nuevo ->
                campos = if (campos.any { it.id == nuevo.id }) campos.map { if (it.id == nuevo.id) nuevo else it } else campos + nuevo
                editando = null
            }
        )
    }
}

@Composable
private fun EditorCampo(inicial: CampoExtra, onCancelar: () -> Unit, onListo: (CampoExtra) -> Unit) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var tipo by remember { mutableStateOf(inicial.tipo) }
    var unidad by remember { mutableStateOf(inicial.unidad) }
    var opciones by remember { mutableStateOf(inicial.opciones.joinToString("\n")) }
    var estadistica by remember { mutableStateOf(inicial.estadistica) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial.nombre.isBlank()) "Nuevo campo" else "Editar campo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it; error = null }, label = { Text("Nombre") },
                    placeholder = { Text("Ej. Aditivo, Dosis de fibra") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Tipo", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                TipoCampo.values().forEach { t ->
                    if (t == tipo) Button(
                        onClick = { tipo = t }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text(t.nombre) }
                    else OutlinedButton(onClick = { tipo = t }, modifier = Modifier.fillMaxWidth()) { Text(t.nombre, color = AzulOscuro) }
                }
                if (tipo == TipoCampo.NUMERO) OutlinedTextField(
                    value = unidad, onValueChange = { unidad = it }, label = { Text("Unidad (opcional)") },
                    placeholder = { Text("Ej. L/m³, kg/m³, MPa") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (tipo == TipoCampo.LISTA) OutlinedTextField(
                    value = opciones, onValueChange = { opciones = it; error = null },
                    label = { Text("Opciones (una por renglón)") }, minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (tipo == TipoCampo.LISTA) Text("\"Otro...\" se agrega solo al final.", fontSize = 12.sp, color = Gris)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Incluir en estadísticas", modifier = Modifier.weight(1f))
                    Switch(checked = estadistica, onCheckedChange = { estadistica = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ops = opciones.lines().map { it.trim() }
                    .filter { it.isNotEmpty() && !it.equals(Elementos.OTRO, ignoreCase = true) }.distinct()
                when {
                    nombre.isBlank() -> error = "Escribe el nombre del campo"
                    tipo == TipoCampo.LISTA && ops.isEmpty() -> error = "Escribe al menos una opción"
                    else -> onListo(
                        CampoExtra(
                            id = inicial.id, nombre = nombre.trim(), tipo = tipo,
                            unidad = if (tipo == TipoCampo.NUMERO) unidad.trim() else "",
                            opciones = if (tipo == TipoCampo.LISTA) ops else emptyList(),
                            estadistica = estadistica
                        )
                    )
                }
            }) { Text("Listo") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/** Resumen que se muestra al finalizar la jornada: parámetros, estadísticas y salida en PDF / Excel. */
@Composable
fun PantallaResumen(
    j: Jornada,
    cfg: Config,
    generando: Boolean,
    onCerrar: () -> Unit,
    onPdf: () -> Unit,
    onExcel: () -> Unit,
    onPaquete: () -> Unit
) {
    val filas = remember(j, cfg) { filasResumen(resumen(j, cfg), cfg) }
    PantallaCompleta("Resumen de la jornada", onCerrar) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                (listOf(T.fechaLarga(j.fecha), "turno ${j.turno.lowercase()}") + listOf(j.frente, j.tramo).filter { it.isNotBlank() })
                    .joinToString("  ·  "),
                color = AzulOscuro, fontWeight = FontWeight.SemiBold
            )
            if (j.mixers.any { it.fin.isBlank() }) Surface(
                color = AvisoFondo, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Text(
                    "Hay mixers sin hora de fin de descargue. Complétalos para que las estadísticas queden exactas.",
                    color = Aviso, fontSize = 14.sp, modifier = Modifier.padding(12.dp)
                )
            }
            Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    filas.forEachIndexed { i, (k, v) ->
                        if (i > 0) HorizontalDivider(color = Color(0xFFE6ECF4))
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Text(k, fontSize = 13.sp, color = AzulMedio)
                            Text(v, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AzulOscuro)
                        }
                    }
                }
            }
            Text("Generar informe", color = AzulMedio, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            if (generando) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            Button(
                onClick = onPdf, enabled = !generando, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
            ) { Text("PDF (planilla para imprimir y firmar)") }
            Button(
                onClick = onExcel, enabled = !generando, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cian)
            ) { Text("Excel (.xlsx)") }
            OutlinedButton(
                onClick = onPaquete, enabled = !generando, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp).height(52.dp)
            ) { Text("Jornada completa con fotos (para otro teléfono)", color = AzulOscuro) }
        }
    }
}
