package com.example.core.cost

import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.repository.ProductModality
import java.math.BigDecimal
import java.math.RoundingMode

data class PurchaseCostLineInput(
  val productId: Long,
  val baseQuantityAdded: BigDecimal,
  val lineSubtotalOriginal: BigDecimal,
)

data class PurchaseCostLineResult(
  val productId: Long,
  val lineSubtotalBase: BigDecimal,
  val allocatedAdjustmentBase: BigDecimal,
  val lineTotalRealCostBase: BigDecimal,
  val realCostPerBaseUnit: BigDecimal,
)

/**
 * Pure accounting & cost engine independent of UI screens.
 * Implements moving weighted average cost and proportional acquisition cost allocation.
 */
object CostEngine {
  const val COST_SCALE = 4

  fun toSaleUnits(
    baseQuantity: BigDecimal,
    modality: ProductModality,
    defaultSaleUnit: String,
    gramsPerPound: BigDecimal,
  ): BigDecimal = when (modality) {
    ProductModality.WEIGHT -> when (defaultSaleUnit) {
      "POUND" -> UnitConverter.gramsToPounds(baseQuantity, gramsPerPound)
      "KG" -> UnitConverter.gramsToKilograms(baseQuantity)
      else -> baseQuantity
    }
    ProductModality.UNIT, ProductModality.PRESENTATION -> baseQuantity
  }

  /**
   * Distributes net acquisition adjustments (transport + extraExpenses - discount)
   * proportionally across purchase lines according to each line's subtotal,
   * assigning rounding remainder to the final line so totals match to the exact cent.
   */
  fun allocatePurchaseCosts(
    lines: List<PurchaseCostLineInput>,
    exchangeRateToBase: BigDecimal,
    discountOriginal: BigDecimal,
    transportOriginal: BigDecimal,
    extraExpensesOriginal: BigDecimal,
  ): List<PurchaseCostLineResult> {
    require(lines.isNotEmpty()) { "La compra debe tener al menos un producto." }
    require(exchangeRateToBase > BigDecimal.ZERO) { "La tasa de cambio debe ser positiva." }

    val lineSubtotalsBase = lines.map { MoneyMath.convertToBase(it.lineSubtotalOriginal, exchangeRateToBase) }
    val grossSubtotalBase = lineSubtotalsBase.fold(MoneyMath.ZERO) { acc, v -> MoneyMath.add(acc, v) }

    val netExtraOriginal = transportOriginal.add(extraExpensesOriginal).subtract(discountOriginal)
    val totalAdjustmentBase = MoneyMath.convertToBase(netExtraOriginal, exchangeRateToBase)

    var remainingAdjustment = totalAdjustmentBase
    return lines.mapIndexed { index, line ->
      val lineSubBase = lineSubtotalsBase[index]
      val allocated = if (index == lines.lastIndex) {
        remainingAdjustment
      } else if (grossSubtotalBase > BigDecimal.ZERO) {
        val share = totalAdjustmentBase
          .multiply(lineSubBase, MoneyMath.MATH_CONTEXT)
          .divide(grossSubtotalBase, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
        remainingAdjustment = remainingAdjustment.subtract(share)
        share
      } else {
        BigDecimal.ZERO.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
      }

      val lineTotalRealCostBase = lineSubBase.add(allocated)
        .max(BigDecimal.ZERO)
        .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)

      val realCostPerBaseUnit = if (line.baseQuantityAdded > BigDecimal.ZERO) {
        lineTotalRealCostBase.divide(line.baseQuantityAdded, COST_SCALE, RoundingMode.HALF_UP)
      } else {
        BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP)
      }

      PurchaseCostLineResult(
        productId = line.productId,
        lineSubtotalBase = lineSubBase,
        allocatedAdjustmentBase = allocated,
        lineTotalRealCostBase = lineTotalRealCostBase,
        realCostPerBaseUnit = realCostPerBaseUnit,
      )
    }
  }

  /**
   * Moving weighted average cost per sale unit.
   * Example: 10 lb @ 400 + 10 lb @ 500 -> (4000 + 5000) / 20 = 450.0000 CUP/lb.
   */
  fun computeWeightedAverageCost(
    existingStockBase: BigDecimal,
    currentAvgCostPerSaleUnit: BigDecimal,
    addedBaseQty: BigDecimal,
    addedTotalCostBase: BigDecimal,
    modality: ProductModality,
    defaultSaleUnit: String,
    gramsPerPound: BigDecimal,
  ): BigDecimal {
    val addedSaleUnits = toSaleUnits(addedBaseQty, modality, defaultSaleUnit, gramsPerPound)
    if (addedSaleUnits <= BigDecimal.ZERO) {
      return currentAvgCostPerSaleUnit.setScale(COST_SCALE, RoundingMode.HALF_UP)
    }

    if (existingStockBase <= BigDecimal.ZERO) {
      return addedTotalCostBase.divide(addedSaleUnits, COST_SCALE, RoundingMode.HALF_UP)
    }

    val existingSaleUnits = toSaleUnits(existingStockBase, modality, defaultSaleUnit, gramsPerPound)
    val existingTotalValue = existingSaleUnits.multiply(currentAvgCostPerSaleUnit, MoneyMath.MATH_CONTEXT)
    val combinedValue = existingTotalValue.add(addedTotalCostBase)
    val combinedSaleUnits = existingSaleUnits.add(addedSaleUnits)

    if (combinedSaleUnits <= BigDecimal.ZERO) {
      return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP)
    }

    return combinedValue.divide(combinedSaleUnits, COST_SCALE, RoundingMode.HALF_UP)
  }

  fun computeGrossProfit(netSalesBase: BigDecimal, costOfGoodsSoldBase: BigDecimal): BigDecimal =
    MoneyMath.subtract(netSalesBase, costOfGoodsSoldBase)

  fun computeNetProfit(grossProfitBase: BigDecimal, operatingExpensesBase: BigDecimal): BigDecimal =
    MoneyMath.subtract(grossProfitBase, operatingExpensesBase)
}
