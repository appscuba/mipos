package com.example.data.repository

import com.example.core.cost.CostEngine
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AppSettingsEntity
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.ExpenseEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.ProductEntity
import com.example.data.local.db.entity.PurchaseEntity
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.local.db.entity.SaleEntity
import com.example.data.local.db.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

enum class ReportPeriodPreset(val label: String) {
  TODAY("Hoy"),
  YESTERDAY("Ayer"),
  THIS_WEEK("Semana"),
  THIS_MONTH("Mes"),
  ALL("Todo"),
}

data class ExpenseCategoryTotal(
  val categoryName: String,
  val totalBase: BigDecimal,
  val expenseCount: Int,
)

data class ProductProfitabilityRow(
  val productId: Long,
  val productName: String,
  val unitsSummary: String,
  val revenueBase: BigDecimal,
  val costBase: BigDecimal,
  val grossProfitBase: BigDecimal,
  val marginPercent: BigDecimal,
)

data class BusinessReportSnapshot(
  val primaryCurrency: String,
  val salesCount: Int,
  val purchasesCount: Int,
  val expensesCount: Int,
  val netSalesBase: BigDecimal,
  val cogsBase: BigDecimal,
  val grossProfitBase: BigDecimal,
  val grossMarginPercent: BigDecimal,
  val operatingExpensesBase: BigDecimal,
  val expenseBreakdown: List<ExpenseCategoryTotal>,
  val netProfitBase: BigDecimal,
  val netMarginPercent: BigDecimal,
  val totalPurchasesBase: BigDecimal,
  val extraIncomeBase: BigDecimal,
  val investedCapitalBase: BigDecimal,
  val ownerWithdrawalsBase: BigDecimal,
  val inventoryValueBase: BigDecimal,
  val currentCashDrawerBase: BigDecimal,
  val customerReceivableBase: BigDecimal,
  val supplierPayableBase: BigDecimal,
  val productProfitability: List<ProductProfitabilityRow>,
  val paymentMethodTotals: Map<String, BigDecimal>,
)

class ReportsRepository(private val db: AppDatabase) {
  private val reportsDao = db.reportsDao()
  private val businessConfigDao = db.businessConfigDao()
  private val inventoryDao = db.inventoryDao()
  private val financeDao = db.financeDao()

