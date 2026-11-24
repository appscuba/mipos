package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.cost.CostEngine
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.local.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class SaleLineInput(
  val productId: Long,
  val productName: String,
  val unitLabel: String,
  val enteredQuantity: BigDecimal,
  val conversionFactorToBase: BigDecimal,
  /** Optional explicit unit price in base currency (e.g. presentation sale price). If null, derived from product.salePrice & saleUnits. */
  val overrideUnitSalePriceBase: BigDecimal? = null,
)

data class SalePaymentInput(
  val paymentMethod: String, // CASH, TRANSFER, USD, OTHER
  val currencyCode: String,
  val originalAmount: BigDecimal,
  val exchangeRateToBase: BigDecimal,
  val isPhysicalCash: Boolean = paymentMethod == "CASH",
  val note: String = "",
)

data class ConfirmSaleRequest(
  val businessId: Long,
  val customerId: Long? = null,
  val userId: Long? = null,
  val clientRequestId: String = UUID.randomUUID().toString(),
  val saleDate: Long = System.currentTimeMillis(),
  val discountBase: BigDecimal = MoneyMath.ZERO,
  val payments: List<SalePaymentInput> = emptyList(),
  val notes: String = "",
  val lines: List<SaleLineInput>,
)

data class SaleWithDetails(
  val sale: SaleEntity,
  val customerName: String?,
  val details: List<SaleDetailEntity>,
  val payments: List<PaymentEntity>,
)

class SaleRepository(private val db: AppDatabase) {
  private val saleDao = db.saleDao()
  private val inventoryDao = db.inventoryDao()
  private val businessConfigDao = db.businessConfigDao()
  private val securityDao = db.securityDao()

  fun observeCustomers(businessId: Long): Flow<List<CustomerEntity>> =
    saleDao.observeActiveCustomers(businessId)

  fun observeExchangeRates(businessId: Long) =
    businessConfigDao.observeExchangeRates(businessId)

  fun observeSales(businessId: Long): Flow<List<SaleWithDetails>> = combine(
    saleDao.observeSales(businessId),
    saleDao.observeAllCustomers(businessId),
    saleDao.observeAllSaleDetailsForBusiness(businessId),
    saleDao.observeAllSalePaymentsForBusiness(businessId),
  ) { sales, customers, details, payments ->
    val customerMap = customers.associateBy { it.id }
    val detailsBySale = details.groupBy { it.saleId }
    val paymentsBySale = payments.groupBy { it.referenceId }
    sales.map { sale ->
      SaleWithDetails(
        sale = sale,
        customerName = sale.customerId?.let { customerMap[it]?.name },
        details = detailsBySale[sale.id].orEmpty(),
        payments = paymentsBySale[sale.id].orEmpty(),
      )
    }
  }

  suspend fun saveCustomer(
    businessId: Long,
    customerId: Long? = null,
    name: String,
    phone: String = "",
    address: String = "",
    notes: String = "",
  ): Long {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "El nombre del cliente es obligatorio." }

