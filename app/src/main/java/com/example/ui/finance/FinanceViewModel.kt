package com.example.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.repository.CashAdjustmentType
import com.example.data.repository.ExpenseWithCategory
import com.example.data.repository.FinanceOverviewSnapshot
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class ExpenseStatusFilter(val label: String) {
  ALL("Todos"),
  CONFIRMED("Confirmados"),
  ANNULLED("Anulados"),
}

data class FinanceUiState(
  val businessId: Long = 0L,
  val primaryCurrency: String = "CUP",
  val expenseCategories: List<ExpenseCategoryEntity> = emptyList(),
  val allExpenses: List<ExpenseWithCategory> = emptyList(),
  val filteredExpenses: List<ExpenseWithCategory> = emptyList(),
  val exchangeRates: List<ExchangeRateEntity> = emptyList(),
  val openCashSession: CashSessionEntity? = null,
  val statusFilter: ExpenseStatusFilter = ExpenseStatusFilter.ALL,
  val netSalesBase: BigDecimal = MoneyMath.ZERO,
  val costOfGoodsSoldBase: BigDecimal = MoneyMath.ZERO,
  val grossProfitBase: BigDecimal = MoneyMath.ZERO,
  val operatingExpensesBase: BigDecimal = MoneyMath.ZERO,
  val netProfitBase: BigDecimal = MoneyMath.ZERO,
  val totalInvestmentsBase: BigDecimal = MoneyMath.ZERO,
  val totalExtraIncomeBase: BigDecimal = MoneyMath.ZERO,
  val totalOwnerWithdrawalsBase: BigDecimal = MoneyMath.ZERO,
  val customerReceivableBase: BigDecimal = MoneyMath.ZERO,
  val supplierPayableBase: BigDecimal = MoneyMath.ZERO,
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class FinanceViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val financeRepository: FinanceRepository,
  private val currentUserIdProvider: () -> Long? = { null },
) : ViewModel() {

  private val statusFilter = MutableStateFlow(ExpenseStatusFilter.ALL)
  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val domainFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        financeRepository.observeFinanceOverview(bizId),
        financeRepository.observeExchangeRates(bizId),
      ) { overview, rates ->
        Triple(bizId, overview, rates)
      }
    }
  }

  val uiState: StateFlow<FinanceUiState> = combine(
    domainFlow,
    statusFilter,
    isSaving,
    feedbackMessage,
  ) { triple, filter, saving, msg ->
    if (triple == null) {
      FinanceUiState(statusFilter = filter, isSaving = saving, feedbackMessage = msg)
    } else {
      val (bizId, overview, rates) = triple
      val filtered = when (filter) {
        ExpenseStatusFilter.ALL -> overview.expenses
        ExpenseStatusFilter.CONFIRMED -> overview.expenses.filter { it.expense.status == "CONFIRMED" }
        ExpenseStatusFilter.ANNULLED -> overview.expenses.filter { it.expense.status == "ANNULLED" }
      }
      FinanceUiState(
        businessId = bizId,
        primaryCurrency = overview.primaryCurrency,
        expenseCategories = overview.expenseCategories,
        allExpenses = overview.expenses,
        filteredExpenses = filtered,
        exchangeRates = rates,
        openCashSession = overview.openCashSession,
        statusFilter = filter,
        netSalesBase = overview.netSalesBase,
        costOfGoodsSoldBase = overview.costOfGoodsSoldBase,
        grossProfitBase = overview.grossProfitBase,
        operatingExpensesBase = overview.operatingExpensesBase,
        netProfitBase = overview.netProfitBase,
        totalInvestmentsBase = overview.totalInvestmentsBase,
        totalExtraIncomeBase = overview.totalExtraIncomeBase,
        totalOwnerWithdrawalsBase = overview.totalOwnerWithdrawalsBase,
        customerReceivableBase = overview.customerReceivableBase,
        supplierPayableBase = overview.supplierPayableBase,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = FinanceUiState(),
  )

  fun setStatusFilter(filter: ExpenseStatusFilter) {
    statusFilter.value = filter
  }

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun saveExpenseCategory(name: String, onSuccess: () -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.saveExpenseCategory(bizId, name)
        feedbackMessage.value = "Categoría '$name' guardada"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo guardar la categoría."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun archiveExpenseCategory(categoryId: Long) {
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.archiveExpenseCategory(categoryId)
        feedbackMessage.value = "Categoría desactivada"
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo desactivar la categoría."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun recordExpense(
    expenseCategoryId: Long,
    description: String,
    currencyCode: String,
    originalAmount: BigDecimal,
    exchangeRateToBase: BigDecimal,
    paymentMethod: String,
    paidFromCashDrawer: Boolean,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.recordExpense(
          businessId = bizId,
          expenseCategoryId = expenseCategoryId,
          description = description,
          currencyCode = currencyCode,
          originalAmount = originalAmount,
          exchangeRateToBase = exchangeRateToBase,
          paymentMethod = paymentMethod,
          paidFromCashDrawer = paidFromCashDrawer,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "Gasto registrado correctamente"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo registrar el gasto."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun annulExpense(expenseId: Long, reason: String, onSuccess: () -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.annulExpense(
          businessId = bizId,
          expenseId = expenseId,
          reason = reason,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "Gasto anulado (registro conservado)"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo anular el gasto."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun recordCashAdjustment(
    type: CashAdjustmentType,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    affectsPhysicalCash: Boolean,
    description: String,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.recordCashAdjustment(
          businessId = bizId,
          type = type,
          amountOriginal = amountOriginal,
          currencyCode = currencyCode,
          exchangeRateToBase = exchangeRateToBase,
          affectsPhysicalCash = affectsPhysicalCash,
          description = description,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "${type.label} registrado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo registrar el movimiento."
      } finally {
        isSaving.value = false
      }
    }
  }

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val financeRepository: FinanceRepository,
    private val currentUserIdProvider: () -> Long? = { null },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      FinanceViewModel(businessIdFlow, financeRepository, currentUserIdProvider) as T
  }
}
