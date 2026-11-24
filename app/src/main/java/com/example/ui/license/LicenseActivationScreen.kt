package com.example.ui.license

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.license.LicenseRepository
import kotlinx.coroutines.launch

@Composable
fun LicenseActivationScreen(
  repository: LicenseRepository,
  deviceId: String,
  savedLicenseKey: String = "",
  onActivated: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var licenseKey by remember(savedLicenseKey) { mutableStateOf(savedLicenseKey) }
  var message by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var currentStatus by remember { mutableStateOf("") }

  Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Activación de Stock+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Introduce la clave de licencia que recibiste al comprar tu plan. El ID identifica este dispositivo y se vincula a la licencia.")
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ID ÚNICO DE INSTALACIÓN", style = MaterialTheme.typography.labelMedium)
        Text(deviceId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(deviceId)) }) { Text("Copiar ID del dispositivo") }
      }
    }
    OutlinedTextField(
      value = licenseKey,
      onValueChange = { licenseKey = it; message = null },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Clave de licencia") },
      placeholder = { Text("Introduce la licencia generada por PedritoTech") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
      singleLine = true,
    )
    if (currentStatus.isNotBlank()) {
      Text(
        when (currentStatus) {
          "active" -> "Licencia activa. Stock+ está autorizado."
          "invalid" -> "La clave de licencia no es válida para este dispositivo. Comprueba la clave o contacta con PedritoTech."
          "revoked" -> "Esta licencia fue revocada. Contacta con PedritoTech."
          "expired" -> "Tu licencia ha vencido. Solicita una renovación."
          else -> "No se pudo verificar la licencia."
        },
        color = if (currentStatus == "active") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      )
    }
    if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
    Button(
      onClick = {
        loading = true
        message = null
        scope.launch {
          val result = repository.requestApproval(licenseKey)
          loading = false
          currentStatus = result.status
          when (result.status) {
            "active" -> { message = "Licencia validada correctamente."; onActivated() }
            "invalid" -> message = result.message ?: "Clave de licencia no registrada. Comprueba la clave."
            "revoked" -> message = result.message ?: "La licencia está revocada."
            "expired" -> message = result.message ?: "La licencia ha vencido. Solicita renovación."
            else -> message = result.message ?: "No se pudo consultar el servidor."
          }
        }
      },
      enabled = !loading && licenseKey.isNotBlank(),
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (loading) CircularProgressIndicator() else Text("Validar licencia")
    }
    OutlinedButton(
      onClick = {
        val text = Uri.encode("Hola, necesito ayuda con mi licencia de Stock+.\\nID de instalación: $deviceId\\nClave de licencia: $licenseKey")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5353274074?text=$text")))
      },
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Enviar datos por WhatsApp") }
  }
}
