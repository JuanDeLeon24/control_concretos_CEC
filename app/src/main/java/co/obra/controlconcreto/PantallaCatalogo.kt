@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.controlconcreto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Pantalla para administrar el catálogo (plantilla, cargar archivo, escribir en la app, ver y eliminar). */
@Composable
fun PantallaCatalogo(
    cfg: Config,
    catalogo: List<ItemCatalogo>,
    elementos: List<String>,
    mensaje: String?,
    cargando: Boolean,
    onCerrar: () -> Unit,
    onPlantillaExcel: () -> Unit,
    onPlantillaCsv: () -> Unit,
    onCargar: () -> Unit,
    onGuardarTexto: (String, String) -> Unit,
    onEliminar: () -> Unit
) {
    var editor by remember { mutableStateOf(false) }
    val fr = cfg.etFrente.lowercase(); val tr = cfg.etTramo.lowercase(); val lo = cfg.etLoc.lowercase()
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Fondo) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar") } },
                    title = { Text("Catálogo", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AzulOscuro, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        "Lista fija de ${fr}s con sus ${tr}s, y la lista de ${lo} (HD, HI, BV… o lo que necesites). " +
                            "Al crear una jornada podrás elegir de la lista o escribir otro ${fr} o actividad. " +
                            "Sin catálogo, la app funciona igual que siempre.",
                        color = Color(0xFF56636C), fontSize = 14.sp
                    )

                    mensaje?.let {
                        Surface(
                            color = AvisoFondo, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text(it, color = Aviso, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
                    }

                    Titulo("1. Escribirlo en la app (recomendado)")
                    Text(
                        "No necesita archivos. Sirve también en teléfonos de la empresa donde Microsoft protege (cifra) los archivos de Excel.",
                        fontSize = 14.sp, color = Color(0xFF56636C)
                    )
                    Button(
                        onClick = { editor = true }, enabled = !cargando,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text(if (catalogo.isEmpty() && elementos.isEmpty()) "Escribir catálogo" else "Editar catálogo", fontWeight = FontWeight.SemiBold) }

                    Titulo("2. O cargarlo desde un archivo")
                    Text(
                        "Columnas: ${cfg.etFrente} | ${cfg.etTramo} | ${cfg.etLoc}. Repite el ${fr} en cada ${tr} que le pertenece. " +
                            "La columna ${cfg.etLoc} es una lista aparte (una por fila).",
                        fontSize = 14.sp, color = Color(0xFF56636C)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = onPlantillaExcel, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Plantilla Excel", color = AzulOscuro)
                        }
                        OutlinedButton(onClick = onPlantillaCsv, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Plantilla CSV", color = AzulOscuro)
                        }
                    }
                    OutlinedButton(
                        onClick = onCargar, enabled = !cargando,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp)
                    ) {
                        if (cargando) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        else Text("Cargar archivo (reemplaza el catálogo actual)", color = AzulOscuro)
                    }

                    if (catalogo.isNotEmpty() || elementos.isNotEmpty()) {
                        CatalogoCargado(cfg, catalogo, elementos)
                        OutlinedButton(
                            onClick = onEliminar,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 24.dp).height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Eliminar catálogo") }
                    } else Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (editor) EditorCatalogo(
        cfg = cfg,
        frentesInicial = Catalogo.textoFrentes(catalogo),
        locsInicial = elementos.joinToString("\n"),
        onCancelar = { editor = false },
        onGuardar = { f, l -> editor = false; onGuardarTexto(f, l) }
    )
}

@Composable
private fun EditorCatalogo(
    cfg: Config,
    frentesInicial: String,
    locsInicial: String,
    onCancelar: () -> Unit,
    onGuardar: (String, String) -> Unit
) {
    var frentes by remember { mutableStateOf(frentesInicial) }
    var locs by remember { mutableStateOf(locsInicial) }
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onCancelar) { Icon(Icons.Default.Close, contentDescription = "Cancelar") } },
                    title = { Text("Escribir catálogo", fontWeight = FontWeight.Bold) },
                    actions = { TextButton(onClick = { onGuardar(frentes, locs) }) { Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AzulOscuro, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("${cfg.etFrente}s y ${cfg.etTramo.lowercase()}s", fontWeight = FontWeight.SemiBold, color = AzulOscuro)
                    Text(
                        "Un renglón por ${cfg.etTramo.lowercase()}: ${cfg.etFrente} ; ${cfg.etTramo}\nEjemplo:\nTUNEL 0 ; Tramo 1\nTUNEL 0 ; Tramo 2\nTUNEL 1 ; Tramo 1",
                        fontSize = 13.sp, color = Color(0xFF56636C), modifier = Modifier.padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = frentes, onValueChange = { frentes = it }, minLines = 8,
                        label = { Text("${cfg.etFrente} ; ${cfg.etTramo}") }, modifier = Modifier.fillMaxWidth()
                    )
                    Text(cfg.etLoc, fontWeight = FontWeight.SemiBold, color = AzulOscuro, modifier = Modifier.padding(top = 18.dp))
                    Text(
                        "Una por renglón. Ejemplo: HD, HI, BV, BV-HD, BV-HI, Ambos hastiales, Revestimiento completo. " +
                            "\"Otro...\" se agrega solo.",
                        fontSize = 13.sp, color = Color(0xFF56636C), modifier = Modifier.padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = locs, onValueChange = { locs = it }, minLines = 7,
                        label = { Text(cfg.etLoc) }, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onGuardar(frentes, locs) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                    ) { Text("Guardar catálogo", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun Titulo(t: String) {
    Text(t, color = AzulMedio, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
    HorizontalDivider(color = AzulClaro)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CatalogoCargado(cfg: Config, catalogo: List<ItemCatalogo>, elementos: List<String>) {
    val porFrente = remember(catalogo) { catalogo.groupBy { it.frente } }
    Titulo("Catálogo actual")
    Text(
        "${porFrente.size} ${cfg.etFrente.lowercase()}${if (porFrente.size == 1) "" else "s"}  ·  ${elementos.size} opciones de ${cfg.etLoc.lowercase()}",
        fontWeight = FontWeight.SemiBold, color = AzulOscuro
    )
    porFrente.forEach { (frente, items) ->
        val tramos = items.map { it.tramo }.filter { it.isNotBlank() }.distinct()
        Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(frente, fontWeight = FontWeight.Bold, color = AzulOscuro, fontSize = 16.sp)
                Text(
                    if (tramos.isEmpty()) "Sin ${cfg.etTramo.lowercase()}s"
                    else "${tramos.size} ${cfg.etTramo.lowercase()}${if (tramos.size == 1) "" else "s"}: " + tramos.joinToString(", "),
                    fontSize = 14.sp, color = Color(0xFF56636C), modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(cfg.etLoc, fontWeight = FontWeight.Bold, color = AzulOscuro, fontSize = 16.sp)
            Text(
                if (elementos.isEmpty()) "Sin lista: se escribe libre en cada mixer."
                else elementos.joinToString(" · ") + " · Otro...",
                fontSize = 14.sp, color = Color(0xFF56636C), modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
