@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.controlconcreto

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min
import kotlin.math.roundToInt

private val FondoOscuro = Color(0xFF0B1620)

@Composable
private fun barraOscura() = TopAppBarDefaults.topAppBarColors(
    containerColor = FondoOscuro, titleContentColor = Color.White,
    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
)

private fun esquinasIniciales(b: Bitmap): List<PointF> {
    val mx = b.width * 0.06f; val my = b.height * 0.06f
    return listOf(
        PointF(mx, my), PointF(b.width - mx, my),
        PointF(b.width - mx, b.height - my), PointF(mx, b.height - my)
    )
}

/** Ajuste del recorte: 4 esquinas arrastrables con lupa; al guardar se endereza y se aplica el filtro. */
@Composable
fun PantallaRecorte(
    original: Bitmap,
    procesando: Boolean,
    onCancelar: () -> Unit,
    onRepetir: () -> Unit,
    onGuardar: (Bitmap, List<PointF>) -> Unit
) {
    var bmp by remember(original) { mutableStateOf(original) }
    var esquinas by remember(bmp) { mutableStateOf(esquinasIniciales(bmp)) }
    var activo by remember { mutableIntStateOf(-1) }
    var tam by remember { mutableStateOf(IntSize.Zero) }
    val img = remember(bmp) { bmp.asImageBitmap() }

    Dialog(
        onDismissRequest = { if (!procesando) onCancelar() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = FondoOscuro) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { if (!procesando) onCancelar() }) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
                    },
                    title = { Text("Ajusta el recorte", fontWeight = FontWeight.Bold) },
                    colors = barraOscura()
                )
                Text(
                    "Arrastra los cuatro puntos a las esquinas de la remisión",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(18.dp).onSizeChanged { tam = it }
                ) {
                    if (tam.width > 0 && tam.height > 0) {
                        val s = min(tam.width / bmp.width.toFloat(), tam.height / bmp.height.toFloat())
                        val ox = (tam.width - bmp.width * s) / 2f
                        val oy = (tam.height - bmp.height * s) / 2f
                        Image(img, contentDescription = "Foto de la remisión", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(bmp, tam) {
                                val radioToque = 44.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { p ->
                                        val pts = esquinas.map { Offset(ox + it.x * s, oy + it.y * s) }
                                        val i = pts.indices.minByOrNull { (pts[it] - p).getDistance() } ?: -1
                                        activo = if (i >= 0 && (pts[i] - p).getDistance() < radioToque) i else -1
                                    },
                                    onDragEnd = { activo = -1 },
                                    onDragCancel = { activo = -1 },
                                    onDrag = { change, d ->
                                        if (activo >= 0) {
                                            change.consume()
                                            val e = esquinas.toMutableList()
                                            val q = e[activo]
                                            e[activo] = PointF(
                                                (q.x + d.x / s).coerceIn(0f, bmp.width.toFloat()),
                                                (q.y + d.y / s).coerceIn(0f, bmp.height.toFloat())
                                            )
                                            esquinas = e
                                        }
                                    }
                                )
                            }
                        ) {
                            val pts = esquinas.map { Offset(ox + it.x * s, oy + it.y * s) }
                            val marco = Path().apply {
                                moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
                            }
                            val fuera = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addPath(marco)
                            }
                            drawPath(fuera, Color.Black.copy(alpha = 0.5f))
                            drawPath(marco, AzulClaro, style = Stroke(width = 2.dp.toPx()))
                            pts.forEachIndexed { i, p ->
                                val r = if (i == activo) 15.dp.toPx() else 11.dp.toPx()
                                drawCircle(Color.White, r, p)
                                drawCircle(AzulOscuro, r, p, style = Stroke(3.dp.toPx()))
                            }

                            // Lupa para ajustar con precisión
                            if (activo >= 0) {
                                val q = esquinas[activo]
                                val lupa = 120.dp.toPx(); val zoom = 2.5f
                                val lado = min(lupa / (s * zoom), min(bmp.width, bmp.height).toFloat())
                                val sx = (q.x - lado / 2).coerceIn(0f, bmp.width - lado)
                                val sy = (q.y - lado / 2).coerceIn(0f, bmp.height - lado)
                                val margen = 8.dp.toPx()
                                val centro = if (pts[activo].x < size.width / 2)
                                    Offset(size.width - lupa / 2 - margen, lupa / 2 + margen)
                                else Offset(lupa / 2 + margen, lupa / 2 + margen)
                                val tl = centro - Offset(lupa / 2, lupa / 2)
                                clipPath(Path().apply { addOval(Rect(centro, lupa / 2)) }) {
                                    drawRect(Color.Black, tl, Size(lupa, lupa))
                                    drawImage(
                                        img,
                                        srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                                        srcSize = IntSize(lado.roundToInt(), lado.roundToInt()),
                                        dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                                        dstSize = IntSize(lupa.roundToInt(), lupa.roundToInt())
                                    )
                                }
                                drawCircle(Color.White, lupa / 2, centro, style = Stroke(2.dp.toPx()))
                                val k = lupa / lado
                                val cruz = Offset(tl.x + (q.x - sx) * k, tl.y + (q.y - sy) * k)
                                val l = 9.dp.toPx()
                                drawLine(AzulClaro, cruz - Offset(l, 0f), cruz + Offset(l, 0f), 2.dp.toPx())
                                drawLine(AzulClaro, cruz - Offset(0f, l), cruz + Offset(0f, l), 2.dp.toPx())
                            }
                        }
                    }
                    if (procesando) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(12.dp))
                                Text("Aplicando filtro de escaneo…", color = Color.White)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onRepetir, enabled = !procesando, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Repetir", color = Color.White)
                    }
                    OutlinedButton(onClick = { bmp = Fotos.rotar90(bmp) }, enabled = !procesando, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Girar", color = Color.White)
                    }
                    Button(
                        onClick = { onGuardar(bmp, esquinas) }, enabled = !procesando,
                        modifier = Modifier.weight(1.3f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulClaro, contentColor = AzulOscuro)
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Visor de la remisión guardada, con zoom con dos dedos. */
@Composable
fun VerFoto(
    bmp: Bitmap,
    titulo: String,
    onCerrar: () -> Unit,
    onCamara: () -> Unit,
    onGaleria: () -> Unit,
    onCompartir: () -> Unit,
    onEliminar: () -> Unit
) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    var escala by remember { mutableFloatStateOf(1f) }
    var desp by remember { mutableStateOf(Offset.Zero) }
    val gestos = rememberTransformableState { z, pan, _ ->
        escala = (escala * z).coerceIn(1f, 6f)
        desp = if (escala == 1f) Offset.Zero else desp + pan
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = FondoOscuro) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar") } },
                    title = { Text(titulo, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onCompartir) { Icon(Icons.Default.Share, contentDescription = "Compartir foto") }
                        IconButton(onClick = onEliminar) { Icon(Icons.Default.Delete, contentDescription = "Eliminar foto") }
                    },
                    colors = barraOscura()
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).transformable(gestos)) {
                    Image(
                        img, contentDescription = "Remisión", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = escala, scaleY = escala, translationX = desp.x, translationY = desp.y
                        )
                    )
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCamara, modifier = Modifier.weight(1f).height(52.dp)) { Text("Tomar de nuevo", color = Color.White) }
                    OutlinedButton(onClick = onGaleria, modifier = Modifier.weight(1f).height(52.dp)) { Text("Desde galería", color = Color.White) }
                }
            }
        }
    }
}
