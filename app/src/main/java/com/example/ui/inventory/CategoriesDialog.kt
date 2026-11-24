package com.example.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.db.entity.CategoryEntity

private val CATEGORY_COLORS = listOf(
  "#FFA510",
  "#1D2B50",
  "#38BDF8",
  "#22C55E",
  "#EF4444",
  "#A855F7",
)

@Composable
fun CategoriesDialog(
  categories: List<CategoryEntity>,
  onSaveCategory: (name: String, description: String, colorHex: String) -> Unit,
  onArchiveCategory: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var selectedColor by remember { mutableStateOf(CATEGORY_COLORS.first()) }
  var localError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Categorías de Productos") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            localError = null
          },
          label = { Text("Nueva categoría") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("category_name_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Descripción (opcional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Color:", style = MaterialTheme.typography.labelMedium)
          CATEGORY_COLORS.forEach { hex ->
            val parsedColor = parseHexColor(hex)
            val isSelected = selectedColor == hex
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(parsedColor)
                .border(
                  width = if (isSelected) 2.5.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                  shape = CircleShape,
                )
                .clickable { selectedColor = hex },
            )
          }
        }

        if (localError != null) {
          Text(
            text = localError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }

        Button(
          onClick = {
            if (name.isBlank()) {
              localError = "Ingresa el nombre de la categoría."
            } else {
              onSaveCategory(name.trim(), description.trim(), selectedColor)
              name = ""
              description = ""
            }
          },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("category_add_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Agregar Categoría")
        }

        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(categories, key = { it.id }) { cat ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Box(
                    modifier = Modifier
                      .size(12.dp)
                      .clip(CircleShape)
                      .background(parseHexColor(cat.colorHex)),
                  )
                  Column {
                    Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                    if (cat.description.isNotBlank()) {
                      Text(
                        cat.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
                IconButton(onClick = { onArchiveCategory(cat.id) }) {
                  Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Desactivar ${cat.name}",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Cerrar")
      }
    },
  )
}

fun parseHexColor(hex: String): Color = try {
  Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
  Color(0xFFFFA510)
}
