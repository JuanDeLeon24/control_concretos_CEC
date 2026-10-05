@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.controlconcreto

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Db(applicationContext)
        setContent { TemaCondor { App(db) } }
    }
}

data class FormJ(val jornada: Jornada?)
data class FormM(val jornada: Jornada, val mixer: Mixer?)
data class Confirmacion(val titulo: String, val texto: String, val accion: String, val alConfirmar: () -> Unit)

private fun uriDe(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", f)

private fun compartirPdf(ctx: Context, f: File) {
    val i = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uriDe(ctx, f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Compartir PDF"))
}

private fun abrirPdf(ctx: Context, f: File): Boolean = try {
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uriDe(ctx, f), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )
    true
} catch (e: ActivityNotFoundException) { false }

private fun compartirExcel(ctx: Context, f: File) {
    val i = Intent(Intent.ACTION_SEND)
        .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .putExtra(Intent.EXTRA_STREAM, uriDe(ctx, f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Compartir Excel"))
}

private fun nombreCarpeta(ctx: Context, uri: android.net.Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0).orEmpty() else ""
    } ?: ""
}.getOrDefault("")

private fun exportarExcelOrigen(
    ctx: Context, scope: kotlinx.coroutines.CoroutineScope, jornadas: List<Jornada>, origen: String,
    onListo: (File) -> Unit, onError: () -> Unit
) {
    scope.launch {
        val r = runCatching { withContext(Dispatchers.IO) { Excel.generarOrigen(ctx, jornadas, origen) } }
        r.onSuccess(onListo).onFailure { onError() }
    }
}

@Composable
fun App(db: Db) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    fun aviso(msg: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } }

    var version by remember { mutableIntStateOf(0) }
    val jornadas = remember(version) { db.jornadas() }
    val sugerencias = remember(jornadas) {
        jornadas.flatMap { it.mixers }.map { it.loc.trim() }.filter { it.isNotEmpty() }.distinct().take(15)
    }
    var detalleId by rememberSaveable { mutableStateOf<String?>(null) }
    var logo by remember { mutableStateOf(Logo.cargar(ctx)) }
    var formJ by remember { mutableStateOf<FormJ?>(null) }
    var formM by remember { mutableStateOf<FormM?>(null) }
    var confirm by remember { mutableStateOf<Confirmacion?>(null) }
    var pdf by remember { mutableStateOf<File?>(null) }
    var excel by remember { mutableStateOf<File?>(null) }
    var generando by remember { mutableStateOf(false) }
    var elegirOrigenExcel by remember { mutableStateOf(false) }

    val elegirLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (Logo.guardar(ctx, uri)) { logo = Logo.cargar(ctx); aviso("Logo actualizado") }
            else aviso("No se pudo leer esa imagen")
        }
    }
    val exportarCopia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Respaldo.aJson(db.jornadas()).toByteArray()) }
            }.onSuccess { aviso("Copia de seguridad guardada") }
                .onFailure { aviso("No se pudo guardar la copia") }
        }
    }
    val importarCopia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val txt = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                val lista = Respaldo.desdeJson(txt)
                db.importar(lista)
                lista.size
            }.onSuccess { version++; aviso("Se restauraron $it jornadas") }
                .onFailure { aviso("Ese archivo no es una copia válida de esta app") }
        }
    }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pdf
        if (uri != null && f != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
            }.onSuccess { aviso("PDF guardado") }
                .onFailure { aviso("No se pudo guardar el PDF") }
        }
    }

    val importarExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                val r = runCatching {
                    withContext(Dispatchers.IO) {
                        val origen = nombreCarpeta(ctx, uri).ifBlank { "Importado" }
                        Excel.importarCarpeta(ctx, uri, origen).also { db.importar(it) }
                    }
                }
                r.onSuccess { lista ->
                    version++
                    val mixers = lista.sumOf { it.mixers.size }
                    aviso("Importadas ${lista.size} jornadas y $mixers mixers como '${lista.firstOrNull()?.origen ?: "Importado"}'")
                }.onFailure { aviso("No se pudo importar el Excel: ${it.message ?: "archivo no válido"}") }
            }
        }
    }

    val guardarExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val f = excel
        if (uri != null && f != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
            }.onSuccess { aviso("Excel guardado") }
                .onFailure { aviso("No se pudo guardar el Excel") }
        }
    }

    val actual = detalleId?.let { id -> jornadas.find { it.id == id } }

    if (actual == null) {
        Inicio(
            jornadas = jornadas, logo = logo, snack = snack,
            onAbrir = { detalleId = it },
            onNueva = { formJ = FormJ(null) },
            onCambiarLogo = { elegirLogo.launch("image/*") },
            onQuitarLogo = { Logo.quitar(ctx); logo = Logo.cargar(ctx); aviso("Logo quitado") },
            onExportar = { exportarCopia.launch("respaldo_concreto_${T.hoy()}.json") },
            onImportar = { importarCopia.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onImportarExcel = { importarExcel.launch(null) },
            onExportarExcel = { elegirOrigenExcel = true }
        )
    } else {
        BackHandler { detalleId = null }
        Detalle(
            j = actual, snack = snack, generando = generando,
            onAtras = { detalleId = null },
            onEditar = { formJ = FormJ(actual) },
            onAgregar = { formM = FormM(actual, null) },
            onMixer = { formM = FormM(actual, it) },
            onPdf = {
                generando = true
                scope.launch {
                    val r = runCatching { withContext(Dispatchers.IO) { Pdf.generar(ctx, actual, logo) } }
                    generando = false
                    r.onSuccess { pdf = it }.onFailure { aviso("No se pudo generar el PDF") }
                }
            },
            onExcel = {
                scope.launch {
                    val r = runCatching { withContext(Dispatchers.IO) { Excel.generarJornada(ctx, actual) } }
                    r.onSuccess { excel = it }
                        .onFailure { aviso("No se pudo generar el Excel") }
                }
            }
        )
    }

    formJ?.let { f ->
        FormJornada(
            j = f.jornada,
            ultimo = remember { Prefs.ultimo(ctx) },
            onCerrar = { formJ = null },
            onGuardar = { nueva ->
                val duplicada = if (f.jornada == null) jornadas.find {
                    it.fecha == nueva.fecha && it.turno == nueva.turno && it.frente == nueva.frente && it.tramo == nueva.tramo
                } else null
                if (duplicada != null) {
                    formJ = null; detalleId = duplicada.id
                    aviso("Esa jornada ya existía; la abrí para continuar")
                } else {
                    db.guardarJornada(nueva)
                    Prefs.guardar(ctx, nueva)
                    version++
                    formJ = null; detalleId = nueva.id
                    aviso(if (f.jornada == null) "Jornada creada" else "Cambios guardados")
                }
            },
            onEliminar = {
                val j = f.jornada ?: return@FormJornada
                confirm = Confirmacion(
                    "Eliminar jornada",
                    "Se borrará la jornada del ${T.fechaCorta(j.fecha)} con sus ${j.mixers.size} mixers. Esta acción no se puede deshacer.",
                    "Eliminar"
                ) {
                    db.eliminarJornada(j.id); version++
                    formJ = null; detalleId = null
                    aviso("Jornada eliminada")
                }
            }
        )
    }

    formM?.let { f ->
        FormMixer(
            j = f.jornada, m = f.mixer, sugerencias = sugerencias,
            onCerrar = { formM = null },
            onGuardar = { m ->
                val final = if (f.mixer == null) m.copy(orden = db.siguienteOrden(f.jornada.id)) else m
                db.guardarMixer(final); version++
                formM = null
                aviso(if (f.mixer == null) "Mixer guardado" else "Cambios guardados")
            },
            onEliminar = {
                val m = f.mixer ?: return@FormMixer
                confirm = Confirmacion(
                    "Eliminar mixer",
                    "Se borrará el mixer ${m.codigo.ifBlank { m.orden.toString() }} de esta jornada. Los demás conservarán su orden.",
                    "Eliminar"
                ) {
                    db.eliminarMixer(m); version++
                    formM = null
                    aviso("Mixer eliminado")
                }
            }
        )
    }

    pdf?.let { f ->
        AlertDialog(
            onDismissRequest = { pdf = null },
            title = { Text("PDF listo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(f.name)
                    Button(
                        onClick = { if (!abrirPdf(ctx, f)) aviso("No hay una app para ver PDF; usa Compartir") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text("Abrir") }
                    OutlinedButton(onClick = { compartirPdf(ctx, f) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Compartir (WhatsApp, correo…)")
                    }
                    OutlinedButton(onClick = { guardarPdf.launch(f.name) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Guardar en el teléfono")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pdf = null }) { Text("Cerrar") } }
        )
    }

    if (elegirOrigenExcel) {
        val fuentes = listOf(Excel.ORIGEN_LOCAL) + jornadas.map { it.origen }.filter { it != Excel.ORIGEN_LOCAL }.distinct().sorted()
        AlertDialog(
            onDismissRequest = { elegirOrigenExcel = false },
            title = { Text("Exportar a Excel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecciona qué registros quieres exportar:")
                    OutlinedButton(onClick = { elegirOrigenExcel = false; exportarExcelOrigen(ctx, scope, jornadas, Excel.ORIGEN_LOCAL, { excel = it }, { aviso("No se pudo generar el Excel") }) }, modifier = Modifier.fillMaxWidth()) { Text("Mis datos") }
                    fuentes.filter { it != Excel.ORIGEN_LOCAL }.forEach { fuente ->
                        OutlinedButton(onClick = { elegirOrigenExcel = false; exportarExcelOrigen(ctx, scope, jornadas, fuente, { excel = it }, { aviso("No se pudo generar el Excel") }) }, modifier = Modifier.fillMaxWidth()) { Text(fuente) }
                    }
                    Button(onClick = { elegirOrigenExcel = false; exportarExcelOrigen(ctx, scope, jornadas, Excel.ORIGEN_TODOS, { excel = it }, { aviso("No se pudo generar el Excel") }) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)) { Text("Registro unificado — Todos") }
                }
            },
            confirmButton = { TextButton(onClick = { elegirOrigenExcel = false }) { Text("Cancelar") } }
        )
    }

    excel?.let { f ->
        AlertDialog(
            onDismissRequest = { excel = null },
            title = { Text("Excel listo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(f.name)
                    OutlinedButton(
                        onClick = { compartirExcel(ctx, f) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compartir (WhatsApp, correo…)")
                    }
                    OutlinedButton(
                        onClick = { guardarExcel.launch(f.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar en el teléfono")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { excel = null }) { Text("Cerrar") } }
        )
    }

    confirm?.let { cf ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(cf.titulo) },
            text = { Text(cf.texto) },
            confirmButton = {
                TextButton(onClick = { confirm = null; cf.alConfirmar() }) {
                    Text(cf.accion, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }
        )
    }
}