    return db.withTransaction {
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
        val current = saleDao.getCustomerById(customerId)
          ?: error("Cliente no encontrado.")
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

  suspend fun confirmSale(request: ConfirmSaleRequest): Long {
    require(request.lines.isNotEmpty()) { "Agrega al menos un producto al carrito." }
    require(request.discountBase >= BigDecimal.ZERO) { "El descuento no puede ser negativo." }
    request.lines.forEach { line ->
      require(line.enteredQuantity > BigDecimal.ZERO) {
        "La cantidad de ${line.productName} debe ser mayor que cero."
      }
      require(line.conversionFactorToBase > BigDecimal.ZERO) {
        "La equivalencia de ${line.productName} debe ser positiva."
      }
    }
    request.payments.forEach { payment ->
      require(payment.originalAmount >= BigDecimal.ZERO) { "El monto de pago no puede ser negativo." }
      require(payment.exchangeRateToBase > BigDecimal.ZERO) { "La tasa de cambio del pago debe ser positiva." }
    }

    return db.withTransaction {
      // Idempotency check against rapid double-tap
      val existingByRequest = saleDao.getSaleByClientRequestId(request.clientRequestId)
      if (existingByRequest != null) {
        return@withTransaction existingByRequest.id
      }

      val business = businessConfigDao.getBusinessById(request.businessId)
        ?: error("Negocio no encontrado.")
      val settings = businessConfigDao.getSettings(request.businessId)
      val gramsPerPound = settings?.gramsPerPound ?: UnitConverter.DEFAULT_GRAMS_PER_POUND
      val allowNegative = settings?.allowNegativeStock ?: false
      val now = System.currentTimeMillis()

      // First pass: validate total stock per product across all cart lines
      val requestedBaseByProduct = mutableMapOf<Long, BigDecimal>()
      val loadedProducts = mutableMapOf<Long, com.example.data.local.db.entity.ProductEntity>()

      for (line in request.lines) {
        val product = loadedProducts.getOrPut(line.productId) {
          inventoryDao.getProductById(line.productId)
            ?: error("Producto no encontrado: ${line.productName}")
        }
        val baseDeducted = UnitConverter.toBaseUnits(line.enteredQuantity, line.conversionFactorToBase)
        val prev = requestedBaseByProduct[line.productId] ?: BigDecimal.ZERO
        requestedBaseByProduct[line.productId] = prev.add(baseDeducted)
      }

      if (!allowNegative) {
        for ((productId, totalReqBase) in requestedBaseByProduct) {
          val product = loadedProducts.getValue(productId)
          if (product.trackInventory && product.stockBaseQty < totalReqBase) {
            error(
              "Stock insuficiente para '${product.name}'. Disponible: ${MoneyMath.formatQuantity(product.stockBaseQty)} ${product.baseUnit}, solicitado: ${MoneyMath.formatQuantity(totalReqBase)} ${product.baseUnit}.",
            )
          }
        }
      }

      // Compute line subtotals, historical costs, and totals
      var subtotalBase = MoneyMath.ZERO
      var totalCostBase = MoneyMath.ZERO

      data class ComputedLine(
        val input: SaleLineInput,
        val product: com.example.data.local.db.entity.ProductEntity,
        val baseDeducted: BigDecimal,
        val unitSalePriceBase: BigDecimal,
        val lineTotalBase: BigDecimal,
        val lineCostBase: BigDecimal,
        val lineGrossProfitBase: BigDecimal,
      )

      val computedLines = request.lines.map { line ->
        val product = loadedProducts.getValue(line.productId)
        val modality = ProductModality.fromCode(product.modality)
        val baseDeducted = UnitConverter.toBaseUnits(line.enteredQuantity, line.conversionFactorToBase)
        val saleUnits = CostEngine.toSaleUnits(
          baseQuantity = baseDeducted,
          modality = modality,
          defaultSaleUnit = product.defaultSaleUnit,
          gramsPerPound = gramsPerPound,
        )

        val lineTotalBase = if (line.overrideUnitSalePriceBase != null) {
          MoneyMath.multiply(line.overrideUnitSalePriceBase, line.enteredQuantity)
        } else {
          MoneyMath.multiply(product.salePrice, saleUnits)
        }

        val unitSalePriceBase = if (line.overrideUnitSalePriceBase != null) {
          line.overrideUnitSalePriceBase.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
        } else if (line.enteredQuantity > BigDecimal.ZERO) {
          lineTotalBase.divide(line.enteredQuantity, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
        } else {
          MoneyMath.ZERO
        }

        val lineCostBase = MoneyMath.multiply(product.avgCostPerSaleUnit, saleUnits)
        val lineGrossProfitBase = CostEngine.computeGrossProfit(lineTotalBase, lineCostBase)

        subtotalBase = MoneyMath.add(subtotalBase, lineTotalBase)
        totalCostBase = MoneyMath.add(totalCostBase, lineCostBase)

        ComputedLine(
          input = line,
          product = product,
          baseDeducted = baseDeducted,
          unitSalePriceBase = unitSalePriceBase,
          lineTotalBase = lineTotalBase,
          lineCostBase = lineCostBase,
          lineGrossProfitBase = lineGrossProfitBase,
        )
      }

      val normalizedDiscount = request.discountBase.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
      require(normalizedDiscount <= subtotalBase) {
        "El descuento no puede superar el subtotal de la venta."
      }

      val totalBase = MoneyMath.subtract(subtotalBase, normalizedDiscount).max(MoneyMath.ZERO)
      val grossProfitBase = CostEngine.computeGrossProfit(totalBase, totalCostBase)

      // Compute payments
      val validPayments = request.payments.filter { it.originalAmount > BigDecimal.ZERO }
      val convertedPayments = validPayments.map { p ->
        p to MoneyMath.convertToBase(p.originalAmount, p.exchangeRateToBase)
      }
      val paidBase = convertedPayments.fold(MoneyMath.ZERO) { acc, (_, baseAmt) ->
        MoneyMath.add(acc, baseAmt)
      }
      require(paidBase <= totalBase) {
        "El total pagado ($paidBase) no puede superar el total de la venta ($totalBase)."
      }

      val pendingDebtBase = MoneyMath.subtract(totalBase, paidBase).max(MoneyMath.ZERO)
      if (pendingDebtBase > BigDecimal.ZERO && request.customerId == null) {
        error("Selecciona un cliente para registrar la venta a crédito o pago parcial.")
      }

      val openCashSession = saleDao.getOpenCashSession(request.businessId)
      val nextNumber = saleDao.countSalesForBusiness(request.businessId) + 1
      val receiptNumber = "V-${nextNumber.toString().padStart(5, '0')}"

      val saleId = saleDao.insertSale(
        SaleEntity(
          businessId = request.businessId,
          customerId = request.customerId,
          userId = request.userId,
          cashSessionId = openCashSession?.id,
          receiptNumber = receiptNumber,
          clientRequestId = request.clientRequestId,
          saleDate = request.saleDate,
          currencyCode = business.primaryCurrency,
          exchangeRateToBase = BigDecimal("1.0000"),
          subtotalBase = subtotalBase,
          discountBase = normalizedDiscount,
          totalBase = totalBase,
          totalOriginal = totalBase,
          paidBase = paidBase,
          pendingDebtBase = pendingDebtBase,
          totalCostOfGoodsBase = totalCostBase,
          grossProfitBase = grossProfitBase,
          isCreditSale = pendingDebtBase > BigDecimal.ZERO,
          status = "CONFIRMED",
          notes = request.notes.trim(),
          createdAt = now,
        ),
      )

      // Deduct inventory & write SaleDetailEntity + InventoryMovementEntity
      val currentStocks = loadedProducts.mapValues { it.value.stockBaseQty }.toMutableMap()
      val detailEntities = mutableListOf<SaleDetailEntity>()

      for (cl in computedLines) {
        val currentStock = currentStocks.getValue(cl.product.id)
        val newStock = if (cl.product.trackInventory) {
          currentStock.subtract(cl.baseDeducted).setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
        } else {
          currentStock
        }
        currentStocks[cl.product.id] = newStock

        if (cl.product.trackInventory) {
          inventoryDao.updateProductStock(cl.product.id, newStock, now)
        }

        detailEntities.add(
          SaleDetailEntity(
            saleId = saleId,
            productId = cl.product.id,
            productNameSnapshot = cl.product.name,
            unitLabel = cl.input.unitLabel,
            enteredQuantity = cl.input.enteredQuantity.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            appliedEquivalence = cl.input.conversionFactorToBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            baseQuantityDeducted = cl.baseDeducted,
            returnedBaseQuantity = BigDecimal.ZERO.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            unitSalePriceBase = cl.unitSalePriceBase,
            historicalUnitCostBase = cl.product.avgCostPerSaleUnit,
            lineTotalBase = cl.lineTotalBase,
            lineCostBase = cl.lineCostBase,
            lineGrossProfitBase = cl.lineGrossProfitBase,
          ),
        )

        inventoryDao.insertMovement(
          InventoryMovementEntity(
            businessId = request.businessId,
            productId = cl.product.id,
            userId = request.userId,
            movementType = "SALE_OUT",
            quantityBaseDelta = cl.baseDeducted.negate(),
            stockAfterBase = newStock,
            enteredQuantity = cl.input.enteredQuantity.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            enteredUnitLabel = cl.input.unitLabel,
            appliedEquivalence = cl.input.conversionFactorToBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            unitCostSnapshot = cl.product.avgCostPerSaleUnit,
            referenceType = "SALE",
            referenceId = saleId,
            note = "Venta $receiptNumber",
            timestamp = now,
          ),
        )
      }

      saleDao.insertSaleDetails(detailEntities)

      // Record payments & open cash session movements if applicable
      var sessionCashAdded = MoneyMath.ZERO
      var sessionTransferAdded = MoneyMath.ZERO

      for ((paymentInput, baseAmount) in convertedPayments) {
        val normCurrency = paymentInput.currencyCode.trim().uppercase()
        saleDao.insertPayment(
          PaymentEntity(
            businessId = request.businessId,
            userId = request.userId,
            referenceType = "SALE",
            referenceId = saleId,
            paymentMethod = paymentInput.paymentMethod,
            currencyCode = normCurrency,
            originalAmount = paymentInput.originalAmount.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            exchangeRateToBase = paymentInput.exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
            convertedAmountBase = baseAmount,
            isPhysicalCash = paymentInput.isPhysicalCash,
            paymentDate = now,
            note = paymentInput.note.ifBlank { "Cobro venta $receiptNumber" },
          ),
        )

        if (openCashSession != null && baseAmount > BigDecimal.ZERO) {
          val isCash = paymentInput.isPhysicalCash
          if (isCash) {
            sessionCashAdded = MoneyMath.add(sessionCashAdded, baseAmount)
          } else {
            sessionTransferAdded = MoneyMath.add(sessionTransferAdded, baseAmount)
          }
          saleDao.insertCashMovement(
            CashMovementEntity(
              businessId = request.businessId,
              cashSessionId = openCashSession.id,
              userId = request.userId,
              movementType = if (isCash) "SALE_CASH" else "SALE_TRANSFER",
              affectsPhysicalCash = isCash,
              currencyCode = normCurrency,
              originalAmount = paymentInput.originalAmount.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
              exchangeRateToBase = paymentInput.exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
              signedAmountBase = baseAmount,
              description = "Venta $receiptNumber (${paymentInput.paymentMethod})",
              referenceType = "SALE",
              referenceId = saleId,
              timestamp = now,
            ),
          )
        }
      }

      if (openCashSession != null && (sessionCashAdded > BigDecimal.ZERO || sessionTransferAdded > BigDecimal.ZERO)) {
        saleDao.updateCashSession(
          openCashSession.copy(
            totalCashSalesBase = MoneyMath.add(openCashSession.totalCashSalesBase, sessionCashAdded),
            totalTransferSalesBase = MoneyMath.add(openCashSession.totalTransferSalesBase, sessionTransferAdded),
            expectedCashBase = MoneyMath.add(openCashSession.expectedCashBase, sessionCashAdded),
          ),
        )
      }

      // Customer receivable debt if credit / partial payment
      if (pendingDebtBase > BigDecimal.ZERO && request.customerId != null) {
        val customer = saleDao.getCustomerById(request.customerId)
          ?: error("Cliente no encontrado.")
        saleDao.insertDebt(
          DebtEntity(
            businessId = request.businessId,
            partyType = "CUSTOMER_RECEIVABLE",
            partyId = customer.id,
            referenceType = "SALE",
            referenceId = saleId,
            currencyCode = business.primaryCurrency,
            originalAmount = pendingDebtBase,
            exchangeRateToBase = BigDecimal("1.0000"),
            totalBase = pendingDebtBase,
            paidBase = MoneyMath.ZERO,
            remainingBase = pendingDebtBase,
            status = "OPEN",
            createdAt = now,
          ),
        )
        saleDao.updateCustomerDebtBalance(
          customerId = customer.id,
          newBalance = MoneyMath.add(customer.debtBalanceBase, pendingDebtBase),
        )
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = request.businessId,
          userId = request.userId,
          action = "SALE_CONFIRMED",
          entityType = "SALE",
          entityId = saleId,
          details = "Venta $receiptNumber confirmada: Total ${MoneyMath.format(totalBase, business.primaryCurrency)} | Costo ${MoneyMath.format(totalCostBase, business.primaryCurrency)} | Ganancia ${MoneyMath.format(grossProfitBase, business.primaryCurrency)}",
          timestamp = now,
        ),
      )

      saleId
    }
  }

  /**
   * Non-destructive partial return of a single sale detail line.
   * Restores inventory using the original historical unit cost & equivalence snapshot.
   */
  suspend fun returnSaleItem(
    businessId: Long,
    saleId: Long,
    saleDetailId: Long,
    returnEnteredQty: BigDecimal,
    reason: String = "Devolución parcial",
    userId: Long? = null,
  ) {
    require(returnEnteredQty > BigDecimal.ZERO) { "La cantidad a devolver debe ser mayor que cero." }
    val now = System.currentTimeMillis()

    db.withTransaction {
      val sale = saleDao.getSaleById(saleId) ?: error("Venta no encontrada.")
      require(sale.status != "ANNULLED") { "No se puede devolver una venta ya anulada." }

      val detail = saleDao.getSaleDetailById(saleDetailId) ?: error("Detalle de venta no encontrado.")
      require(detail.saleId == saleId) { "El detalle no pertenece a esta venta." }

      val remainingBaseToReturn = detail.baseQuantityDeducted.subtract(detail.returnedBaseQuantity)
      val returnBaseQty = UnitConverter.toBaseUnits(returnEnteredQty, detail.appliedEquivalence)
      require(returnBaseQty > BigDecimal.ZERO && returnBaseQty <= remainingBaseToReturn) {
        "La cantidad a devolver supera lo pendiente por devolver en esta línea."
      }

      val product = inventoryDao.getProductById(detail.productId) ?: error("Producto no encontrado.")
      val newStock = if (product.trackInventory) {
        product.stockBaseQty.add(returnBaseQty).setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
      } else {
        product.stockBaseQty
      }
      if (product.trackInventory) {
        inventoryDao.updateProductStock(product.id, newStock, now)
      }

      inventoryDao.insertMovement(
        InventoryMovementEntity(
          businessId = businessId,
          productId = product.id,
          userId = userId,
          movementType = "SALE_RETURN_IN",
          quantityBaseDelta = returnBaseQty,
          stockAfterBase = newStock,
          enteredQuantity = returnEnteredQty.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
          enteredUnitLabel = detail.unitLabel,
          appliedEquivalence = detail.appliedEquivalence,
          unitCostSnapshot = detail.historicalUnitCostBase,
          referenceType = "SALE_RETURN",
          referenceId = saleId,
          note = "$reason (${sale.receiptNumber})",
          timestamp = now,
        ),
      )

      val ratio = returnBaseQty.divide(detail.baseQuantityDeducted, 8, RoundingMode.HALF_UP)
      val returnedTotalBase = detail.lineTotalBase.multiply(ratio).setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
      val returnedCostBase = detail.lineCostBase.multiply(ratio).setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
      val updatedReturnedBaseQty = detail.returnedBaseQuantity.add(returnBaseQty).setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)

      val newLineTotal = MoneyMath.subtract(detail.lineTotalBase, returnedTotalBase).max(MoneyMath.ZERO)
      val newLineCost = MoneyMath.subtract(detail.lineCostBase, returnedCostBase).max(MoneyMath.ZERO)
      val newLineProfit = CostEngine.computeGrossProfit(newLineTotal, newLineCost)

      saleDao.updateSaleDetail(
        detail.copy(
          returnedBaseQuantity = updatedReturnedBaseQty,
          lineTotalBase = newLineTotal,
          lineCostBase = newLineCost,
          lineGrossProfitBase = newLineProfit,
        ),
      )

      // Recompute sale totals
      val allDetails = saleDao.getSaleDetails(saleId)
      val allReturned = allDetails.all { it.returnedBaseQuantity.compareTo(it.baseQuantityDeducted) >= 0 }

      val newSubtotal = MoneyMath.subtract(sale.subtotalBase, returnedTotalBase).max(MoneyMath.ZERO)
      val newTotal = MoneyMath.subtract(newSubtotal, sale.discountBase).max(MoneyMath.ZERO)
      val newCost = MoneyMath.subtract(sale.totalCostOfGoodsBase, returnedCostBase).max(MoneyMath.ZERO)
      val newProfit = CostEngine.computeGrossProfit(newTotal, newCost)

      // Adjust pending debt first before cash refund
      var newPendingDebt = sale.pendingDebtBase
      var newPaid = sale.paidBase
      var refundBase = MoneyMath.ZERO
      val totalReduction = MoneyMath.subtract(sale.totalBase, newTotal).max(MoneyMath.ZERO)

      if (totalReduction > BigDecimal.ZERO) {
        if (newPendingDebt >= totalReduction) {
          newPendingDebt = MoneyMath.subtract(newPendingDebt, totalReduction)
          reduceCustomerDebtForReturn(sale, totalReduction)
        } else {
          val debtPart = newPendingDebt
          if (debtPart > BigDecimal.ZERO) {
            reduceCustomerDebtForReturn(sale, debtPart)
          }
          refundBase = MoneyMath.subtract(totalReduction, debtPart)
          newPendingDebt = MoneyMath.ZERO
          newPaid = MoneyMath.subtract(newPaid, refundBase).max(MoneyMath.ZERO)
        }
      }

      if (refundBase > BigDecimal.ZERO) {
        val openSession = saleDao.getOpenCashSession(businessId)
        if (openSession != null) {
          saleDao.insertCashMovement(
            CashMovementEntity(
              businessId = businessId,
              cashSessionId = openSession.id,
              userId = userId,
              movementType = "REFUND_CASH",
              affectsPhysicalCash = true,
              currencyCode = sale.currencyCode,
              originalAmount = refundBase,
              exchangeRateToBase = BigDecimal("1.0000"),
              signedAmountBase = refundBase.negate(),
              description = "Devolución en ${sale.receiptNumber}: $reason",
              referenceType = "SALE_RETURN",
              referenceId = saleId,
              timestamp = now,
            ),
          )
          saleDao.updateCashSession(
            openSession.copy(
              expectedCashBase = MoneyMath.subtract(openSession.expectedCashBase, refundBase),
            ),
          )
        }
      }

      saleDao.updateSale(
        sale.copy(
          subtotalBase = newSubtotal,
          totalBase = newTotal,
          totalOriginal = newTotal,
          paidBase = newPaid,
          pendingDebtBase = newPendingDebt,
          totalCostOfGoodsBase = newCost,
          grossProfitBase = newProfit,
          status = if (allReturned) "ANNULLED" else "PARTIALLY_RETURNED",
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "SALE_RETURN",
          entityType = "SALE",
          entityId = saleId,
          details = "Devolución en ${sale.receiptNumber}: ${MoneyMath.formatQuantity(returnEnteredQty)} ${detail.unitLabel} de ${detail.productNameSnapshot} ($reason)",
          timestamp = now,
        ),
      )
    }
  }

