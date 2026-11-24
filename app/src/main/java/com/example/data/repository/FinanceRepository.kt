package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.cost.CostEngine
import com.example.core.money.MoneyMath
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.local.db.entity.ExpenseEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode

enum class CashAdjustmentType(val code: String, val label: String, val isInflow: Boolean) {
  EXTRA_INCOME("EXTRA_INCOME", "Ingreso adicional", true),
  INVESTMENT("INVESTMENT", "Inversión / Aporte de capital", true),
  OWNER_WITHDRAWAL("OWNER_WITHDRAWAL", "Retiro del propietario", false),
}

data class ExpenseWithCategory(
  val expense: ExpenseEntity,
  val categoryName: String,
)

data class FinanceOverviewSnapshot(
  val primaryCurrency: String,
  val expenseCategories: List<ExpenseCategoryEntity>,
  val expenses: List<ExpenseWithCategory>,
  val openCashSession: CashSessionEntity?,
  val allCashMovements: List<CashMovementEntity>,
  val netSalesBase: BigDecimal,
  val costOfGoodsSoldBase: BigDecimal,
  val grossProfitBase: BigDecimal,
  val operatingExpensesBase: BigDecimal,
  val netProfitBase: BigDecimal,
  val totalInvestmentsBase: BigDecimal,
  val totalExtraIncomeBase: BigDecimal,
  val totalOwnerWithdrawalsBase: BigDecimal,
  val customerReceivableBase: BigDecimal,
  val supplierPayableBase: BigDecimal,
)

data class CustomerDetailSnapshot(
  val customer: CustomerEntity?,
  val sales: List<SaleEntity>,
  val debts: List<DebtEntity>,
)

class FinanceRepository(private val db: AppDatabase) {
  private val financeDao = db.financeDao()
  private val saleDao = db.saleDao()
  private val businessConfigDao = db.businessConfigDao()
  private val securityDao = db.securityDao()

  fun observeFinanceOverview(businessId: Long): Flow<FinanceOverviewSnapshot> = combine(
    businessConfigDao.observeActiveBusiness(),
    financeDao.observeActiveExpenseCategories(businessId),
    financeDao.observeExpenses(businessId),
    financeDao.observeOpenCashSession(businessId),
    financeDao.observeAllCashMovements(businessId),
    financeDao.observeActiveSalesForBusiness(businessId),
    financeDao.observeActiveCustomers(businessId),
    financeDao.observeActiveSuppliersForFinance(businessId),
  ) { values ->
    val business = values[0] as com.example.data.local.db.entity.BusinessEntity?
    @Suppress("UNCHECKED_CAST")
    val categories = values[1] as List<ExpenseCategoryEntity>
    @Suppress("UNCHECKED_CAST")
    val expenses = values[2] as List<ExpenseEntity>
    val openSession = values[3] as CashSessionEntity?
    @Suppress("UNCHECKED_CAST")
    val movements = values[4] as List<CashMovementEntity>
    @Suppress("UNCHECKED_CAST")
    val activeSales = values[5] as List<SaleEntity>
    @Suppress("UNCHECKED_CAST")
    val customers = values[6] as List<CustomerEntity>
    @Suppress("UNCHECKED_CAST")
    val suppliers = values[7] as List<com.example.data.local.db.entity.SupplierEntity>

    val catMap = categories.associateBy { it.id }
    val expenseItems = expenses.map { e ->
      ExpenseWithCategory(
        expense = e,
        categoryName = catMap[e.expenseCategoryId]?.name ?: "General",
      )
    }

    val netSales = activeSales.fold(MoneyMath.ZERO) { acc, s -> MoneyMath.add(acc, s.totalBase) }
    val cogs = activeSales.fold(MoneyMath.ZERO) { acc, s -> MoneyMath.add(acc, s.totalCostOfGoodsBase) }
    val grossProfit = CostEngine.computeGrossProfit(netSales, cogs)

    val operatingExpenses = expenses
      .filter { it.status == "CONFIRMED" }
      .fold(MoneyMath.ZERO) { acc, e -> MoneyMath.add(acc, e.convertedAmountBase) }

    val netProfit = CostEngine.computeNetProfit(grossProfit, operatingExpenses)

    val investments = movements
      .filter { it.movementType == "INVESTMENT" }
      .fold(MoneyMath.ZERO) { acc, m -> MoneyMath.add(acc, m.signedAmountBase) }
    val extraIncome = movements
      .filter { it.movementType == "EXTRA_INCOME" }
      .fold(MoneyMath.ZERO) { acc, m -> MoneyMath.add(acc, m.signedAmountBase) }
    val withdrawals = movements
      .filter { it.movementType == "OWNER_WITHDRAWAL" }
      .fold(MoneyMath.ZERO) { acc, m -> MoneyMath.add(acc, m.signedAmountBase.abs()) }

    val receivable = customers.fold(MoneyMath.ZERO) { acc, c -> MoneyMath.add(acc, c.debtBalanceBase) }
    val payable = suppliers.fold(MoneyMath.ZERO) { acc, s -> MoneyMath.add(acc, s.balanceOwedBase) }

    FinanceOverviewSnapshot(
      primaryCurrency = business?.primaryCurrency ?: "CUP",
      expenseCategories = categories,
      expenses = expenseItems,
      openCashSession = openSession,
      allCashMovements = movements,
      netSalesBase = netSales,
      costOfGoodsSoldBase = cogs,
      grossProfitBase = grossProfit,
      operatingExpensesBase = operatingExpenses,
      netProfitBase = netProfit,
      totalInvestmentsBase = investments,
      totalExtraIncomeBase = extraIncome,
      totalOwnerWithdrawalsBase = withdrawals,
      customerReceivableBase = receivable,
      supplierPayableBase = payable,
    )
  }