  fun observeReport(
    businessId: Long,
    startMillis: Long,
    endMillis: Long,
  ): Flow<BusinessReportSnapshot> = combine(
    businessConfigDao.observeActiveBusiness(),
    businessConfigDao.observeSettings(businessId),
    reportsDao.observeValidSales(businessId),
    reportsDao.observeValidSaleDetails(businessId),
    financeDao.observeExpenses(businessId),
    reportsDao.observeConfirmedPurchases(businessId),
    financeDao.observeAllCashMovements(businessId),
    reportsDao.observePayments(businessId),
    inventoryDao.observeActiveProducts(businessId),
    financeDao.observeOpenCashSession(businessId),
    financeDao.observeActiveCustomers(businessId),
    financeDao.observeActiveSuppliersForFinance(businessId),
    financeDao.observeExpenseCategoriesForReport(businessId),
  ) { values ->
    val business = values[0] as BusinessEntity?
    val settings = values[1] as AppSettingsEntity?
    @Suppress("UNCHECKED_CAST")
    val validSales = values[2] as List<SaleEntity>
    @Suppress("UNCHECKED_CAST")
    val validDetails = values[3] as List<SaleDetailEntity>
    @Suppress("UNCHECKED_CAST")
    val expenses = values[4] as List<ExpenseEntity>
    @Suppress("UNCHECKED_CAST")
    val purchases = values[5] as List<PurchaseEntity>
    @Suppress("UNCHECKED_CAST")
    val cashMovements = values[6] as List<CashMovementEntity>
    @Suppress("UNCHECKED_CAST")
    val payments = values[7] as List<PaymentEntity>
    @Suppress("UNCHECKED_CAST")
    val products = values[8] as List<ProductEntity>
    val openSession = values[9] as CashSessionEntity?
    @Suppress("UNCHECKED_CAST")
    val customers = values[10] as List<CustomerEntity>
    @Suppress("UNCHECKED_CAST")
    val suppliers = values[11] as List<SupplierEntity>
    @Suppress("UNCHECKED_CAST")
    val expenseCategories = values[12] as List<ExpenseCategoryEntity>

    val periodSales = validSales.filter { it.saleDate in startMillis..endMillis }
    val periodSaleIds = periodSales.map { it.id }.toSet()
    val periodDetails = validDetails.filter { it.saleId in periodSaleIds }

    val periodExpenses = expenses.filter {
      it.status == "CONFIRMED" && it.expenseDate in startMillis..endMillis
    }
    val periodPurchases = purchases.filter { it.purchaseDate in startMillis..endMillis }
    val periodMovements = cashMovements.filter { it.timestamp in startMillis..endMillis }
    val periodPayments = payments.filter {
      it.paymentDate in startMillis..endMillis &&
        (it.referenceType == "SALE" || it.referenceType == "DEBT_CUSTOMER")
    }

    val netSales = periodSales.fold(MoneyMath.ZERO) { acc, s -> MoneyMath.add(acc, s.totalBase) }
    val cogs = periodSales.fold(MoneyMath.ZERO) { acc, s -> MoneyMath.add(acc, s.totalCostOfGoodsBase) }
    val grossProfit = CostEngine.computeGrossProfit(netSales, cogs)
    val operatingExpenses = periodExpenses.fold(MoneyMath.ZERO) { acc, e -> MoneyMath.add(acc, e.convertedAmountBase) }
    val netProfit = CostEngine.computeNetProfit(grossProfit, operatingExpenses)
    val categoryNames = expenseCategories.associate { it.id to it.name }
    val expenseBreakdown = periodExpenses
      .groupBy { it.expenseCategoryId }
      .map { (categoryId, categoryExpenses) ->
        ExpenseCategoryTotal(
          categoryName = categoryNames[categoryId] ?: "Categoría archivada",
          totalBase = categoryExpenses.fold(MoneyMath.ZERO) { acc, expense -> MoneyMath.add(acc, expense.convertedAmountBase) },
          expenseCount = categoryExpenses.size,
        )
      }
      .sortedByDescending { it.totalBase }

    val grossMargin = if (netSales > BigDecimal.ZERO) {
      grossProfit.multiply(BigDecimal("100")).divide(netSales, 1, RoundingMode.HALF_UP)
    } else {
      MoneyMath.ZERO
    }
    val netMargin = if (netSales > BigDecimal.ZERO) {
      netProfit.multiply(BigDecimal("100")).divide(netSales, 1, RoundingMode.HALF_UP)
    } else {
      MoneyMath.ZERO
    }

    val totalPurchases = periodPurchases.fold(MoneyMath.ZERO) { acc, p -> MoneyMath.add(acc, p.totalConvertedBase) }
    val extraIncome = periodMovements
      .filter { it.movementType == "EXTRA_INCOME" }
      .fold(MoneyMath.ZERO) { acc, m -> MoneyMath.add(acc, m.signedAmountBase) }
    val investedCapital = periodMovements
      .filter { it.movementType == "INVESTMENT" }
      .fold(MoneyMath.ZERO) { acc, m -> MoneyMath.add(acc, m.signedAmountBase) }
    val ownerWithdrawals = periodMovements
      .filter { it.movementType == "OWNER_WITHDRAWAL" }
      .fold(MoneyMath.ZERO) { acc, m -> MoneyMath.add(acc, m.signedAmountBase.abs()) }

    val gramsPerPound = settings?.gramsPerPound ?: UnitConverter.DEFAULT_GRAMS_PER_POUND
    val inventoryValue = products.fold(MoneyMath.ZERO) { acc, p ->
      MoneyMath.add(acc, InventoryRepository.computeProductInventoryValue(p, gramsPerPound))
    }

    val receivable = customers.fold(MoneyMath.ZERO) { acc, c -> MoneyMath.add(acc, c.debtBalanceBase) }
    val payable = suppliers.fold(MoneyMath.ZERO) { acc, s -> MoneyMath.add(acc, s.balanceOwedBase) }

    // Aggregate per-product profitability using active proportion (excluding returned qty)
    val grouped = periodDetails.groupBy { it.productId }
    val productRows = grouped.map { (productId, lines) ->
      var rev = MoneyMath.ZERO
      var cost = MoneyMath.ZERO
      var netBaseQty = BigDecimal.ZERO
      val unitSuffix = if (lines.first().unitLabel.equals("g", ignoreCase = true) ||
        lines.first().unitLabel.equals("lb", ignoreCase = true) ||
        lines.first().unitLabel.equals("kg", ignoreCase = true)
      ) {
        "g"
      } else {
        "u"
      }

      for (line in lines) {
        val ratioRemaining = if (line.baseQuantityDeducted > BigDecimal.ZERO) {
          val remainingBase = line.baseQuantityDeducted.subtract(line.returnedBaseQuantity).max(BigDecimal.ZERO)
          remainingBase.divide(line.baseQuantityDeducted, 6, RoundingMode.HALF_UP)
        } else {
          BigDecimal.ONE
        }
        val activeLineRev = line.lineTotalBase.multiply(ratioRemaining).setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
        val activeLineCost = line.lineCostBase.multiply(ratioRemaining).setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
        rev = MoneyMath.add(rev, activeLineRev)
        cost = MoneyMath.add(cost, activeLineCost)
        netBaseQty = netBaseQty.add(line.baseQuantityDeducted.subtract(line.returnedBaseQuantity).max(BigDecimal.ZERO))
      }

      val gp = CostEngine.computeGrossProfit(rev, cost)
      val margin = if (rev > BigDecimal.ZERO) {
        gp.multiply(BigDecimal("100")).divide(rev, 1, RoundingMode.HALF_UP)
      } else {
        MoneyMath.ZERO
      }

      ProductProfitabilityRow(
        productId = productId,
        productName = lines.first().productNameSnapshot,
        unitsSummary = "${MoneyMath.formatQuantity(netBaseQty)} $unitSuffix",
        revenueBase = rev,
        costBase = cost,
        grossProfitBase = gp,
        marginPercent = margin,
      )
    }.sortedByDescending { it.grossProfitBase }

    val paymentBreakdown = linkedMapOf<String, BigDecimal>()
    for (p in periodPayments) {
      val current = paymentBreakdown[p.paymentMethod] ?: MoneyMath.ZERO
      paymentBreakdown[p.paymentMethod] = MoneyMath.add(current, p.convertedAmountBase)
    }

    BusinessReportSnapshot(
      primaryCurrency = business?.primaryCurrency ?: "CUP",
      salesCount = periodSales.size,
      purchasesCount = periodPurchases.size,
      expensesCount = periodExpenses.size,
      netSalesBase = netSales,
      cogsBase = cogs,
      grossProfitBase = grossProfit,
      grossMarginPercent = grossMargin,
      operatingExpensesBase = operatingExpenses,
      expenseBreakdown = expenseBreakdown,
      netProfitBase = netProfit,
      netMarginPercent = netMargin,
      totalPurchasesBase = totalPurchases,
      extraIncomeBase = extraIncome,
      investedCapitalBase = investedCapital,
      ownerWithdrawalsBase = ownerWithdrawals,
      inventoryValueBase = inventoryValue,
      currentCashDrawerBase = openSession?.expectedCashBase ?: MoneyMath.ZERO,
      customerReceivableBase = receivable,
      supplierPayableBase = payable,
      productProfitability = productRows,
      paymentMethodTotals = paymentBreakdown,
    )
  }

