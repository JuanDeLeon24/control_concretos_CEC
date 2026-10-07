@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package co.obra.controlconcreto

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
private fun coloresBarra() = TopAppBarDefaults.topAppBarColors(
    containerColor = AzulOscuro,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White
)

// ===================== INICIO =====================

@Composable
fun Inicio(
    jornadas: List<Jornada>,
    logo: Bitmap?,
    snack: SnackbarHostState,
    onAbrir: (String) -> Unit,
    onNueva: () -> Unit,
    onCambiarLogo: () -> Unit,
    onQuitarLogo: () -> Unit,
    onExportar: () -> Unit,
    onImportar: () -> Unit,
    onHistorialExcel: () -> Unit,
    onCatalogo: () -> Unit,
    onImportarJornada: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Control de concreto", fontWeight = FontWeight.Bold)
                        Text("PS-TUNEL 011  |  Revestimiento del túnel", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                },
                colors = coloresBarra(),
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (logo == null) "Agregar logo de la empresa" else "Cambiar logo") },
                                onClick = { menu = false; onCambiarLogo() }
                            )
                            if (logo != null) DropdownMenuItem(text = { Text("Quitar logo") }, onClick = { menu = false; onQuitarLogo() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Catálogo de frentes y tramos") }, onClick = { menu = false; onCatalogo() })
                            DropdownMenuItem(text = { Text("Importar jornada de otro teléfono") }, onClick = { menu = false; onImportarJornada() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Exportar historial a Excel") }, onClick = { menu = false; onHistorialExcel() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Guardar copia de seguridad") }, onClick = { menu = false; onExportar() })
                            DropdownMenuItem(text = { Text("Restaurar copia de seguridad") }, onClick = { menu = false; onImportar() })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNueva,
                containerColor = AzulOscuro, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva jornada", fontWeight = FontWeight.SemiBold) }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        val grupos = remember(jornadas) { jornadas.groupBy { it.fecha }.toSortedMap(compareByDescending { it }) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp)
        ) {
            item { Marca(logo, onCambiarLogo) }
            if (grupos.isEmpty()) item {
                Vacio("Sin jornadas todavía", "Toca \"Nueva jornada\" para crear la del día y empezar a registrar cada mixer que llegue a obra.")
            }
            grupos.forEach { (fecha, lista) ->
                stickyHeader(key = "h$fecha") { CabeceraDia(fecha, lista.sumOf { T.volumen(it) }) }
                items(lista, key = { it.id }) { j -> TarjetaJornada(j) { onAbrir(j.id) } }
            }
        }
    }
}