  fun observeExchangeRates(businessId: Long) =
    businessConfigDao.observeExchangeRates(businessId)

  fun observeCashSessions(businessId: Long): Flow<List<CashSessionEntity>> =
    financeDao.observeCashSessions(businessId)

  fun observeOpenCashSession(businessId: Long): Flow<CashSessionEntity?> =
    financeDao.observeOpenCashSession(businessId)

  fun observeCashMovementsForSession(sessionId: Long): Flow<List<CashMovementEntity>> =
    financeDao.observeCashMovementsForSession(sessionId)

  fun observeCustomers(businessId: Long): Flow<List<CustomerEntity>> =
    financeDao.observeActiveCustomers(businessId)

  fun observeCustomerDetail(businessId: Long, customerId: Long): Flow<CustomerDetailSnapshot> = combine(
    financeDao.observeCustomerById(customerId),
    financeDao.observeSalesForCustomer(customerId),
    financeDao.observeDebtsForCustomer(businessId, customerId),
  ) { customer, sales, debts ->
    CustomerDetailSnapshot(
      customer = customer,
      sales = sales,
      debts = debts,
    )
  }

  // --- Expense Categories ---

  suspend fun saveExpenseCategory(businessId: Long, name: String): Long {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "El nombre de la categoría es obligatorio." }

