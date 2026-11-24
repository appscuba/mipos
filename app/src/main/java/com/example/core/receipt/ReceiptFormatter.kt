package com.example.core.receipt

import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.local.db.entity.SaleEntity
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptFormatter {
  fun buildTextReceipt(
    business: BusinessEntity?,
    sale: SaleEntity,
    details: List<SaleDetailEntity>,
    payments: List<PaymentEntity>,
    customerName: String?,
  ): String {
    val baseCurrency = business?.primaryCurrency ?: sale.currencyCode
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sale.saleDate))
    val sb = StringBuilder()

    sb.appendLine("========================================")
    sb.appendLine((business?.name ?: "Gestor Comercial").uppercase())
    business?.phone?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Tel: $it") }
    business?.address?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
    sb.appendLine("========================================")
    sb.appendLine("Comprobante: ${sale.receiptNumber}")
    sb.appendLine("Fecha: $dateStr")
    sb.appendLine("Cliente: ${customerName ?: "Cliente al contado"}")
    if (sale.status == "ANNULLED") {
      sb.appendLine("ESTADO: *** ANULADA ***")
    } else if (sale.status == "PARTIALLY_RETURNED") {
      sb.appendLine("ESTADO: DEVOLUCION PARCIAL")
    }
    sb.appendLine("----------------------------------------")

    details.forEach { d ->
      val qtyStr = "${MoneyMath.formatQuantity(d.enteredQuantity)} ${d.unitLabel}"
      sb.appendLine("${d.productNameSnapshot}")
      sb.appendLine("  $qtyStr x ${MoneyMath.format(d.unitSalePriceBase, baseCurrency)} = ${MoneyMath.format(d.lineTotalBase, baseCurrency)}")
      if (d.returnedBaseQuantity > BigDecimal.ZERO) {
        sb.appendLine("  (Devuelto base: ${MoneyMath.formatQuantity(d.returnedBaseQuantity)})")
      }
    }

    sb.appendLine("----------------------------------------")
    sb.appendLine("Subtotal: ${MoneyMath.format(sale.subtotalBase, baseCurrency)}")
    if (sale.discountBase > BigDecimal.ZERO) {
      sb.appendLine("Descuento: -${MoneyMath.format(sale.discountBase, baseCurrency)}")
    }
    sb.appendLine("TOTAL: ${MoneyMath.format(sale.totalBase, baseCurrency)}")

    if (payments.isNotEmpty()) {
      sb.appendLine("Pagos:")
      payments.forEach { p ->
        val label = when (p.paymentMethod) {
          "CASH" -> "Efectivo"
          "TRANSFER" -> "Transferencia"
          "USD" -> "USD"
          else -> "Otro"
        }
        val orig = MoneyMath.format(p.originalAmount, p.currencyCode)
        val base = if (p.currencyCode != baseCurrency) " (${MoneyMath.format(p.convertedAmountBase, baseCurrency)})" else ""
        sb.appendLine("  - $label: $orig$base")
      }
    }
    sb.appendLine("Pagado: ${MoneyMath.format(sale.paidBase, baseCurrency)}")
    if (sale.pendingDebtBase > BigDecimal.ZERO) {
      sb.appendLine("Saldo pendiente: ${MoneyMath.format(sale.pendingDebtBase, baseCurrency)}")
    }
    sb.appendLine("========================================")
    sb.appendLine("Gracias por su compra")

    return sb.toString().trimEnd()
  }
}