/** Franja de marca: logo + barra divisoria azul claro 50 % + nombre del proyecto (según manual). */
@Composable
private fun Marca(logo: Bitmap?, onLogo: () -> Unit) {
    Surface(
        color = Color.White, shape = RoundedCornerShape(10.dp), shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Row(Modifier.padding(14.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) {
                val img = remember(logo) { logo.asImageBitmap() }
                Image(img, contentDescription = "Logo de la empresa", contentScale = ContentScale.Fit,
                    modifier = Modifier.height(54.dp).widthIn(max = 170.dp))
            } else {
                Column(Modifier.clickable(onClick = onLogo)) {
                    Text("Construcciones", color = AzulOscuro, fontSize = 13.sp)
                    Text("EL CONDOR S.A.", color = AzulOscuro, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Toca para agregar el logo", color = AzulMedio, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.5.dp).fillMaxHeight().background(AzulClaro.copy(alpha = 0.5f)))
            Spacer(Modifier.width(14.dp))
            Text(
                "Control llegadas de concreto a obra",
                color = AzulOscuro.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun CabeceraDia(fecha: String, vol: Double) {
    Surface(color = Fondo, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(T.diaNumero(fecha), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = AzulOscuro, lineHeight = 34.sp)
            Spacer(Modifier.width(8.dp))
            Text(T.diaResto(fecha), fontSize = 15.sp, color = AzulMedio, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
            Text("${T.fmt(vol, 2)} m³", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AzulMedio,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun TarjetaJornada(j: Jornada, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (j.turno == "Noche") AzulOscuro else Cian))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("Turno ${j.turno.lowercase()}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AzulOscuro)
                val meta = listOf(j.frente, if (j.tramo.isNotBlank()) "Tramo ${j.tramo}" else "").filter { it.isNotBlank() }
                Text(
                    "${j.mixers.size} ${if (j.mixers.size == 1) "mixer" else "mixers"}" + (if (meta.isNotEmpty()) ", " + meta.joinToString(", ") else ""),
                    fontSize = 14.sp, color = Color(0xFF56636C)
                )
            }
            Column(Modifier.padding(end = 14.dp), horizontalAlignment = Alignment.End) {
                Text(T.fmt(T.volumen(j), 2), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AzulOscuro)
                Text("m³", fontSize = 12.sp, color = Color(0xFF56636C))
            }
        }
    }
}

@Composable
private fun Vacio(titulo: String, texto: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titulo, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AzulOscuro)
        Spacer(Modifier.height(6.dp))
        Text(texto, textAlign = TextAlign.Center, color = Color(0xFF56636C))
    }
}

// ===================== DETALLE DE JORNADA =====================

@Composable
fun Detalle(
    j: Jornada,
    snack: SnackbarHostState,
    generando: Boolean,
    onAtras: () -> Unit,
    onEditar: () -> Unit,
    onAgregar: () -> Unit,
    onMixer: (Mixer) -> Unit,
    onCamara: (Mixer) -> Unit,
    versionFotos: Int,
    onExportar: () -> Unit
) {
    val r = remember(j) { resumen(j) }
    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a jornadas") } },
                title = {
                    Column {
                        Text(T.fechaCorta(j.fecha), fontWeight = FontWeight.Bold)
                        Text("Turno ${j.turno.lowercase()}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                },
                actions = { IconButton(onClick = onEditar) { Icon(Icons.Default.Edit, contentDescription = "Editar datos de la jornada") } },
                colors = coloresBarra()
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onExportar, enabled = j.mixers.isNotEmpty() && !generando,
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        if (generando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Exportar", fontWeight = FontWeight.SemiBold, color = AzulOscuro)
                    }
                    Button(
                        onClick = onAgregar, modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar mixer", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item { DatosJornada(j, onEditar) }
            item { Estadisticas(r) }
            if (j.mixers.isEmpty()) item { Vacio("Ningún mixer registrado", "Toca \"Agregar mixer\" cuando llegue el primero.") }
            itemsIndexed(j.mixers, key = { _, m -> m.id }) { i, m -> TarjetaMixer(i + 1, m, versionFotos, { onCamara(m) }) { onMixer(m) } }
        }
    }
}

@Composable
private fun DatosJornada(j: Jornada, onEditar: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            listOf("Nombre" to j.nombre, "Frente" to j.frente, "Tramo" to j.tramo, "Fecha" to T.fechaLarga(j.fecha)).forEach { (k, v) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(k, color = Color(0xFF56636C), modifier = Modifier.width(72.dp))
                    Text(v.ifBlank { "–" }, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Editar datos de la jornada", color = AzulMedio, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = onEditar).padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun Estadisticas(r: Resumen) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dato(r.n.toString(), "Mixers", Modifier.weight(1f))
        Dato(T.fmt(r.vol, 2), "m³ vaciados", Modifier.weight(1f))
        Dato(r.descargue?.roundToInt()?.toString() ?: "–", "min descargue prom.", Modifier.weight(1f))
    }
}

@Composable
private fun Dato(valor: String, etiqueta: String, modifier: Modifier) {
    Surface(color = Color(0xFFDCE7F6), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(valor, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AzulOscuro)
            Text(etiqueta, fontSize = 12.sp, color = AzulMedio)
        }
    }
}

@Composable
private fun TarjetaMixer(n: Int, m: Mixer, versionFotos: Int, onCamara: () -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(AzulOscuro, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text(n.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.codigo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val horas = listOfNotNull(
                    m.llegada.takeIf { it.isNotBlank() }?.let { "Llegó $it" },
                    m.inicio.takeIf { it.isNotBlank() }?.let { "descargue $it" + if (m.fin.isNotBlank()) " a ${m.fin}" else "" }
                ).joinToString(", ")
                Text(horas.ifBlank { "Sin horas" }, fontSize = 13.sp, color = Color(0xFF56636C))
                val det = listOfNotNull(
                    m.asObra.takeIf { it.isNotBlank() }?.let { "Asent. obra $it\"" },
                    m.temp.takeIf { it.isNotBlank() }?.let { "$it °C" },
                    m.loc.takeIf { it.isNotBlank() }
                ).joinToString(", ")
                if (det.isNotBlank()) Text(det, fontSize = 13.sp, color = Color(0xFF56636C))
                if (m.fin.isBlank()) Text("Falta hora de fin de descargue", fontSize = 12.sp, color = Aviso)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (m.cant.isNotBlank()) T.fmt(T.num(m.cant), 2) else "–", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AzulOscuro)
                Text("m³", fontSize = 12.sp, color = Color(0xFF56636C))
            }
            Spacer(Modifier.width(12.dp))
            BotonRemision(m.id, versionFotos, onCamara)
        }
    }
}

/** Cuadro de la remisión: cámara si no hay foto, miniatura si ya se tomó. */
@Composable
private fun BotonRemision(id: String, versionFotos: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val mini = remember(id, versionFotos) { Fotos.miniatura(ctx, id)?.asImageBitmap() }
    val forma = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(48.dp).clip(forma)
            .background(if (mini == null) Color(0xFFEEF3FA) else Color.White)
            .border(1.dp, if (mini == null) AzulClaro else AzulOscuro, forma)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (mini != null) {
            Image(mini, contentDescription = "Ver foto de la remisión", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_camara), contentDescription = "Tomar foto de la remisión", tint = AzulOscuro, modifier = Modifier.size(24.dp))
        }
    }
}
