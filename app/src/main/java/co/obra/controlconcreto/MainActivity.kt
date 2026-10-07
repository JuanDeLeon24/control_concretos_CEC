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
import androidx.compose.ui.unit.sp
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
data class Exportado(val archivo: File, val mime: String, val tipo: String)
const val MIME_PDF = "application/pdf"
const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val MIME_CSV = "text/csv"
const val MIME_ZIP = "application/zip"

/** Jornada importada que choca con una existente: se espera la decisión del usuario. */
data class Pendiente(val paquete: Paquete, val existente: Jornada)

data class Confirmacion(val titulo: String, val texto: String, val accion: String, val alConfirmar: () -> Unit)

private fun uriDe(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", f)

private fun compartir(ctx: Context, f: File, mime: String) {
    val i = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uriDe(ctx, f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Compartir archivo"))
}

private fun abrir(ctx: Context, f: File, mime: String): Boolean = try {
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uriDe(ctx, f), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )
    true
} catch (e: ActivityNotFoundException) { false }

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
    var exportado by remember { mutableStateOf<Exportado?>(null) }
    var elegirFormato by remember { mutableStateOf<Jornada?>(null) }
    var generando by remember { mutableStateOf(false) }
    var versionFotos by remember { mutableIntStateOf(0) }
    var capturaId by rememberSaveable { mutableStateOf<String?>(null) }
    var recorte by remember { mutableStateOf<Pair<String, android.graphics.Bitmap>?>(null) }
    var procesando by remember { mutableStateOf(false) }
    var verFotoId by remember { mutableStateOf<String?>(null) }
    var versionCat by remember { mutableIntStateOf(0) }
    val catalogo = remember(versionCat) { db.catalogo() }
    val elementosCat = remember(versionCat) { db.elementos() }
    var verCatalogo by remember { mutableStateOf(false) }
    var msgCatalogo by remember { mutableStateOf<String?>(null) }
    var cargandoCat by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf<Pendiente?>(null) }
    var pedirAdmin by remember { mutableStateOf<Autorizacion?>(null) }
    fun conAdmin(motivo: String, accion: () -> Unit) { pedirAdmin = Autorizacion(motivo, accion) }

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val id = capturaId
        if (ok && id != null) scope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { Fotos.cargarCaptura(ctx) }.getOrNull() }
            if (b != null) recorte = id to b else aviso("No se pudo leer la foto")
        }
    }
    val desdeGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = capturaId
        if (uri != null && id != null) scope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { Fotos.cargarUri(ctx, uri) }.getOrNull() }
            if (b != null) recorte = id to b else aviso("No se pudo leer esa imagen")
        }
    }
    fun abrirCamara(id: String) {
        capturaId = id; verFotoId = null
        val f = Fotos.captura(ctx); f.delete()
        try { tomarFoto.launch(uriDe(ctx, f)) }
        catch (e: ActivityNotFoundException) { aviso("No se encontró una app de cámara") }
    }
    fun abrirGaleria(id: String) {
        capturaId = id; verFotoId = null
        desdeGaleria.launch("image/*")
    }

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
    val cargarCatalogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            cargandoCat = true; msgCatalogo = null
            scope.launch {
                val r = runCatching {
                    withContext(Dispatchers.IO) {
                        val res = Catalogo.leer(ctx, uri)
                        db.reemplazarCatalogo(res.items, res.elementos)
                        res
                    }
                }
                cargandoCat = false
                r.onSuccess { res ->
                    versionCat++
                    msgCatalogo = "Catálogo cargado: ${res.items.size} lugares y ${res.elementos.size} elementos de vaciado." +
                        (if (res.omitidas > 0) " Se omitieron ${res.omitidas} filas sin frente." else "")
                }.onFailure { e ->
                    msgCatalogo = e.message?.takeIf { it.isNotBlank() && e is IllegalArgumentException }
                        ?: "No se pudo leer ese archivo. Usa la plantilla (Excel .xlsx o CSV)."
                }
            }
        }
    }

    fun instalarJornada(p: Paquete, reemplazar: Jornada?, ambas: Boolean) {
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Intercambio.instalar(ctx, db, p, reemplazar, ambas) } }
            r.onSuccess { j ->
                version++; versionFotos++
                detalleId = j.id
                aviso("Jornada importada: ${j.mixers.size} mixers y ${p.fotos.size} fotos")
            }.onFailure { aviso("No se pudo importar la jornada") }
        }
    }

    val importarJornada = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Intercambio.leer(ctx, uri) } }
            r.onSuccess { p ->
                val choque = Intercambio.conflicto(db.jornadas(), p.jornada)
                if (choque == null) instalarJornada(p, null, false)
                else pendiente = Pendiente(p, choque)
            }.onFailure { e ->
                aviso(e.message?.takeIf { e is IllegalArgumentException } ?: "Ese archivo no es una jornada exportada desde esta app")
            }
        }
    }

    fun copiarA(uri: android.net.Uri?) {
        val e = exportado
        if (uri != null && e != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { out -> e.archivo.inputStream().use { it.copyTo(out) } }
            }.onSuccess {
                val txt = if (e.tipo == "Plantilla") "Plantilla guardada en el teléfono" else "${e.tipo} guardado"
                aviso(txt)
                if (verCatalogo) msgCatalogo = txt
            }
                .onFailure { aviso("No se pudo guardar el archivo") }
        }
    }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { copiarA(it) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { copiarA(it) }
    val guardarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_CSV)) { copiarA(it) }
    val guardarZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_ZIP)) { copiarA(it) }

    fun generar(tipo: String, mime: String, crear: () -> File) {
        generando = true
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { crear() } }
            generando = false
            r.onSuccess { exportado = Exportado(it, mime, tipo) }
                .onFailure { aviso("No se pudo generar el archivo ($tipo)") }
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
            onImportar = {
                conAdmin("restaurar una copia de seguridad") {
                    importarCopia.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            },
            onHistorialExcel = {
                val todas = db.jornadas()
                if (todas.none { it.mixers.isNotEmpty() }) aviso("Todavía no hay mixers registrados para exportar")
                else generar("Excel", MIME_XLSX) { Excel.historial(ctx, todas) }
            },
            onCatalogo = { msgCatalogo = null; verCatalogo = true },
            onImportarJornada = { conAdmin("importar una jornada") { importarJornada.launch(arrayOf("*/*")) } }
        )
    } else {
        BackHandler { detalleId = null }
        Detalle(
            j = actual, snack = snack, generando = generando,
            onAtras = { detalleId = null },
            onEditar = { formJ = FormJ(actual) },
            onAgregar = { formM = FormM(actual, null) },
            onMixer = { formM = FormM(actual, it) },
            onCamara = { m -> if (Fotos.existe(ctx, m.id)) verFotoId = m.id else abrirCamara(m.id) },
            versionFotos = versionFotos,
            onExportar = { elegirFormato = actual }
        )
    }

    formJ?.let { f ->
        FormJornada(
            j = f.jornada,
            ultimo = remember { Prefs.ultimo(ctx) },
            catalogo = catalogo,
            hayElementos = elementosCat.isNotEmpty(),
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
                    j.mixers.forEach { Fotos.eliminar(ctx, it.id) }
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
            lugares = if (f.jornada.usaCatalogo) Catalogo.lugares(catalogo, f.jornada.frente, f.jornada.tramo) else emptyList(),
            elementos = if (f.jornada.usaCatalogo) elementosCat else emptyList(),
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
                    Fotos.eliminar(ctx, m.id); versionFotos++
                    db.eliminarMixer(m); version++
                    formM = null
                    aviso("Mixer eliminado")
                }
            }
        )
    }

    elegirFormato?.let { j ->
        AlertDialog(
            onDismissRequest = { elegirFormato = null },
            title = { Text("Exportar jornada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿En qué formato quieres exportar el resumen del ${T.fechaCorta(j.fecha)}?")
                    Button(
                        onClick = { elegirFormato = null; generar("PDF", MIME_PDF) { Pdf.generar(ctx, j, logo) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text("PDF (planilla para imprimir y firmar)") }
                    Button(
                        onClick = { elegirFormato = null; generar("Excel", MIME_XLSX) { Excel.jornada(ctx, j) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cian)
                    ) { Text("Excel (.xlsx)") }
                    OutlinedButton(
                        onClick = { elegirFormato = null; generar("Archivo de jornada", MIME_ZIP) { Intercambio.exportar(ctx, j) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Jornada completa con fotos (para otro teléfono)", color = AzulOscuro) }
                }
            },
            confirmButton = { TextButton(onClick = { elegirFormato = null }) { Text("Cancelar") } }
        )
    }

    if (verCatalogo) {
        PantallaCatalogo(
            catalogo = catalogo, elementos = elementosCat, mensaje = msgCatalogo, cargando = cargandoCat,
            onCerrar = { verCatalogo = false },
            onPlantillaExcel = {
                runCatching { Catalogo.plantilla(ctx, true) }
                    .onSuccess { exportado = Exportado(it, MIME_XLSX, "Plantilla") }
                    .onFailure { msgCatalogo = "No se pudo preparar la plantilla" }
            },
            onPlantillaCsv = {
                runCatching { Catalogo.plantilla(ctx, false) }
                    .onSuccess { exportado = Exportado(it, MIME_CSV, "Plantilla") }
                    .onFailure { msgCatalogo = "No se pudo preparar la plantilla" }
            },
            onCargar = { msgCatalogo = null; conAdmin("cargar el catálogo") { cargarCatalogo.launch(arrayOf("*/*")) } },
            onEliminar = {
                conAdmin("eliminar el catálogo") { confirm = Confirmacion(
                    "Eliminar catálogo",
                    "Se borrarán los ${catalogo.size} lugares y ${elementosCat.size} elementos cargados. Las jornadas ya registradas no se modifican. " +
                        "Después podrás cargar un catálogo nuevo.",
                    "Eliminar"
                ) {
                    db.borrarCatalogo(); versionCat++
                    msgCatalogo = "Catálogo eliminado. Ya puedes cargar uno nuevo."
                } }
            }
        )
    }

    pendiente?.let { pd ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("Esta jornada ya existe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ya tienes la jornada del ${T.fechaCorta(pd.existente.fecha)}, turno ${pd.existente.turno.lowercase()}" +
                            (if (pd.existente.frente.isNotBlank()) ", ${pd.existente.frente}" else "") +
                            " (${pd.existente.mixers.size} mixers). La que llega trae ${pd.paquete.jornada.mixers.size} mixers " +
                            "y ${pd.paquete.fotos.size} fotos. ¿Qué quieres hacer?"
                    )
                    Button(
                        onClick = { pendiente = null; instalarJornada(pd.paquete, pd.existente, false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text("Reemplazar la que ya tengo") }
                    OutlinedButton(
                        onClick = { pendiente = null; instalarJornada(pd.paquete, null, true) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Conservar las dos", color = AzulOscuro) }
                }
            },
            confirmButton = { TextButton(onClick = { pendiente = null }) { Text("Cancelar") } }
        )
    }

    pedirAdmin?.let { pa ->
        DialogoAdmin(
            motivo = pa.motivo,
            onCancelar = { pedirAdmin = null },
            onAutorizado = { pedirAdmin = null; pa.accion() }
        )
    }

    exportado?.let { e ->
        AlertDialog(
            onDismissRequest = { exportado = null },
            title = { Text("${e.tipo} listo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(e.archivo.name)
                    if (e.mime == MIME_ZIP) Text(
                        "Envíalo por WhatsApp o correo. En el otro teléfono: menú ⋮ → Importar jornada de otro teléfono.",
                        fontSize = 13.sp
                    )
                    else Button(
                        onClick = { if (!abrir(ctx, e.archivo, e.mime)) aviso("No hay una app para abrir este archivo; usa Compartir") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text("Abrir") }
                    OutlinedButton(onClick = { compartir(ctx, e.archivo, e.mime) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Compartir (WhatsApp, correo…)")
                    }
                    OutlinedButton(
                        onClick = {
                            when (e.mime) {
                                MIME_PDF -> guardarPdf.launch(e.archivo.name)
                                MIME_CSV -> guardarCsv.launch(e.archivo.name)
                                MIME_ZIP -> guardarZip.launch(e.archivo.name)
                                else -> guardarXlsx.launch(e.archivo.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar en el teléfono") }
                }
            },
            confirmButton = { TextButton(onClick = { exportado = null }) { Text("Cerrar") } }
        )
    }

    recorte?.let { (id, bmp) ->
        PantallaRecorte(
            original = bmp, procesando = procesando,
            onCancelar = { recorte = null },
            onRepetir = { recorte = null; abrirCamara(id) },
            onGuardar = { b, esquinas ->
                procesando = true
                scope.launch {
                    val r = runCatching {
                        withContext(Dispatchers.Default) {
                            val limpia = Escaner.filtroEscaneo(Escaner.enderezar(b, esquinas))
                            Fotos.guardar(ctx, id, limpia)
                        }
                    }
                    procesando = false; recorte = null; versionFotos++
                    r.onSuccess { aviso("Remisión guardada") }.onFailure { aviso("No se pudo procesar la foto") }
                }
            }
        )
    }

    verFotoId?.let { id ->
        val m = jornadas.flatMap { it.mixers }.find { it.id == id }
        val b = remember(id, versionFotos) { Fotos.cargar(ctx, id) }
        if (m != null && b != null) {
            VerFoto(
                bmp = b, titulo = "Remisión ${m.codigo}",
                onCerrar = { verFotoId = null },
                onCamara = { abrirCamara(id) },
                onGaleria = { abrirGaleria(id) },
                onCompartir = { compartir(ctx, Fotos.archivo(ctx, id), "image/jpeg") },
                onEliminar = {
                    confirm = Confirmacion("Eliminar foto", "Se borrará la foto de la remisión ${m.codigo}.", "Eliminar") {
                        Fotos.eliminar(ctx, id); versionFotos++; verFotoId = null
                        aviso("Foto eliminada")
                    }
                }
            )
        }
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
