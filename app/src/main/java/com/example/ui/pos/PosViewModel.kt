package com.example.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.cost.CostEngine
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.license.PlanLimits
import com.example.data.license.PlanRules
import com.example.data.license.PlanState
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.BusinessWorkspaceSnapshot
import com.example.data.repository.ConfirmSaleRequest
import com.example.data.repository.InventoryOverview
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.SaleLineInput
import com.example.data.repository.SalePaymentInput
import com.example.data.repository.SaleRepository
import com.example.data.repository.SaleWithDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.UUID

data class PosCartItem(
  val lineId: String = UUID.randomUUID().toString(),
  val productWithDetails: ProductWithDetails,
  val unitLabel: String,
  val enteredQuantity: BigDecimal,
  val conversionFactorToBase: BigDecimal,
  val overrideUnitSalePriceBase: BigDecimal? = null,
  val gramsPerPound: BigDecimal,
) {
  val baseQuantityDeducted: BigDecimal
    get() = UnitConverter.toBaseUnits(enteredQuantity, conversionFactorToBase)

  val lineTotalBase: BigDecimal
    get() {
      if (overrideUnitSalePriceBase != null) {
        return MoneyMath.multiply(overrideUnitSalePriceBase, enteredQuantity)
      }
      val product = productWithDetails.product
      val saleUnits = CostEngine.toSaleUnits(
        baseQuantity = baseQuantityDeducted,
        modality = ProductModality.fromCode(product.modality),
        defaultSaleUnit = product.defaultSaleUnit,
        gramsPerPound = gramsPerPound,
      )
      return MoneyMath.multiply(product.salePrice, saleUnits)
    }
}

private data class PosDraftState(
  val searchQuery: String = "",
  val selectedCategoryId: Long? = null,
  val cartItems: List<PosCartItem> = emptyList(),
  val selectedCustomerId: Long? = null,
  val discountBase: BigDecimal = MoneyMath.ZERO,
  val clientRequestId: String = UUID.randomUUID().toString(),
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
  val completedSaleId: Long? = null,
)

data class PosUiState(
  val businessId: Long = 0L,
  val business: BusinessEntity? = null,
  val products: List<ProductWithDetails> = emptyList(),
  val filteredProducts: List<ProductWithDetails> = emptyList(),
  val categories: List<CategoryEntity> = emptyList(),
  val customers: List<CustomerEntity> = emptyList(),
  val exchangeRates: List<ExchangeRateEntity> = emptyList(),
  val primaryCurrency: String = "CUP",
  val gramsPerPound: BigDecimal = UnitConverter.DEFAULT_GRAMS_PER_POUND,
  val allowNegativeStock: Boolean = false,
  val searchQuery: String = "",
  val selectedCategoryId: Long? = null,
  val cartItems: List<PosCartItem> = emptyList(),
  val selectedCustomerId: Long? = null,
  val subtotalBase: BigDecimal = MoneyMath.ZERO,
  val discountBase: BigDecimal = MoneyMath.ZERO,
  val totalBase: BigDecimal = MoneyMath.ZERO,
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
  val completedSale: SaleWithDetails? = null,
  val planState: PlanState = PlanState.TRIAL,
  val salesThisMonth: Int = 0,
)

class PosViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val saleRepository: SaleRepository,
  private val inventoryRepository: InventoryRepository,
  private val businessRepository: BusinessRepository,
  private val preferences: AppPreferencesDataStore,
) : ViewModel() {

  private val draft = MutableStateFlow(PosDraftState())

  @OptIn(ExperimentalCoroutinesApi::class)
  private val domainFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        inventoryRepository.observeInventoryOverview(bizId),
        saleRepository.observeCustomers(bizId),
        saleRepository.observeExchangeRates(bizId),
        saleRepository.observeSales(bizId),
        businessRepository.observeWorkspaceSnapshot(),
      ) { inventory, customers, rates, sales, workspace ->
        PosDomainBundle(
          businessId = bizId,
          inventory = inventory,
          customers = customers,
          rates = rates,
          sales = sales,
          workspace = workspace,
        )
      }
    }
  }

  val uiState: StateFlow<PosUiState> = combine(domainFlow, draft, preferences.preferencesFlow) { bundle, d, prefs ->
    val nowMs = System.currentTimeMillis()
    val planState = PlanRules.resolve(prefs, nowMs)
    if (bundle == null) {
      PosUiState(isSaving = d.isSaving, feedbackMessage = d.feedbackMessage, planState = planState)
    } else {
      val q = d.searchQuery.trim().lowercase()
      val filtered = bundle.inventory.items.filter { item ->
        val matchesCat = d.selectedCategoryId == null || item.product.categoryId == d.selectedCategoryId
        val matchesQuery = q.isEmpty() ||
          item.product.name.lowercase().contains(q) ||
          item.product.sku.lowercase().contains(q)
        matchesCat && matchesQuery
      }

      val subtotal = d.cartItems.fold(MoneyMath.ZERO) { acc, item ->
        MoneyMath.add(acc, item.lineTotalBase)
      }
      val safeDiscount = d.discountBase.min(subtotal).max(MoneyMath.ZERO)
      val total = MoneyMath.subtract(subtotal, safeDiscount).max(MoneyMath.ZERO)
      val completedSale = d.completedSaleId?.let { id ->
        bundle.sales.firstOrNull { it.sale.id == id }
      }

      PosUiState(
        businessId = bundle.businessId,
        business = bundle.workspace.business,
        products = bundle.inventory.items,
        filteredProducts = filtered,
        categories = bundle.inventory.categories,
        customers = bundle.customers,
        exchangeRates = bundle.rates,
        primaryCurrency = bundle.inventory.primaryCurrency,
        gramsPerPound = bundle.inventory.gramsPerPound,
        allowNegativeStock = bundle.workspace.settings?.allowNegativeStock ?: false,
        searchQuery = d.searchQuery,
        selectedCategoryId = d.selectedCategoryId,
        cartItems = d.cartItems,
        selectedCustomerId = d.selectedCustomerId,
        subtotalBase = subtotal,
        discountBase = safeDiscount,
        totalBase = total,
        isSaving = d.isSaving,
        feedbackMessage = d.feedbackMessage,
        completedSale = completedSale,
        planState = planState,
        salesThisMonth = countSalesInCurrentMonth(bundle.sales, nowMs),
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = PosUiState(),
  )

  fun updateSearch(query: String) {
    draft.update { it.copy(searchQuery = query) }
  }

  fun selectCategory(categoryId: Long?) {
    draft.update { it.copy(selectedCategoryId = categoryId) }
  }

  fun selectCustomer(customerId: Long?) {
    draft.update { it.copy(selectedCustomerId = customerId) }
  }

  fun setDiscount(discountBase: BigDecimal) {
    draft.update { it.copy(discountBase = discountBase.max(MoneyMath.ZERO)) }
  }

  fun clearFeedback() {
    draft.update { it.copy(feedbackMessage = null) }
  }

  fun dismissReceipt() {
    draft.update { it.copy(completedSaleId = null) }
  }

  fun addItemToCart(
    productWithDetails: ProductWithDetails,
    unitLabel: String,
    enteredQuantity: BigDecimal,
    conversionFactorToBase: BigDecimal,
    overrideUnitSalePriceBase: BigDecimal? = null,
    onSuccess: () -> Unit,
  ) {
    if (enteredQuantity <= BigDecimal.ZERO) {
      draft.update { it.copy(feedbackMessage = "La cantidad debe ser mayor que cero.") }
      return
    }

    val state = uiState.value
    val product = productWithDetails.product
    val requestedNewBase = UnitConverter.toBaseUnits(enteredQuantity, conversionFactorToBase)
    val alreadyInCartBase = state.cartItems
      .filter { it.productWithDetails.product.id == product.id }
      .fold(BigDecimal.ZERO) { acc, line -> acc.add(line.baseQuantityDeducted) }

    if (product.trackInventory && !state.allowNegativeStock &&
      alreadyInCartBase.add(requestedNewBase) > product.stockBaseQty
    ) {
      draft.update {
        it.copy(
          feedbackMessage = "Stock insuficiente para '${product.name}'. Disponible: ${MoneyMath.formatQuantity(product.stockBaseQty)} ${product.baseUnit}.",
        )
      }
      return
    }

    draft.update { current ->
      // Merge identical product + unitLabel + override price if already in cart
      val existingIndex = current.cartItems.indexOfFirst {
        it.productWithDetails.product.id == product.id &&
          it.unitLabel == unitLabel &&
          it.conversionFactorToBase.compareTo(conversionFactorToBase) == 0 &&
          ((it.overrideUnitSalePriceBase == null && overrideUnitSalePriceBase == null) ||
            (it.overrideUnitSalePriceBase != null && overrideUnitSalePriceBase != null &&
              it.overrideUnitSalePriceBase.compareTo(overrideUnitSalePriceBase) == 0))
      }

      val updatedCart = if (existingIndex >= 0) {
        current.cartItems.toMutableList().also { list ->
          val old = list[existingIndex]
          list[existingIndex] = old.copy(
            enteredQuantity = old.enteredQuantity.add(enteredQuantity).setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
          )
        }
      } else {
        current.cartItems + PosCartItem(
          productWithDetails = productWithDetails,
          unitLabel = unitLabel,
          enteredQuantity = enteredQuantity.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
          conversionFactorToBase = conversionFactorToBase,
          overrideUnitSalePriceBase = overrideUnitSalePriceBase,
          gramsPerPound = state.gramsPerPound,
        )
      }
      current.copy(cartItems = updatedCart, feedbackMessage = null)
    }
    onSuccess()
  }

  fun quickAddDefaultUnit(productWithDetails: ProductWithDetails) {
    val product = productWithDetails.product
    val modality = ProductModality.fromCode(product.modality)
    val state = uiState.value
    val (unitLabel, qty, factor) = if (modality == ProductModality.WEIGHT) {
      when (product.defaultSaleUnit) {
        "KG" -> Triple("kg", BigDecimal.ONE, UnitConverter.GRAMS_PER_KILOGRAM)
        "GRAM" -> Triple("g", BigDecimal("100"), BigDecimal.ONE)
        else -> Triple("lb", BigDecimal.ONE, state.gramsPerPound)
      }
    } else {
      Triple("u", BigDecimal.ONE, BigDecimal.ONE)
    }
    addItemToCart(
      productWithDetails = productWithDetails,
      unitLabel = unitLabel,
      enteredQuantity = qty,
      conversionFactorToBase = factor,
      overrideUnitSalePriceBase = null,
      onSuccess = {},
    )
  }

  fun quickDecrementDefaultUnit(productWithDetails: ProductWithDetails) {
    val product = productWithDetails.product
    val modality = ProductModality.fromCode(product.modality)
    val state = uiState.value
    val (unitLabel, step) = if (modality == ProductModality.WEIGHT) {
      when (product.defaultSaleUnit) {
        "KG" -> "kg" to BigDecimal.ONE
        "GRAM" -> "g" to BigDecimal("100")
        else -> "lb" to BigDecimal.ONE
      }
    } else {
      "u" to BigDecimal.ONE
    }
    val matchingLine = state.cartItems.lastOrNull {
      it.productWithDetails.product.id == product.id &&
        it.unitLabel == unitLabel &&
        it.overrideUnitSalePriceBase == null
    } ?: return
    updateCartItemQuantity(matchingLine.lineId, matchingLine.enteredQuantity.subtract(step))
  }

  fun updateCartItemQuantity(lineId: String, newQty: BigDecimal) {
    if (newQty <= BigDecimal.ZERO) {
      removeCartItem(lineId)
      return
    }
    val state = uiState.value
    val target = state.cartItems.firstOrNull { it.lineId == lineId } ?: return
    val product = target.productWithDetails.product
    val otherLinesBase = state.cartItems
      .filter { it.lineId != lineId && it.productWithDetails.product.id == product.id }
      .fold(BigDecimal.ZERO) { acc, l -> acc.add(l.baseQuantityDeducted) }
    val newBase = UnitConverter.toBaseUnits(newQty, target.conversionFactorToBase)

    if (product.trackInventory && !state.allowNegativeStock && otherLinesBase.add(newBase) > product.stockBaseQty) {
      draft.update {
        it.copy(feedbackMessage = "Stock máximo alcanzado para '${product.name}'.")
      }
      return
    }

    draft.update { current ->
      current.copy(
        cartItems = current.cartItems.map { item ->
          if (item.lineId == lineId) item.copy(enteredQuantity = newQty) else item
        },
      )
    }
  }

  fun removeCartItem(lineId: String) {
    draft.update { current ->
      current.copy(cartItems = current.cartItems.filterNot { it.lineId == lineId })
    }
  }

  fun clearCart() {
    draft.update {
      it.copy(
        cartItems = emptyList(),
        discountBase = MoneyMath.ZERO,
        selectedCustomerId = null,
        clientRequestId = UUID.randomUUID().toString(),
      )
    }
  }

  fun quickCreateCustomer(name: String, phone: String, onCreated: (Long) -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    // Límite de clientes del plan gratuito también en el alta rápida.
    val stateNow = uiState.value
    if (stateNow.planState == PlanState.FREE && stateNow.customers.size >= PlanLimits.MAX_FREE_CUSTOMERS) {
      draft.update {
        it.copy(feedbackMessage = "Plan gratuito: llegaste a los ${PlanLimits.MAX_FREE_CUSTOMERS} clientes. Activa una licencia para añadir más.")
      }
      return
    }
    viewModelScope.launch {
      draft.update { it.copy(isSaving = true) }
      try {
        val id = saleRepository.saveCustomer(businessId = bizId, name = name, phone = phone)
        draft.update {
          it.copy(
            isSaving = false,
            selectedCustomerId = id,
            feedbackMessage = "Cliente '$name' creado",
          )
        }
        onCreated(id)
      } catch (e: Exception) {
        draft.update {
          it.copy(isSaving = false, feedbackMessage = e.message ?: "No se pudo crear el cliente.")
        }
      }
    }
  }

  fun confirmSale(
    payments: List<SalePaymentInput>,
    notes: String,
    onSuccess: (Long) -> Unit,
  ) {
    val state = uiState.value
    val currentDraft = draft.value
    if (state.businessId <= 0L || currentDraft.isSaving) return
    // Límite de ventas del mes en el plan gratuito.
    if (state.planState == PlanState.FREE && state.salesThisMonth >= PlanLimits.MAX_FREE_SALES_PER_MONTH) {
      draft.update {
        it.copy(feedbackMessage = "Plan gratuito: llegaste a las ${PlanLimits.MAX_FREE_SALES_PER_MONTH} ventas de este mes. Activa una licencia para seguir vendiendo.")
      }
      return
    }

    viewModelScope.launch {
      draft.update { it.copy(isSaving = true) }
      try {
        val saleId = saleRepository.confirmSale(
          ConfirmSaleRequest(
            businessId = state.businessId,
            customerId = state.selectedCustomerId,
            clientRequestId = currentDraft.clientRequestId,
            discountBase = state.discountBase,
            payments = payments,
            notes = notes,
            lines = state.cartItems.map { item ->
              SaleLineInput(
                productId = item.productWithDetails.product.id,
                productName = item.productWithDetails.product.name,
                unitLabel = item.unitLabel,
                enteredQuantity = item.enteredQuantity,
                conversionFactorToBase = item.conversionFactorToBase,
                overrideUnitSalePriceBase = item.overrideUnitSalePriceBase,
              )
            },
          ),
        )
        // Aviso suave del plan gratuito al cruzar el 80 % del mes.
        val newMonthCount = state.salesThisMonth + 1
        val successMessage = if (
          state.planState == PlanState.FREE &&
          newMonthCount == PlanLimits.warnThreshold(PlanLimits.MAX_FREE_SALES_PER_MONTH)
        ) {
          "Venta confirmada correctamente. Ya usaste $newMonthCount de tus " +
            "${PlanLimits.MAX_FREE_SALES_PER_MONTH} ventas gratuitas de este mes: " +
            "activa una licencia para vender sin límites."
        } else {
          "Venta confirmada correctamente"
        }
        draft.update {
          it.copy(
            cartItems = emptyList(),
            discountBase = MoneyMath.ZERO,
            selectedCustomerId = null,
            clientRequestId = UUID.randomUUID().toString(),
            isSaving = false,
            completedSaleId = saleId,
            feedbackMessage = successMessage,
          )
        }
        onSuccess(saleId)
      } catch (e: Exception) {
        draft.update {
          it.copy(
            isSaving = false,
            feedbackMessage = e.message ?: "Error al confirmar la venta.",
          )
        }
      }
    }
  }

  /** Ventas válidas (no anuladas) del mes natural en curso. */
  private fun countSalesInCurrentMonth(sales: List<SaleWithDetails>, nowMs: Long): Int {
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val year = nowCal.get(Calendar.YEAR)
    val month = nowCal.get(Calendar.MONTH)
    val saleCal = Calendar.getInstance()
    return sales.count { item ->
      if (item.sale.status == "ANNULLED") {
        false
      } else {
        saleCal.timeInMillis = item.sale.saleDate
        saleCal.get(Calendar.YEAR) == year && saleCal.get(Calendar.MONTH) == month
      }
    }
  }

  private data class PosDomainBundle(
    val businessId: Long,
    val inventory: InventoryOverview,
    val customers: List<CustomerEntity>,
    val rates: List<ExchangeRateEntity>,
    val sales: List<SaleWithDetails>,
    val workspace: BusinessWorkspaceSnapshot,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val saleRepository: SaleRepository,
    private val inventoryRepository: InventoryRepository,
    private val businessRepository: BusinessRepository,
    private val preferences: AppPreferencesDataStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      PosViewModel(businessIdFlow, saleRepository, inventoryRepository, businessRepository, preferences) as T
  }
}