  companion object {
    fun dateRangeForPreset(preset: ReportPeriodPreset, nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
      if (preset == ReportPeriodPreset.ALL) {
        return 0L to Long.MAX_VALUE
      }
      val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
      return when (preset) {
        ReportPeriodPreset.TODAY -> {
          val start = startOfDay(cal)
          val end = endOfDay(cal)
          start to end
        }
        ReportPeriodPreset.YESTERDAY -> {
          cal.add(Calendar.DAY_OF_YEAR, -1)
          val start = startOfDay(cal)
          val end = endOfDay(cal)
          start to end
        }
        ReportPeriodPreset.THIS_WEEK -> {
          val end = endOfDay(cal)
          cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
          val start = startOfDay(cal)
          start to end
        }
        ReportPeriodPreset.THIS_MONTH -> {
          val end = endOfDay(cal)
          cal.set(Calendar.DAY_OF_MONTH, 1)
          val start = startOfDay(cal)
          start to end
        }
        ReportPeriodPreset.ALL -> 0L to Long.MAX_VALUE
      }
    }

    private fun startOfDay(source: Calendar): Long {
      val c = source.clone() as Calendar
      c.set(Calendar.HOUR_OF_DAY, 0)
      c.set(Calendar.MINUTE, 0)
      c.set(Calendar.SECOND, 0)
      c.set(Calendar.MILLISECOND, 0)
      return c.timeInMillis
    }

    private fun endOfDay(source: Calendar): Long {
      val c = source.clone() as Calendar
      c.set(Calendar.HOUR_OF_DAY, 23)
      c.set(Calendar.MINUTE, 59)
      c.set(Calendar.SECOND, 59)
      c.set(Calendar.MILLISECOND, 999)
      return c.timeInMillis
    }

    fun formatExportSummary(
      snapshot: BusinessReportSnapshot,
      businessName: String,
      periodLabel: String,
    ): String = buildString {
      val cur = snapshot.primaryCurrency
      appendLine("REPORTE FINANCIERO — $businessName")
      appendLine("Período: $periodLabel")
      appendLine("----------------------------------------")
      appendLine("Ventas netas (${snapshot.salesCount}): ${MoneyMath.format(snapshot.netSalesBase, cur)}")
      appendLine("Costo de mercancía vendida: ${MoneyMath.format(snapshot.cogsBase, cur)}")
      appendLine("Ganancia bruta (${snapshot.grossMarginPercent}%): ${MoneyMath.format(snapshot.grossProfitBase, cur)}")
      appendLine("Gastos operativos (${snapshot.expensesCount}): ${MoneyMath.format(snapshot.operatingExpensesBase, cur)}")
      snapshot.expenseBreakdown.forEach { row ->
        appendLine("  - ${row.categoryName} (${row.expenseCount}): ${MoneyMath.format(row.totalBase, cur)}")
      }
      appendLine("GANANCIA NETA (${snapshot.netMarginPercent}%): ${MoneyMath.format(snapshot.netProfitBase, cur)}")
      appendLine("----------------------------------------")
      appendLine("Compras del período (${snapshot.purchasesCount}): ${MoneyMath.format(snapshot.totalPurchasesBase, cur)}")
      appendLine("Valor inventario actual: ${MoneyMath.format(snapshot.inventoryValueBase, cur)}")
      appendLine("Efectivo en caja abierta: ${MoneyMath.format(snapshot.currentCashDrawerBase, cur)}")
      appendLine("Cuentas por cobrar: ${MoneyMath.format(snapshot.customerReceivableBase, cur)}")
      appendLine("Cuentas por pagar: ${MoneyMath.format(snapshot.supplierPayableBase, cur)}")
      if (snapshot.productProfitability.isNotEmpty()) {
        appendLine("----------------------------------------")
        appendLine("Rentabilidad por producto:")
        snapshot.productProfitability.take(25).forEach { row ->
          appendLine("- ${row.productName} (${row.unitsSummary}): Ing ${MoneyMath.format(row.revenueBase, cur)} | Costo ${MoneyMath.format(row.costBase, cur)} | Ganancia ${MoneyMath.format(row.grossProfitBase, cur)} (${row.marginPercent}%)")
        }
      }
    }
  }
}
