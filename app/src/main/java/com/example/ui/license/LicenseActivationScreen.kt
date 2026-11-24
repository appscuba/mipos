package com.example.ui.license

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
  currentLicenseKey: String,
  onActivated: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var message by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var currentStatus by remember { mutableStateOf("pending") }

  Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Activación de Stock+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Copia tu ID de instalación y envíalo a PedritoTech. El administrador revisará tu solicitud y aprobará tu licencia. No necesitas introducir códigos.")
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ID ÚNICO DE INSTALACIÓN", style = MaterialTheme.typography.labelMedium)
        Text(deviceId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(deviceId)) }) { Text("Copiar ID") }
      }
    }
    Text(
      when (currentStatus) {
        "active" -> "Licencia aprobada. Stock+ está activado."
        "pending" -> "Solicitud pendiente de aprobación. Consulta nuevamente después de que PedritoTech la revise."
        "revoked" -> "Esta licencia fue revocada. Contacta con PedritoTech."
        "expired" -> "Tu licencia ha vencido. Solicita una renovación."
        else -> "Envía tu ID para solicitar la activación."
      },
      color = MaterialTheme.colorScheme.primary,
    )
    if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
    Button(
      onClick = {
        loading = true
        message = null
        scope.launch {
          val result = repository.requestApproval()
          loading = false
          currentStatus = result.status
          when (result.status) {
            "active" -> { message = "Licencia aprobada y validada."; onActivated() }
            "pending" -> message = result.message ?: "Solicitud recibida. Pendiente de aprobación."
            "not_found" -> message = "Este ID todavía no está registrado. Envíalo a PedritoTech para solicitar la licencia."
            "revoked" -> message = "La licencia está revocada."
            "expired" -> message = "La licencia ha vencido. Solicita renovación."
            else -> message = result.message ?: "No se pudo consultar el servidor."
          }
        }
      },
      enabled = !loading,
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (loading) CircularProgressIndicator() else Text("Consultar aprobación")
    }
    OutlinedButton(
      onClick = {
        val text = Uri.encode("Hola, quiero solicitar/aprobar una licencia de Stock+.
Mi ID único de instalación es: $deviceId")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5353274074?text=$text")))
      },
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Enviar ID por WhatsApp") }
  }
}
