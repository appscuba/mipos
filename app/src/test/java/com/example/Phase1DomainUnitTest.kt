package com.example

import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class Phase1DomainUnitTest {

  @Test
  fun `10 libras equivalen a 5000 gramos con equivalencia por defecto`() {
    val grams = UnitConverter.poundsToGrams(BigDecimal("10"))
    assertEquals(0, BigDecimal("5000.0000").compareTo(grams))
  }

  @Test
  fun `fracciones y decimales de libra convierten exactamente a gramos`() {
    assertEquals(0, BigDecimal("125").compareTo(UnitConverter.poundsToGrams(UnitConverter.QUARTER_POUND)))
    assertEquals(0, BigDecimal("250").compareTo(UnitConverter.poundsToGrams(UnitConverter.HALF_POUND)))
    assertEquals(0, BigDecimal("375").compareTo(UnitConverter.poundsToGrams(UnitConverter.THREE_QUARTER_POUND)))
    assertEquals(0, BigDecimal("175").compareTo(UnitConverter.poundsToGrams(BigDecimal("0.35"))))
    assertEquals(0, BigDecimal("750").compareTo(UnitConverter.poundsToGrams(UnitConverter.ONE_AND_HALF_POUNDS)))
    assertEquals(0, BigDecimal("1000").compareTo(UnitConverter.poundsToGrams(UnitConverter.TWO_POUNDS)))
  }

  @Test
  fun `equivalencia personalizada de gramos por libra funciona correctamente`() {
    val grams = UnitConverter.poundsToGrams(
      pounds = BigDecimal("2"),
      gramsPerPound = BigDecimal("454"),
    )
    assertEquals(0, BigDecimal("908").compareTo(grams))
  }

  @Test
  fun `10 cajas de 12 unidades generan 120 unidades base`() {
    val totalUnits = UnitConverter.packagesToBaseUnits(
      packageCount = BigDecimal("10"),
      unitsPerPackage = BigDecimal("12"),
    )
    assertEquals(0, BigDecimal("120").compareTo(totalUnits))
  }

  @Test
  fun `calculos monetarios BigDecimal conservan precision exacta sin punto flotante`() {
    val pricePerPound = MoneyMath.money("550.00")
    val qtyPounds = BigDecimal("0.50")
    val total = MoneyMath.multiply(pricePerPound, qtyPounds)
    assertEquals(0, BigDecimal("275.00").compareTo(total))

    val costPerPound = MoneyMath.money("400.00")
    val cost = MoneyMath.multiply(costPerPound, qtyPounds)
    val grossProfit = MoneyMath.subtract(total, cost)
    assertEquals(0, BigDecimal("75.00").compareTo(grossProfit))
  }

  @Test
  fun `conversion explicita de moneda USD a CUP usa BigDecimal`() {
    val usdAmount = MoneyMath.money("12.50")
    val rateUsdToCup = MoneyMath.rate("320.0000")
    val cupAmount = MoneyMath.convertToBase(usdAmount, rateUsdToCup)
    assertEquals(0, BigDecimal("4000.00").compareTo(cupAmount))
  }
}
