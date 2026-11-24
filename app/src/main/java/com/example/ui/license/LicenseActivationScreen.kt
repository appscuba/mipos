package com.example.ui.license

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.data.license.LicenseRepository
import kotlinx.coroutines.launch

@Composable
fun LicenseActivationScreen(
  repository: LicenseRepository,
  deviceId: String,
  currentLicenseKey: String,
  onActivated: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var licenseKey by remember(currentLicenseKey) { mutableStateOf(currentLicenseKey) }
  var message by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Activar licencia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Tu instalación de Stock+ tiene un identificador único. Envíalo a PedritoTech para recibir tu clave de licencia. Después, introdúcela aquí para activar tu plan.")
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ID DE INSTALACIÓN", style = MaterialTheme.typography.labelMedium)
        Text(deviceId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(deviceId)) }) { Text("Copiar ID") }
      }
    }
    OutlinedTextField(
      value = licenseKey,
      onValueChange = {
        licenseKey = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(80)
        message = null
      },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Clave de licencia") },
      placeholder = { Text("Introduce la clave recibida") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        keyboardType = KeyboardType.Ascii,
      ),
    )
    if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {
          if (licenseKey.isBlank()) {
            message = "Introduce la clave de licencia que te proporcionó PedritoTech."
          } else {
            loading = true
            message = null
            scope.launch {
              val result = repository.activate(licenseKey.trim())
              loading = false
              when (result.status) {
                "active" -> { message = "Licencia activada correctamente."; onActivated() }
                "invalid" -> message = "La clave de licencia no existe o no es válida."
                "revoked" -> message = "Esta licencia fue revocada. Contacta con soporte."
                "expired" -> message = "Esta licencia ha vencido. Solicita una renovación."
                else -> message = result.message ?: "No se pudo validar la licencia. Comprueba tu conexión."
              }
            }
          }
        },
        enabled = !loading,
      ) {
        if (loading) CircularProgressIndicator() else Text("Activar licencia")
      }
      OutlinedButton(
        onClick = {
          val text = Uri.encode("Hola, quiero solicitar una licencia de Stock+. Mi ID de instalación es $deviceId")
          val uri = Uri.parse("https://wa.me/5353274074?text=$text")
          context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        },
      ) { Text("WhatsApp") }
    }
  }
}