    return db.withTransaction {
      val existing = financeDao.getExpenseCategoryByName(businessId, clean)
      if (existing != null) {
        if (!existing.isActive) {
          financeDao.updateExpenseCategory(existing.copy(name = clean, isActive = true))
          return@withTransaction existing.id
        }
        error("Ya existe la categoría '$clean'.")
      }
      financeDao.insertExpenseCategory(
        ExpenseCategoryEntity(
          businessId = businessId,
          name = clean,
          isSystemDefault = false,
          isActive = true,
        ),
      )
    }
  }

  suspend fun archiveExpenseCategory(categoryId: Long) {
    db.withTransaction {
      val cat = financeDao.getExpenseCategoryById(categoryId) ?: return@withTransaction
      require(!cat.isSystemDefault) { "Las categorías base del sistema no se pueden eliminar." }
      financeDao.deactivateExpenseCategory(categoryId)
    }
  }

  // --- Operating Expenses ---

  suspend fun recordExpense(
    businessId: Long,
    expenseCategoryId: Long,
    description: String,
    currencyCode: String,
    originalAmount: BigDecimal,
    exchangeRateToBase: BigDecimal,
    paymentMethod: String,
    paidFromCashDrawer: Boolean,
    expenseDate: Long = System.currentTimeMillis(),
    userId: Long? = null,
  ): Long {
    val cleanDesc = description.trim()
    require(cleanDesc.isNotEmpty()) { "La descripción del gasto es obligatoria." }
    require(originalAmount > BigDecimal.ZERO) { "El monto del gasto debe ser mayor que cero." }
    require(exchangeRateToBase > BigDecimal.ZERO) { "La tasa de cambio debe ser positiva." }

    val convertedBase = MoneyMath.convertToBase(originalAmount, exchangeRateToBase)
    val now = System.currentTimeMillis()

    return db.withTransaction {
      val openSession = if (paidFromCashDrawer) {
        financeDao.getOpenCashSession(businessId)
          ?: error("Abre una caja diaria antes de pagar un gasto desde el efectivo en caja.")
      } else {
        financeDao.getOpenCashSession(businessId)
      }

      val expenseId = financeDao.insertExpense(
        ExpenseEntity(
          businessId = businessId,
          expenseCategoryId = expenseCategoryId,
          userId = userId,
          cashSessionId = openSession?.id,
          description = cleanDesc,
          currencyCode = currencyCode.trim().uppercase(),
          originalAmount = originalAmount.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          exchangeRateToBase = exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
          convertedAmountBase = convertedBase,
          paymentMethod = paymentMethod,
          paidFromCashDrawer = paidFromCashDrawer,
          expenseDate = expenseDate,
          status = "CONFIRMED",
          createdAt = now,
        ),
      )

      if (paidFromCashDrawer && openSession != null) {
        financeDao.insertCashMovement(
          CashMovementEntity(
            businessId = businessId,
            cashSessionId = openSession.id,
            userId = userId,
            movementType = "EXPENSE_CASH",
            affectsPhysicalCash = true,
            currencyCode = currencyCode.trim().uppercase(),
            originalAmount = originalAmount.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            exchangeRateToBase = exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
            signedAmountBase = convertedBase.negate(),
            description = "Gasto: $cleanDesc",
            referenceType = "EXPENSE",
            referenceId = expenseId,
            timestamp = now,
          ),
        )
        val newExpenses = MoneyMath.add(openSession.totalCashExpensesBase, convertedBase)
        financeDao.updateCashSession(
          openSession.copy(
            totalCashExpensesBase = newExpenses,
            expectedCashBase = computeExpectedCash(openSession.copy(totalCashExpensesBase = newExpenses)),
          ),
        )
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "EXPENSE_RECORDED",
          entityType = "EXPENSE",
          entityId = expenseId,
          details = "Gasto '$cleanDesc': $convertedBase (${if (paidFromCashDrawer) "Caja" else paymentMethod})",
          timestamp = now,
        ),
      )

      expenseId
    }
  }

  suspend fun annulExpense(
    businessId: Long,
    expenseId: Long,
    reason: String = "Anulación de gasto",
    userId: Long? = null,
  ) {
    val now = System.currentTimeMillis()
    db.withTransaction {
      val expense = financeDao.getExpenseById(expenseId) ?: error("Gasto no encontrado.")
      require(expense.status != "ANNULLED") { "Este gasto ya fue anulado." }

      financeDao.updateExpense(expense.copy(status = "ANNULLED"))

      if (expense.paidFromCashDrawer && expense.cashSessionId != null) {
        val session = financeDao.getCashSessionById(expense.cashSessionId)
        if (session != null && session.status == "OPEN") {
          val reducedExpenses = MoneyMath.subtract(session.totalCashExpensesBase, expense.convertedAmountBase)
            .max(MoneyMath.ZERO)
          val updatedSession = session.copy(totalCashExpensesBase = reducedExpenses)
          financeDao.updateCashSession(
            updatedSession.copy(expectedCashBase = computeExpectedCash(updatedSession)),
          )
          financeDao.insertCashMovement(
            CashMovementEntity(
              businessId = businessId,
              cashSessionId = session.id,
              userId = userId,
              movementType = "EXTRA_INCOME",
              affectsPhysicalCash = true,
              currencyCode = expense.currencyCode,
              originalAmount = expense.originalAmount,
              exchangeRateToBase = expense.exchangeRateToBase,
              signedAmountBase = expense.convertedAmountBase,
              description = "Reverso gasto anulado: ${expense.description}",
              referenceType = "EXPENSE_ANNUL",
              referenceId = expenseId,
              timestamp = now,
            ),
          )
        }
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "EXPENSE_ANNULLED",
          entityType = "EXPENSE",
          entityId = expenseId,
          details = "Gasto #${expense.id} (${expense.description}) anulado: $reason",
          timestamp = now,
        ),
      )
    }
  }

  // --- Cash Register & Capital / Withdrawals ---

  suspend fun openCashSession(
    businessId: Long,
    openingCashBase: BigDecimal,
    notes: String = "",
    userId: Long? = null,
  ): Long {
    require(openingCashBase >= BigDecimal.ZERO) { "El efectivo inicial no puede ser negativo." }
    val normalizedOpening = openingCashBase.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
    val now = System.currentTimeMillis()

    return db.withTransaction {
      val existingOpen = financeDao.getOpenCashSession(businessId)
      require(existingOpen == null) { "Ya existe una caja abierta. Ciérrala antes de abrir una nueva." }

      val business = businessConfigDao.getBusinessById(businessId)
      val baseCurrency = business?.primaryCurrency ?: "CUP"

      val sessionId = financeDao.insertCashSession(
        CashSessionEntity(
          businessId = businessId,
          openedByUserId = userId,
          openedAt = now,
          openingCashBase = normalizedOpening,
          expectedCashBase = normalizedOpening,
          status = "OPEN",
          notes = notes.trim(),
        ),
      )

      financeDao.insertCashMovement(
        CashMovementEntity(
          businessId = businessId,
          cashSessionId = sessionId,
          userId = userId,
          movementType = "OPENING",
          affectsPhysicalCash = true,
          currencyCode = baseCurrency,
          originalAmount = normalizedOpening,
          exchangeRateToBase = BigDecimal("1.0000"),
          signedAmountBase = normalizedOpening,
          description = "Apertura de caja",
          referenceType = "CASH_SESSION",
          referenceId = sessionId,
          timestamp = now,
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "CASH_SESSION_OPENED",
          entityType = "CASH_SESSION",
          entityId = sessionId,
          details = "Caja abierta con $normalizedOpening $baseCurrency",
          timestamp = now,
        ),
      )

      sessionId
    }
  }

  suspend fun recordCashAdjustment(
    businessId: Long,
    type: CashAdjustmentType,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    affectsPhysicalCash: Boolean,
    description: String,
    userId: Long? = null,
  ): Long {
    require(amountOriginal > BigDecimal.ZERO) { "El monto debe ser mayor que cero." }
    require(exchangeRateToBase > BigDecimal.ZERO) { "La tasa de cambio debe ser positiva." }
    val cleanDesc = description.trim().ifEmpty { type.label }
    val convertedBase = MoneyMath.convertToBase(amountOriginal, exchangeRateToBase)
    val now = System.currentTimeMillis()

    return db.withTransaction {
      val openSession = financeDao.getOpenCashSession(businessId)
        ?: error("Debes abrir la caja diaria para registrar ${type.label.lowercase()}.")

      val signedBase = if (type.isInflow) convertedBase else convertedBase.negate()
      val movementId = financeDao.insertCashMovement(
        CashMovementEntity(
          businessId = businessId,
          cashSessionId = openSession.id,
          userId = userId,
          movementType = type.code,
          affectsPhysicalCash = affectsPhysicalCash,
          currencyCode = currencyCode.trim().uppercase(),
          originalAmount = amountOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          exchangeRateToBase = exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
          signedAmountBase = signedBase,
          description = cleanDesc,
          referenceType = "CASH_ADJUSTMENT",
          referenceId = openSession.id,
          timestamp = now,
        ),
      )

      if (affectsPhysicalCash) {
        val updated = if (type.isInflow) {
          openSession.copy(
            totalCashInflowsBase = MoneyMath.add(openSession.totalCashInflowsBase, convertedBase),
          )
        } else {
          openSession.copy(
            totalOwnerWithdrawalsBase = MoneyMath.add(openSession.totalOwnerWithdrawalsBase, convertedBase),
          )
        }
        financeDao.updateCashSession(
          updated.copy(expectedCashBase = computeExpectedCash(updated)),
        )
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "CASH_${type.code}",
          entityType = "CASH_MOVEMENT",
          entityId = movementId,
          details = "${type.label}: $convertedBase ($cleanDesc)",
          timestamp = now,
        ),
      )

      movementId
    }
  }

  suspend fun closeCashSession(
    businessId: Long,
    sessionId: Long,
    countedCashBase: BigDecimal,
    notes: String = "",
    userId: Long? = null,
  ): CashSessionEntity {
    require(countedCashBase >= BigDecimal.ZERO) { "El efectivo contado no puede ser negativo." }
    val normalizedCounted = countedCashBase.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
    val now = System.currentTimeMillis()

    return db.withTransaction {
      val session = financeDao.getCashSessionById(sessionId) ?: error("Sesión de caja no encontrada.")
      require(session.status == "OPEN") { "Esta sesión de caja ya se encuentra cerrada." }

      val recomputedExpected = computeExpectedCash(session)
      val difference = MoneyMath.subtract(normalizedCounted, recomputedExpected)

      val closed = session.copy(
        closedByUserId = userId,
        closedAt = now,
        expectedCashBase = recomputedExpected,
        countedCashBase = normalizedCounted,
        differenceBase = difference,
        status = "CLOSED",
        notes = notes.trim().ifBlank { session.notes },
      )
      financeDao.updateCashSession(closed)

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "CASH_SESSION_CLOSED",
          entityType = "CASH_SESSION",
          entityId = sessionId,
          details = "Cierre de caja #$sessionId: Esperado $recomputedExpected | Contado $normalizedCounted | Diferencia $difference",
          timestamp = now,
        ),
      )

      closed
    }
  }

  // --- Customers & Receivable Debt Collections ---

  suspend fun saveCustomer(
    businessId: Long,
    customerId: Long? = null,
    name: String,
    phone: String = "",
    address: String = "",
    notes: String = "",
  ): Long = saleDao.let {
    // Reuse SaleRepository's identical customer CRUD rules via FinanceDao
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "El nombre del cliente es obligatorio." }

    db.withTransaction {
      if (customerId == null || customerId <= 0L) {
        val newId = saleDao.insertCustomer(
          CustomerEntity(
            businessId = businessId,
            name = cleanName,
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim(),
            isActive = true,
          ),
        )
        securityDao.insertAuditLog(
          AuditLogEntity(
            businessId = businessId,
            action = "CUSTOMER_CREATED",
            entityType = "CUSTOMER",
            entityId = newId,
            details = "Cliente creado: $cleanName",
          ),
        )
        newId
      } else {
        val current = financeDao.getCustomerById(customerId) ?: error("Cliente no encontrado.")
        saleDao.updateCustomer(
          current.copy(
            name = cleanName,
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim(),
          ),
        )
        customerId
      }
    }
  }

  suspend fun archiveCustomer(businessId: Long, customerId: Long) {
    db.withTransaction {
      val customer = financeDao.getCustomerById(customerId) ?: return@withTransaction
      require(customer.debtBalanceBase <= BigDecimal.ZERO) {
        "No puedes desactivar un cliente con saldo pendiente por cobrar."
      }
      financeDao.deactivateCustomer(customerId)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          action = "CUSTOMER_ARCHIVED",
          entityType = "CUSTOMER",
          entityId = customerId,
          details = "Cliente desactivado: ${customer.name}",
        ),
      )
    }
  }

  suspend fun collectCustomerDebt(
    businessId: Long,
    debtId: Long,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    paymentMethod: String,
    note: String = "",
    userId: Long? = null,
  ) {
    require(amountOriginal > BigDecimal.ZERO) { "El abono debe ser mayor que cero." }
    require(exchangeRateToBase > BigDecimal.ZERO) { "La tasa de cambio debe ser positiva." }

    val paymentBase = MoneyMath.convertToBase(amountOriginal, exchangeRateToBase)
    val isPhysicalCash = paymentMethod == "CASH"
    val normCurrency = currencyCode.trim().uppercase()
    val now = System.currentTimeMillis()

    db.withTransaction {
      val debt = financeDao.getDebtById(debtId) ?: error("Deuda no encontrada.")
      require(debt.status != "PAID" && debt.status != "ANNULLED") {
        "Esta deuda ya está cobrada o anulada."
      }
      require(paymentBase <= debt.remainingBase) {
        "El abono ($paymentBase) supera el saldo pendiente (${debt.remainingBase})."
      }

      val newPaid = MoneyMath.add(debt.paidBase, paymentBase)
      val newRemaining = MoneyMath.subtract(debt.remainingBase, paymentBase).max(MoneyMath.ZERO)
      val newStatus = if (newRemaining.compareTo(BigDecimal.ZERO) == 0) "PAID" else "PARTIAL"

      financeDao.updateDebt(
        debt.copy(
          paidBase = newPaid,
          remainingBase = newRemaining,
          status = newStatus,
        ),
      )

      val customer = financeDao.getCustomerById(debt.partyId)
      if (customer != null) {
        val updatedBalance = MoneyMath.subtract(customer.debtBalanceBase, paymentBase).max(MoneyMath.ZERO)
        financeDao.updateCustomerDebtBalance(customer.id, updatedBalance)
      }

      financeDao.insertPayment(
        PaymentEntity(
          businessId = businessId,
          userId = userId,
          referenceType = "DEBT_CUSTOMER",
          referenceId = debt.id,
          paymentMethod = paymentMethod,
          currencyCode = normCurrency,
          originalAmount = amountOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          exchangeRateToBase = exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
          convertedAmountBase = paymentBase,
          isPhysicalCash = isPhysicalCash,
          paymentDate = now,
          note = note.ifBlank { "Cobro a deuda #${debt.id}" },
        ),
      )

      val openSession = financeDao.getOpenCashSession(businessId)
      if (isPhysicalCash && openSession != null) {
        financeDao.insertCashMovement(
          CashMovementEntity(
            businessId = businessId,
            cashSessionId = openSession.id,
            userId = userId,
            movementType = "DEBT_COLLECTION_CASH",
            affectsPhysicalCash = true,
            currencyCode = normCurrency,
            originalAmount = amountOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            exchangeRateToBase = exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
            signedAmountBase = paymentBase,
            description = "Cobro deuda cliente #${debt.partyId}",
            referenceType = "DEBT_CUSTOMER",
            referenceId = debt.id,
            timestamp = now,
          ),
        )
        val updatedSession = openSession.copy(
          totalCashInflowsBase = MoneyMath.add(openSession.totalCashInflowsBase, paymentBase),
        )
        financeDao.updateCashSession(
          updatedSession.copy(expectedCashBase = computeExpectedCash(updatedSession)),
        )
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "CUSTOMER_DEBT_COLLECTED",
          entityType = "DEBT",
          entityId = debt.id,
          details = "Cobro a cliente: $paymentBase ($paymentMethod) | Saldo restante: $newRemaining",
          timestamp = now,
        ),
      )
    }
  }

  companion object {
    /**
     * Exact formula: expectedCash = initial + cashSales + cashInflows - cashExpenses - ownerWithdrawals
     */
    fun computeExpectedCash(session: CashSessionEntity): BigDecimal =
      session.openingCashBase
        .add(session.totalCashSalesBase)
        .add(session.totalCashInflowsBase)
        .subtract(session.totalCashExpensesBase)
        .subtract(session.totalOwnerWithdrawalsBase)
        .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
  }
}
