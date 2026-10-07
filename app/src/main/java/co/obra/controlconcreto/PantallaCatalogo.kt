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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Pantalla para administrar el catálogo de frentes y tramos (plantilla, cargar, ver y eliminar). */
@Composable
fun PantallaCatalogo(
    catalogo: List<ItemCatalogo>,
    elementos: List<String>,
    mensaje: String?,
    cargando: Boolean,
    onCerrar: () -> Unit,
    onPlantillaExcel: () -> Unit,
    onPlantillaCsv: () -> Unit,
    onCargar: () -> Unit,
    onEliminar: () -> Unit
) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Fondo) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar") } },
                    title = { Text("Frentes y tramos", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AzulOscuro, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        "Carga una lista fija de frentes, tramos, abscisas, módulos y elementos de vaciado. Al crear una jornada podrás " +
                            "elegir entre esa lista o escribir libre. Sin catálogo, la app funciona igual que siempre.",
                        color = Color(0xFF56636C), fontSize = 14.sp
                    )

                    mensaje?.let {
                        Surface(
                            color = AvisoFondo, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text(it, color = Aviso, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
                    }

                    Titulo("1. Plantilla")
                    Text("Descárgala, llénala en Excel (o en el teléfono) y guárdala.", fontSize = 14.sp, color = Color(0xFF56636C))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = onPlantillaExcel, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Plantilla Excel", color = AzulOscuro)
                        }
                        OutlinedButton(onClick = onPlantillaCsv, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Plantilla CSV", color = AzulOscuro)
                        }
                    }

                    if (catalogo.isEmpty() && elementos.isEmpty()) {
                        Titulo("2. Cargar catálogo")
                        Text("No hay catálogo cargado.", fontSize = 14.sp, color = Color(0xFF56636C))
                        Button(
                            onClick = onCargar, enabled = !cargando,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)
                        ) {
                            if (cargando) CircularProgressIndicator(Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Cargar archivo (Excel o CSV)", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        CatalogoCargado(catalogo, elementos)
                        Text(
                            "Para subir un catálogo nuevo, primero elimina el actual. Las jornadas ya registradas no se modifican.",
                            fontSize = 13.sp, color = Color(0xFF56636C), modifier = Modifier.padding(top = 12.dp)
                        )
                        OutlinedButton(
                            onClick = onEliminar,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp).height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Eliminar catálogo cargado") }
                    }
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
private fun CatalogoCargado(catalogo: List<ItemCatalogo>, elementos: List<String>) {
    val porFrente = remember(catalogo) { catalogo.groupBy { it.frente } }
    Titulo("2. Catálogo cargado")
    Text(
        "${catalogo.size} ${if (catalogo.size == 1) "lugar" else "lugares"} en ${porFrente.size} " +
            if (porFrente.size == 1) "frente" else "frentes",
        fontWeight = FontWeight.SemiBold, color = AzulOscuro
    )
    porFrente.forEach { (frente, items) ->
        Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(frente, fontWeight = FontWeight.Bold, color = AzulOscuro, fontSize = 16.sp)
                items.groupBy { it.tramo }.forEach { (tramo, lugares) ->
                    val conLugar = lugares.count { Catalogo.etiqueta(it).isNotBlank() }
                    Text(
                        (tramo.ifBlank { "(sin tramo)" }) + "  ·  " +
                            "$conLugar ${if (conLugar == 1) "módulo" else "módulos"}",
                        fontSize = 14.sp, color = Color(0xFF56636C), modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Elementos de vaciado", fontWeight = FontWeight.Bold, color = AzulOscuro, fontSize = 16.sp)
            Text(
                if (elementos.isEmpty()) "No se cargaron elementos (columna Elemento vaciado vacía)."
                else elementos.joinToString(" · ") + " · Otro...",
                fontSize = 14.sp, color = Color(0xFF56636C), modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
