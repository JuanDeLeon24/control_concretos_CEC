package co.obra.controlconcreto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

/** Acción protegida que espera el login de administrador. */
class Autorizacion(val motivo: String, val accion: () -> Unit)

/**
 * Login de administrador. Se pide en cada modificación protegida y NO se guarda en ninguna parte:
 * ni sesión, ni usuario, ni contraseña. Las credenciales no están escritas en el código, solo su huella SHA-256.
 */
object Admin {
    private const val HUELLA = "c3779fd0392ef3991a7c97d20ea7c57bb23c627e55097c525a4f3deca6e9ab6b"

    fun valido(usuario: String, clave: String): Boolean {
        val txt = usuario.trim().lowercase() + "\n" + clave
        val h = MessageDigest.getInstance("SHA-256").digest(txt.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return h == HUELLA
    }
}

@Composable
fun DialogoAdmin(motivo: String, onCancelar: () -> Unit, onAutorizado: () -> Unit) {
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var ver by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun entrar() {
        if (Admin.valido(usuario, clave)) {
            usuario = ""; clave = ""
            onAutorizado()
        } else {
            error = true; clave = ""
        }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Acceso de administrador") },
        text = {
            Column {
                Text("Para $motivo se necesita autorización.", fontSize = 14.sp, color = Color(0xFF56636C),
                    modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    value = usuario, onValueChange = { usuario = it; error = false },
                    label = { Text("ID de usuario") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = clave, onValueChange = { clave = it; error = false },
                    label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ocultar contraseña" else "Mostrar contraseña", color = AzulMedio) }
                if (error) Text("Usuario o contraseña incorrectos", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = { entrar() }, colors = ButtonDefaults.buttonColors(containerColor = AzulOscuro)) { Text("Entrar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
