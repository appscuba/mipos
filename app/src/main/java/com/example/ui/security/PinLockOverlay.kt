package com.example.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.repository.ActiveUserSession

@Composable
fun PinLockOverlay(
  businessName: String,
  session: ActiveUserSession,
  onUnlock: (pin: String, onResult: (Boolean) -> Unit) -> Unit,
  modifier: Modifier = Modifier,
) {
  var pin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Sesión bloqueada",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
          )
          Text(businessName, style = MaterialTheme.typography.titleLarge)
          Text(
            text = "${session.user?.fullName ?: "Usuario"} (${session.role?.name ?: ""})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(4.dp))
          OutlinedTextField(
            value = pin,
            onValueChange = {
              pin = it.filter { ch -> ch.isDigit() }.take(8)
              error = null
            },
            label = { Text("Ingresa tu PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("pin_lock_input"),
          )
          if (error != null) {
            Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
          Button(
            onClick = {
              onUnlock(pin) { ok ->
                if (!ok) {
                  error = "PIN incorrecto. Intenta nuevamente."
                } else {
                  pin = ""
                }
              }
            },
            enabled = pin.length >= 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pin_lock_unlock_button"),
          ) {
            Text("Desbloquear")
          }
        }
      }
    }
  }
}
