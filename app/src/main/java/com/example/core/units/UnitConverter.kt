package com.example.core.units

import com.example.core.money.MoneyMath
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure domain conversion engine for weight (internal base: grams) and packaged units.
 * Default equivalence is 1 pound = 500 grams, configurable per business.
 */
object UnitConverter {
  val DEFAULT_GRAMS_PER_POUND: BigDecimal = BigDecimal("500.0000")
  val GRAMS_PER_KILOGRAM: BigDecimal = BigDecimal("1000.0000")

  val QUARTER_POUND: BigDecimal = BigDecimal("0.25")
  val HALF_POUND: BigDecimal = BigDecimal("0.50")
  val THREE_QUARTER_POUND: BigDecimal = BigDecimal("0.75")
  val ONE_POUND: BigDecimal = BigDecimal("1.00")
  val ONE_AND_HALF_POUNDS: BigDecimal = BigDecimal("1.50")
  val TWO_POUNDS: BigDecimal = BigDecimal("2.00")

  val WEIGHT_PRESET_FRACTIONS: List<Pair<String, BigDecimal>> = listOf(
    "¼ lb" to QUARTER_POUND,
    "½ lb" to HALF_POUND,
    "¾ lb" to THREE_QUARTER_POUND,
    "1 lb" to ONE_POUND,
    "1½ lb" to ONE_AND_HALF_POUNDS,
    "2 lb" to TWO_POUNDS,
  )

  fun poundsToGrams(
    pounds: BigDecimal,
    gramsPerPound: BigDecimal = DEFAULT_GRAMS_PER_POUND,
  ): BigDecimal {
    require(pounds >= BigDecimal.ZERO) { "Weight cannot be negative" }
    require(gramsPerPound > BigDecimal.ZERO) { "Equivalence must be positive" }
    return pounds.multiply(gramsPerPound, MoneyMath.MATH_CONTEXT)
      .setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  fun gramsToPounds(
    grams: BigDecimal,
    gramsPerPound: BigDecimal = DEFAULT_GRAMS_PER_POUND,
  ): BigDecimal {
    require(grams >= BigDecimal.ZERO) { "Grams cannot be negative" }
    require(gramsPerPound > BigDecimal.ZERO) { "Equivalence must be positive" }
    return grams.divide(gramsPerPound, MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  fun kilogramsToGrams(kilograms: BigDecimal): BigDecimal {
    require(kilograms >= BigDecimal.ZERO) { "Kilograms cannot be negative" }
    return kilograms.multiply(GRAMS_PER_KILOGRAM, MoneyMath.MATH_CONTEXT)
      .setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  fun gramsToKilograms(grams: BigDecimal): BigDecimal {
    require(grams >= BigDecimal.ZERO) { "Grams cannot be negative" }
    return grams.divide(GRAMS_PER_KILOGRAM, MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  fun packagesToBaseUnits(
    packageCount: BigDecimal,
    unitsPerPackage: BigDecimal,
  ): BigDecimal {
    require(packageCount >= BigDecimal.ZERO) { "Package count cannot be negative" }
    require(unitsPerPackage > BigDecimal.ZERO) { "Units per package must be positive" }
    return packageCount.multiply(unitsPerPackage, MoneyMath.MATH_CONTEXT)
      .setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  fun baseUnitsToPackages(
    baseUnits: BigDecimal,
    unitsPerPackage: BigDecimal,
  ): BigDecimal {
    require(baseUnits >= BigDecimal.ZERO) { "Units cannot be negative" }
    require(unitsPerPackage > BigDecimal.ZERO) { "Units per package must be positive" }
    return baseUnits.divide(unitsPerPackage, MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  /**
   * Computes exact base units (grams or individual units) and records the exact applied equivalence.
   */
  fun toBaseUnits(
    enteredQuantity: BigDecimal,
    conversionFactorToBase: BigDecimal,
  ): BigDecimal {
    require(enteredQuantity >= BigDecimal.ZERO) { "Quantity cannot be negative" }
    require(conversionFactorToBase > BigDecimal.ZERO) { "Conversion factor must be positive" }
    return enteredQuantity.multiply(conversionFactorToBase, MoneyMath.MATH_CONTEXT)
      .setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
  }

  /**
   * Formats weight stock cleanly in both internal grams and configured pounds.
   */
  fun formatWeightStock(
    grams: BigDecimal,
    gramsPerPound: BigDecimal = DEFAULT_GRAMS_PER_POUND,
  ): String {
    val safeGrams = if (grams < BigDecimal.ZERO) BigDecimal.ZERO else grams
    val lb = gramsToPounds(safeGrams, gramsPerPound)
    val prefix = if (grams < BigDecimal.ZERO) "-" else ""
    val absGrams = grams.abs()
    return "$prefix${MoneyMath.formatQuantity(absGrams)} g (${prefix}${MoneyMath.formatQuantity(lb)} lb)"
  }
}
