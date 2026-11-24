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
import androidx.compose.ui.unit.dp
import com.example.data.license.LicenseRepository
import kotlinx.coroutines.launch

@Composable
fun LicenseActivationScreen(
  repository: LicenseRepository,
  deviceId: String,
  currentPhone: String,
  onActivated: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var phone by remember(currentPhone) { mutableStateOf(currentPhone) }
  var message by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Activar licencia / Mejorar plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Tu instalación de Stock+ tiene un identificador único. Envíalo por WhatsApp para asociar tu licencia.")
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ID DE INSTALACIÓN", style = MaterialTheme.typography.labelMedium)
        Text(deviceId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(deviceId)) }) { Text("Copiar ID") }
      }
    }
    OutlinedTextField(
      value = phone,
      onValueChange = { phone = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Teléfono registrado") },
      singleLine = true,
    )
    if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {
          if (phone.isBlank()) {
            message = "Escribe el teléfono asociado a tu licencia."
          } else {
            loading = true
            message = null
            scope.launch {
              val result = repository.activate(phone.trim())
              loading = false
              when (result.status) {
                "active" -> { message = "Licencia activada correctamente."; onActivated() }
                "invalid" -> message = "Ese ID y teléfono no tienen una licencia activa. Escríbenos por WhatsApp."
                "revoked" -> message = "Esta licencia fue revocada. Contacta con soporte."
                "expired" -> message = "Esta licencia ha vencido. Renueva por WhatsApp."
                else -> message = result.message ?: "No se pudo validar la licencia. Comprueba tu conexión."
              }
            }
          }
        },
        enabled = !loading,
      ) {
        if (loading) CircularProgressIndicator() else Text("Activar")
      }
      OutlinedButton(
        onClick = {
          val text = Uri.encode("Hola, quiero activar Stock+. Mi ID es $deviceId y mi teléfono es $phone")
          val uri = Uri.parse("https://wa.me/5353274074?text=$text")
          context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        },
      ) { Text("WhatsApp") }
    }
  }
}
