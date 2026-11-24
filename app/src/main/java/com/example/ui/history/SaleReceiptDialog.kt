package com.example.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.receipt.ReceiptFormatter
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.repository.SaleWithDetails
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SaleReceiptDialog(
  business: BusinessEntity?,
  saleWithDetails: SaleWithDetails,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  @Suppress("DEPRECATION")
  val clipboardManager = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }

  val receiptText = remember(business, saleWithDetails) {
    ReceiptFormatter.buildTextReceipt(
      business = business,
      sale = saleWithDetails.sale,
      details = saleWithDetails.details,
      payments = saleWithDetails.payments,
      customerName = saleWithDetails.customerName,
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Comprobante ${saleWithDetails.sale.receiptNumber}",
        modifier = Modifier.testTag("receipt_title"),
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          ),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = receiptText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp)
              .testTag("receipt_body_text"),
          )
        }
        if (copied) {
          Text(
            text = "Comprobante copiado al portapapeles.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(receiptText))
              copied = true
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_copy_button"),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copiar")
          }
          Button(
            onClick = {
              val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Comprobante ${saleWithDetails.sale.receiptNumber}")
                putExtra(Intent.EXTRA_TEXT, receiptText)
              }
              context.startActivity(Intent.createChooser(shareIntent, "Compartir comprobante"))
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_share_button"),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Compartir")
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("receipt_close_button"),
      ) {
        Text("Listo")
      }
    },
  )
}
