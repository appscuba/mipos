package com.example.core.money

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exact decimal monetary engine using BigDecimal exclusively.
 * Never uses Float or Double for currency amounts or exchange rates.
 */
object MoneyMath {
  val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
  val ONE: BigDecimal = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP)
  val MATH_CONTEXT: MathContext = MathContext(16, RoundingMode.HALF_UP)
  const val MONEY_SCALE = 2
  const val RATE_SCALE = 4
  const val QUANTITY_SCALE = 4

  fun money(value: String): BigDecimal {
    val normalized = value.trim().replace(",", ".")
    if (normalized.isEmpty()) return ZERO
    return BigDecimal(normalized).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
  }

  fun parseMoneyOrNull(value: String): BigDecimal? {
    val normalized = value.trim().replace(",", ".")
    if (normalized.isEmpty()) return null
    return normalized.toBigDecimalOrNull()?.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
  }

  fun decimal(value: String, scale: Int = QUANTITY_SCALE): BigDecimal {
    val normalized = value.trim().replace(",", ".")
    if (normalized.isEmpty()) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
    return BigDecimal(normalized).setScale(scale, RoundingMode.HALF_UP)
  }

  fun parseDecimalOrNull(value: String): BigDecimal? {
    val normalized = value.trim().replace(",", ".")
    if (normalized.isEmpty()) return null
    return normalized.toBigDecimalOrNull()
  }

  fun rate(value: String): BigDecimal {
    val normalized = value.trim().replace(",", ".")
    if (normalized.isEmpty()) return BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP)
    return BigDecimal(normalized).setScale(RATE_SCALE, RoundingMode.HALF_UP)
  }

  fun add(a: BigDecimal, b: BigDecimal): BigDecimal =
    a.add(b).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

  fun subtract(a: BigDecimal, b: BigDecimal): BigDecimal =
    a.subtract(b).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

  fun multiply(amount: BigDecimal, factor: BigDecimal): BigDecimal =
    amount.multiply(factor, MATH_CONTEXT).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

  fun divide(total: BigDecimal, divisor: BigDecimal, scale: Int = MONEY_SCALE): BigDecimal {
    require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Divisor cannot be zero" }
    return total.divide(divisor, scale, RoundingMode.HALF_UP)
  }

  /**
   * Explicitly converts an amount between currencies using exact BigDecimal rates
   * relative to the business base currency (where base currency rate = 1.0000).
   */
  fun convertToBase(amount: BigDecimal, sourceRateToBase: BigDecimal): BigDecimal {
    require(sourceRateToBase > BigDecimal.ZERO) { "Exchange rate must be positive" }
    return amount.multiply(sourceRateToBase, MATH_CONTEXT).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
  }

  fun convertBetween(
    amount: BigDecimal,
    sourceRateToBase: BigDecimal,
    targetRateToBase: BigDecimal,
  ): BigDecimal {
    require(sourceRateToBase > BigDecimal.ZERO && targetRateToBase > BigDecimal.ZERO) {
      "Exchange rates must be positive"
    }
    if (sourceRateToBase.compareTo(targetRateToBase) == 0) {
      return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }
    val inBase = amount.multiply(sourceRateToBase, MATH_CONTEXT)
    return inBase.divide(targetRateToBase, MONEY_SCALE, RoundingMode.HALF_UP)
  }

  fun format(amount: BigDecimal, currencyCode: String): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply {
      groupingSeparator = ','
      decimalSeparator = '.'
    }
    val formatter = DecimalFormat("#,##0.00", symbols)
    return "${formatter.format(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP))} $currencyCode"
  }

  fun formatQuantity(quantity: BigDecimal): String {
    val stripped = quantity.stripTrailingZeros()
    val display = if (stripped.scale() < 0) stripped.setScale(0) else stripped
    return display.toPlainString()
  }
}
