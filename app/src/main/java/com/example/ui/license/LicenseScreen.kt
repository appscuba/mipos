package com.example.ui.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MonoMetricMedium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** WhatsApp comercial de PedritoTech (contacto público de ventas). */
private const val SELLER_WHATSAPP = "5353274074"

@Composable
fun LicenseScreen(
  viewModel: LicenseViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var copied by remember { mutableStateOf(false) }
  var showKeyForm by remember { mutableStateOf(false) }
  val isActive = state.status == "active"

  // Al abrir la pantalla se revalida contra LicenseHub si hay clave.
  LaunchedEffect(Unit) { viewModel.checkOnOpen() }

  // Con la licencia activa manda la tarjeta de estado: si el
  // formulario estaba desplegado («Usar otra clave»), se repliega.
  LaunchedEffect(isActive) { if (isActive) showKeyForm = false }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier
        .widthIn(max = 520.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Spacer(Modifier.height(12.dp))

      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.size(64.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Filled.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp),
          )
        }
      }

      Text(
        if (isActive) "Licencia activa" else "Activa tu Stock+",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      if (!isActive) {
        Text(
          text = "Stock+ funciona con una licencia por periodos (mensual o el tiempo que contrates). " +
            "Envía a PedritoTech el ID de esta instalación por WhatsApp; cuando registremos tu compra, " +
            "te enviamos tu clave de licencia: escríbela aquí y pulsa Activar. La clave queda ligada " +
            "a este dispositivo.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (isActive) {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.testTag("license_active_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
              text = if (state.expiresAt > 0L) {
                "Tu licencia está activa. Válida hasta el ${formatDate(state.expiresAt)}."
              } else {
                "Tu licencia está activa. Licencia permanente."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }

      if (state.status == "expired" || state.status == "revoked" || state.status == "suspended") {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
              text = when (state.status) {
                "expired" -> "Tu licencia venció" +
                  (state.expiresAt.takeIf { it > 0L }?.let { " el ${formatDate(it)}" } ?: "") +
                  ". Renuévala con PedritoTech y vuelve a pulsar Activar."
                "revoked" -> "Esta licencia fue revocada. Contacta a PedritoTech."
                else -> "Tu licencia está suspendida. Contacta a PedritoTech."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      // Tarjeta del ID de instalación: ancho completo y alineada con el
      // resto del formulario, borde fino ámbar y superficie un punto más
      // clara que el fondo. El ID es el protagonista; se copia tocando la
      // tarjeta o el botón de icono.
      val copyId: () -> Unit = {
        if (state.deviceId.isNotBlank()) {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(ClipData.newPlainText("ID Stock+", state.deviceId))
          copied = true
        }
      }
      Card(
        onClick = copyId,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            "TU ID DE INSTALACIÓN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = state.deviceId.ifBlank { "Generando…" },
              style = MonoMetricMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f).testTag("license_device_id"),
            )
            IconButton(onClick = copyId, enabled = state.deviceId.isNotBlank()) {
              Icon(
                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = "Copiar ID",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
          if (copied) {
            Text(
              "¡Copiado! Pégalo donde lo necesites.",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      if (!isActive || showKeyForm) {
        OutlinedTextField(
          value = state.licenseKey,
          onValueChange = viewModel::onLicenseKeyChange,
          label = { Text("Tu clave de licencia") },
          placeholder = { Text("La que te envió PedritoTech") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("license_key_input"),
        )

        if (state.message != null) {
          Text(
            text = state.message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
          )
        }

        Button(
          onClick = viewModel::activate,
          enabled = !state.isChecking && state.deviceId.isNotBlank(),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("license_activate_button"),
        ) {
          if (state.isChecking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Comprobando…")
          } else {
            Text("Activar", fontWeight = FontWeight.Bold)
          }
        }
      } else {
        // Licencia activa y formulario replegado: vía discreta para
        // escribir otra clave (p. ej. tras desvincular la anterior
        // en el panel de LicenseHub).
        TextButton(
          onClick = { showKeyForm = true },
          modifier = Modifier.testTag("license_use_another_key_button"),
        ) {
          Text("Usar otra clave")
        }
      }

      OutlinedButton(
        onClick = {
          val text = "Hola PedritoTech, quiero mi licencia de Stock+. Mi ID de instalación es ${state.deviceId}."
          val url = "https://wa.me/$SELLER_WHATSAPP?text=" + Uri.encode(text)
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        enabled = state.deviceId.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("license_whatsapp_button"),
      ) {
        Text("Pedir mi licencia por WhatsApp")
      }

      Text(
        text = "La licencia se valida una vez contra LicenseHub y queda ligada a este " +
          "dispositivo. Después el tiempo corre en tu teléfono, protegido contra " +
          "cambios de hora; al conectarte, la app vuelve a confirmarla con el servidor.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun formatDate(epochMs: Long): String =
  SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es")).format(Date(epochMs))
