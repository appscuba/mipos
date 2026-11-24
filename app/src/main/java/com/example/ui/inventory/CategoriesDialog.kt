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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
  productCountByCategory: Map<Long, Int> = emptyMap(),
  onSaveCategory: (categoryId: Long?, name: String, description: String, colorHex: String) -> Unit,
  onDeleteCategory: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  var editingCategoryId by remember { mutableStateOf<Long?>(null) }
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var selectedColor by remember { mutableStateOf(CATEGORY_COLORS.first()) }
  var localError by remember { mutableStateOf<String?>(null) }
  var pendingDeleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .widthIn(max = 560.dp)
        .heightIn(max = 760.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (editingCategoryId == null) "Categorías de Productos" else "Editar Categoría",
            style = MaterialTheme.typography.titleLarge,
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar categorías")
          }
        }
        HorizontalDivider()

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = {
              name = it
              localError = null
            },
            label = { Text(if (editingCategoryId == null) "Nueva categoría" else "Nombre de categoría") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("category_name_input"),
          )
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("category_description_input"),
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

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (editingCategoryId != null) {
              OutlinedButton(
                onClick = {
                  editingCategoryId = null
                  name = ""
                  description = ""
                  selectedColor = CATEGORY_COLORS.first()
                  localError = null
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("category_cancel_edit_btn"),
              ) {
                Text("Cancelar")
              }
            }
            Button(
              onClick = {
                if (name.isBlank()) {
                  localError = "Ingresa el nombre de la categoría."
                } else {
                  onSaveCategory(editingCategoryId, name.trim(), description.trim(), selectedColor)
                  editingCategoryId = null
                  name = ""
                  description = ""
                  selectedColor = CATEGORY_COLORS.first()
                  localError = null
                }
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).testTag("category_add_button"),
            ) {
              Icon(
                imageVector = if (editingCategoryId == null) Icons.Filled.Add else Icons.Filled.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(if (editingCategoryId == null) "Agregar Categoría" else "Guardar Cambios")
            }
          }

          HorizontalDivider()

          if (categories.isEmpty()) {
            Text(
              text = "No hay categorías registradas.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          } else {
            categories.forEach { cat ->
              val isBeingEdited = editingCategoryId == cat.id
              val productCount = productCountByCategory[cat.id] ?: 0
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (isBeingEdited) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant
                  },
                ),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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
                      Text(
                        cat.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                      )
                      val subtitle = buildList {
                        add("$productCount prod.")
                        if (cat.description.isNotBlank()) add(cat.description)
                      }.joinToString(" • ")
                      Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                      onClick = {
                        editingCategoryId = cat.id
                        name = cat.name
                        description = cat.description
                        selectedColor = cat.colorHex
                        localError = null
                      },
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("category_edit_btn_${cat.id}"),
                    ) {
                      Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Editar ${cat.name}",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                    IconButton(
                      onClick = { pendingDeleteCategory = cat },
                      modifier = Modifier.minimumInteractiveComponentSize().testTag("category_delete_btn_${cat.id}"),
                    ) {
                      Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar ${cat.name}",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cerrar")
          }
        }
      }
    }
  }

  pendingDeleteCategory?.let { cat ->
    val count = productCountByCategory[cat.id] ?: 0
    AlertDialog(
      onDismissRequest = { pendingDeleteCategory = null },
      title = { Text("Eliminar categoría") },
      text = {
        Text(
          if (count > 0) {
            "¿Eliminar la categoría '${cat.name}'?\n\nHay $count producto(s) asignados; los productos NO se borrarán, solo quedarán sin categoría."
          } else {
            "¿Seguro que deseas eliminar la categoría '${cat.name}'?"
          },
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (editingCategoryId == cat.id) {
              editingCategoryId = null
              name = ""
              description = ""
            }
            onDeleteCategory(cat.id)
            pendingDeleteCategory = null
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier.testTag("category_delete_confirm_button"),
        ) {
          Text("Eliminar")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteCategory = null }) {
          Text("Cancelar")
        }
      },
    )
  }
}

fun parseHexColor(hex: String): Color = try {
  Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
  Color(0xFFFFA510)
}
