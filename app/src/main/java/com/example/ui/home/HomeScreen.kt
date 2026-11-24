package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.money.MoneyMath
import com.example.data.repository.BusinessWorkspaceSnapshot
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  workspace: BusinessWorkspaceSnapshot,
  onOpenSettings: () -> Unit,
  onOpenMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val business = workspace.business
  val settings = workspace.settings
  val baseCurrency = business?.primaryCurrency ?: "CUP"
  val gramsPerPound = settings?.gramsPerPound?.let { MoneyMath.formatQuantity(it) } ?: "500"

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 920.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Business Hero Status Card
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primaryContainer,
                  MaterialTheme.colorScheme.surface,
                ),
              ),
            )
            .padding(20.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
              ) {
                if (business?.logoUri != null) {
                  AsyncImage(
                    model = business.logoUri,
                    contentDescription = "Logotipo del negocio",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                  )
                }
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = business?.name ?: "Stock+",
                  style = MaterialTheme.typography.headlineSmall,
                  modifier = Modifier.testTag("home_business_name"),
                )
                Text(
                  text = business?.businessType ?: "Configuración activa",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              AssistChip(
                onClick = onOpenSettings,
                label = { Text("Moneda: $baseCurrency") },
                leadingIcon = {
                  Icon(Icons.Filled.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp))
                },
              )
              AssistChip(
                onClick = onOpenSettings,
                label = { Text("1 lb = $gramsPerPound g") },
                leadingIcon = {
                  Icon(Icons.Filled.Scale, contentDescription = null, modifier = Modifier.size(16.dp))
                },
              )
              AssistChip(
                onClick = {},
                label = { Text("Offline SQLite") },
                leadingIcon = {
                  Icon(Icons.Filled.OfflinePin, contentDescription = null, modifier = Modifier.size(16.dp))
                },
              )
            }
          }
        }
      }

      // Live DB Summary KPIs (Real database values, zero simulated numbers)
      Text(
        text = "Resumen del Sistema",
        style = MaterialTheme.typography.titleLarge,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        KpiStatCard(
          title = "Valor inventario",
          value = MoneyMath.format(workspace.inventoryValueBase, baseCurrency),
          subtitle = "${workspace.activeProductCount} productos • ${workspace.categoriesCount} cat.",
          icon = Icons.Filled.Inventory2,
          modifier = Modifier.weight(1f),
        )
        KpiStatCard(
          title = "Alertas stock",
          value = "${workspace.lowStockCount + workspace.outOfStockCount}",
          subtitle = "${workspace.outOfStockCount} agotados • ${workspace.lowStockCount} bajos",
          icon = Icons.Filled.PointOfSale,
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        KpiStatCard(
          title = "Compras / Ventas",
          value = "${workspace.confirmedPurchaseCount} / ${workspace.confirmedSaleCount}",
          subtitle = "Por pagar: ${MoneyMath.format(workspace.supplierPayableBase, baseCurrency)}",
          icon = Icons.Filled.AccountBalanceWallet,
          modifier = Modifier.weight(1f),
        )
        KpiStatCard(
          title = "Tasas configuradas",
          value = workspace.exchangeRates.size.toString(),
          subtitle = workspace.exchangeRates.joinToString(" • ") { it.currencyCode }.ifEmpty { baseCurrency },
          icon = Icons.Filled.Category,
          modifier = Modifier.weight(1f),
        )
      }

      if (workspace.exchangeRates.isNotEmpty()) {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Tasas de Cambio Activas", style = MaterialTheme.typography.titleMedium)
            workspace.exchangeRates.forEach { rate ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "${rate.currencyCode} — ${rate.currencyName}",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = if (rate.isBaseCurrency) {
                    "Base (1.0000)"
                  } else {
                    "1 ${rate.currencyCode} = ${MoneyMath.format(rate.rateToBase, baseCurrency)}"
                  },
                  style = MonoMetricMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }

      // Phase 3 Status Card
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Suite Comercial Completa Activa", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = "Todos los módulos operativos 100% offline: inventario por peso y unidades, compras con prorrateo y costo promedio ponderado, POS multi-moneda, caja diaria, clientes, finanzas, reportes, usuarios con PIN y respaldos cifrados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Button(
              onClick = onOpenSettings,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("home_open_settings_button"),
            ) {
              Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Ajustes", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
              onClick = onOpenMenu,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp),
            ) {
              Text("Ver Módulos")
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun KpiStatCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(30.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
      Text(
        text = value,
        style = MonoMetricLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
