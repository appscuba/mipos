package com.example.ui.license

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.license.LicenseSnapshot

private data class StockPlusPlan(
  val name: String,
  val monthlyPrice: Int,
  val description: String,
  val features: List<String>,
  val recommended: Boolean = false,
)

private val stockPlusPlans = listOf(
  StockPlusPlan(
    name = "Básico",
    monthlyPrice = 120,
    description = "Para pequeños negocios que necesitan organizar sus operaciones.",
    features = listOf("Inventario y productos", "Ventas y punto de venta", "Control básico de caja", "Hasta 30 productos y 100 ventas/mes"),
  ),
  StockPlusPlan(
    name = "Premium",
    monthlyPrice = 220,
    description = "Para negocios en crecimiento que necesitan más control.",
    features = listOf("Todo lo del plan Básico", "Compras y proveedores", "Gastos, salarios y transporte", "Reportes y ganancias", "Clientes y cuentas por cobrar"),
    recommended = true,
  ),
  StockPlusPlan(
    name = "Max",
    monthlyPrice = 300,
    description = "Para negocios que quieren aprovechar todas las herramientas.",
    features = listOf("Todo lo del plan Premium", "Funciones avanzadas de gestión", "Respaldos y herramientas adicionales", "Prioridad en soporte"),
  ),
)

@Composable
fun PlansScreen(
  snapshot: LicenseSnapshot,
  onActivate: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val planStatus = snapshot.plan()
  val statusText = when (planStatus) {
    com.example.data.license.PlanStatus.LICENSED -> "Licencia activa"
    com.example.data.license.PlanStatus.TRIAL -> "Prueba gratuita: ${snapshot.trialDaysRemaining()} días restantes"
    com.example.data.license.PlanStatus.FREE -> "Plan gratuito"
  }

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Planes de Stock+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text("Elige el plan que acompaña el crecimiento de tu negocio.", style = MaterialTheme.typography.bodyMedium)
          Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
            Text(statusText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
          }
          Text("Todos los planes se vinculan al dispositivo autorizado. Consulta disponibilidad y activa tu licencia con PedritoTech.", style = MaterialTheme.typography.bodySmall)
        }
      }

      Text("COMPARA LOS PLANES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
      stockPlusPlans.forEach { plan ->
        val annualTotal = (plan.monthlyPrice * 12 * 0.8).toInt()
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (plan.recommended) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              if (plan.recommended) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                  Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text("Recomendado", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
            Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("${plan.monthlyPrice} CUP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
              Text("CUP / mes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text("Anual: $annualTotal CUP (20% de descuento)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            plan.features.forEach { feature ->
              Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(feature, style = MaterialTheme.typography.bodyMedium)
              }
            }
            Button(
              onClick = {
                val message = Uri.encode("Hola, deseo información y activar el plan ${plan.name} de Stock+ (${plan.monthlyPrice} CUP mensual o $annualTotal CUP anual).")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5353274074?text=$message")))
              },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
            ) { Text("Solicitar plan ${plan.name}") }
          }
        }
      }

      Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("¿Ya tienes una licencia?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("Valida tu licencia desde aquí. La activación se realiza con el teléfono registrado y el ID de este dispositivo.", style = MaterialTheme.typography.bodyMedium)
          OutlinedButton(onClick = onActivate, modifier = Modifier.fillMaxWidth()) { Text("Activar o validar licencia") }
        }
      }
      Spacer(Modifier.height(8.dp))
      Text("Precios de referencia sujetos a confirmación con PedritoTech. La app no procesa pagos dentro de esta pantalla.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
  }
}