  private suspend fun reduceCustomerDebtForReturn(sale: SaleEntity, amount: BigDecimal) {
    val debt = saleDao.getDebtByReference("SALE", sale.id) ?: return
    val newRemaining = MoneyMath.subtract(debt.remainingBase, amount).max(MoneyMath.ZERO)
    val newStatus = if (newRemaining.compareTo(BigDecimal.ZERO) == 0) "PAID" else debt.status
    saleDao.updateDebt(debt.copy(remainingBase = newRemaining, status = newStatus))
    sale.customerId?.let { custId ->
      saleDao.getCustomerById(custId)?.let { customer ->
        saleDao.updateCustomerDebtBalance(
          custId,
          MoneyMath.subtract(customer.debtBalanceBase, amount).max(MoneyMath.ZERO),
        )
      }
    }
  }

  /**
   * Non-destructive annulment of an entire sale: preserves the historical record,
   * restores remaining unreturned inventory, and cancels open customer debt.
   */
  suspend fun annulSale(
    businessId: Long,
    saleId: Long,
    reason: String = "Anulación de venta",
    userId: Long? = null,
  ) {
    val now = System.currentTimeMillis()
    db.withTransaction {
      val sale = saleDao.getSaleById(saleId) ?: error("Venta no encontrada.")
      require(sale.status != "ANNULLED") { "Esta venta ya fue anulada." }

      val details = saleDao.getSaleDetails(saleId)
      for (detail in details) {
        val qtyToRestore = detail.baseQuantityDeducted.subtract(detail.returnedBaseQuantity)
        if (qtyToRestore > BigDecimal.ZERO) {
          val product = inventoryDao.getProductById(detail.productId) ?: continue
          val newStock = if (product.trackInventory) {
            product.stockBaseQty.add(qtyToRestore).setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
          } else {
            product.stockBaseQty
          }
          if (product.trackInventory) {
            inventoryDao.updateProductStock(product.id, newStock, now)
          }
          inventoryDao.insertMovement(
            InventoryMovementEntity(
              businessId = businessId,
              productId = product.id,
              userId = userId,
              movementType = "SALE_ANNUL_IN",
              quantityBaseDelta = qtyToRestore,
              stockAfterBase = newStock,
              enteredQuantity = detail.enteredQuantity,
              enteredUnitLabel = detail.unitLabel,
              appliedEquivalence = detail.appliedEquivalence,
              unitCostSnapshot = detail.historicalUnitCostBase,
              referenceType = "SALE_ANNUL",
              referenceId = saleId,
              note = "$reason (${sale.receiptNumber})",
              timestamp = now,
            ),
          )
        }
      }

      val debt = saleDao.getDebtByReference("SALE", saleId)
      if (debt != null && debt.status != "ANNULLED") {
        val customer = saleDao.getCustomerById(debt.partyId)
        if (customer != null) {
          val reduced = MoneyMath.subtract(customer.debtBalanceBase, debt.remainingBase).max(MoneyMath.ZERO)
          saleDao.updateCustomerDebtBalance(customer.id, reduced)
        }
        saleDao.updateDebt(debt.copy(remainingBase = MoneyMath.ZERO, status = "ANNULLED"))
      }

      saleDao.updateSale(
        sale.copy(
          pendingDebtBase = MoneyMath.ZERO,
          status = "ANNULLED",
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "SALE_ANNULLED",
          entityType = "SALE",
          entityId = saleId,
          details = "Venta ${sale.receiptNumber} anulada: $reason",
          timestamp = now,
        ),
      )
    }
  }
}
