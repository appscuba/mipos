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
  savedPhone: String = "",
  onActivated: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var phone by remember(savedPhone) { mutableStateOf(savedPhone) }
  var message by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var currentStatus by remember { mutableStateOf("") }

  Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Activación de Stock+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Introduce el mismo teléfono que registraste con PedritoTech. La licencia se comprueba junto con el ID único de este dispositivo.")
    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ID ÚNICO DE INSTALACIÓN", style = MaterialTheme.typography.labelMedium)
        Text(deviceId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(deviceId)) }) { Text("Copiar ID") }
      }
    }
    OutlinedTextField(
      value = phone,
      onValueChange = { phone = it; message = null },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Teléfono registrado") },
      placeholder = { Text("+53 5XXXXXXX") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
    )
    if (currentStatus.isNotBlank()) {
      Text(
        when (currentStatus) {
          "active" -> "Licencia activa. Stock+ está autorizado."
          "invalid" -> "El ID o el teléfono no coincide con una licencia registrada. Comprueba los datos o contacta con PedritoTech."
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
          val result = repository.requestApproval(phone)
          loading = false
          currentStatus = result.status
          when (result.status) {
            "active" -> { message = "Licencia validada correctamente."; onActivated() }
            "invalid" -> message = result.message ?: "ID o teléfono no registrado. Envía ambos datos a PedritoTech."
            "revoked" -> message = result.message ?: "La licencia está revocada."
            "expired" -> message = result.message ?: "La licencia ha vencido. Solicita renovación."
            else -> message = result.message ?: "No se pudo consultar el servidor."
          }
        }
      },
      enabled = !loading && phone.isNotBlank(),
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (loading) CircularProgressIndicator() else Text("Validar licencia")
    }
    OutlinedButton(
      onClick = {
        val text = Uri.encode("Hola, quiero solicitar o renovar una licencia de Stock+.\nID de instalación: $deviceId\nTeléfono: $phone")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5353274074?text=$text")))
      },
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Enviar datos por WhatsApp") }
  }
}
