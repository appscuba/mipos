package com.example.ui.shell

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ModuleRoadmapScreen(
  title: String,
  phaseLabel: String,
  description: String,
  icon: ImageVector,
  onNavigateHome: () -> Unit,
  onNavigateSettings: () -> Unit,
  modifier: Modifier = Modifier,
  primaryButtonLabel: String = "Volver a Inicio",
  secondaryButtonLabel: String = "Cambiar de Usuario",
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(68.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = title,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(34.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Text(
            text = phaseLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }

        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )

        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Button(
          onClick = onNavigateHome,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Text(primaryButtonLabel)
        }

        OutlinedButton(
          onClick = onNavigateSettings,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Text(secondaryButtonLabel)
        }
      }
    }
  }
}
