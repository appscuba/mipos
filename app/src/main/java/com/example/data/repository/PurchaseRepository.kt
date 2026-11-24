package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.cost.CostEngine
import com.example.core.cost.PurchaseCostLineInput
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.PurchaseDetailEntity
import com.example.data.local.db.entity.PurchaseEntity
import com.example.data.local.db.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode

data class PurchaseLineInput(
  val productId: Long,
  val productName: String,
  val unitLabel: String,
  val enteredQuantity: BigDecimal,
  val conversionFactorToBase: BigDecimal,
  val unitPriceOriginal: BigDecimal,
)

data class ConfirmPurchaseRequest(
  val businessId: Long,
  val supplierId: Long?,
  val userId: Long? = null,
  val invoiceNumber: String = "",
  val purchaseDate: Long = System.currentTimeMillis(),
  val currencyCode: String = "CUP",
  val exchangeRateToBase: BigDecimal = BigDecimal("1.0000"),
  val discountOriginal: BigDecimal = MoneyMath.ZERO,
  val transportCostOriginal: BigDecimal = MoneyMath.ZERO,
  val extraExpensesOriginal: BigDecimal = MoneyMath.ZERO,
  val paidAmountOriginal: BigDecimal,
  val paymentMethod: String = "CASH",
  val notes: String = "",
  val lines: List<PurchaseLineInput>,
)

data class PurchaseWithSupplier(
  val purchase: PurchaseEntity,
  val supplierName: String?,
  val details: List<PurchaseDetailEntity>,
)

data class SupplierDetailSnapshot(
  val supplier: SupplierEntity?,
  val purchases: List<PurchaseEntity>,
  val debts: List<DebtEntity>,
)

class PurchaseRepository(private val db: AppDatabase) {
  private val purchaseDao = db.purchaseDao()
  private val inventoryDao = db.inventoryDao()
  private val businessConfigDao = db.businessConfigDao()
  private val securityDao = db.securityDao()

  fun observePurchases(businessId: Long): Flow<List<PurchaseWithSupplier>> = combine(
    purchaseDao.observePurchases(businessId),
    purchaseDao.observeAllSuppliers(businessId),
    purchaseDao.observeAllPurchaseDetailsForBusiness(businessId),
  ) { purchases, suppliers, details ->
    val supplierMap = suppliers.associateBy { it.id }
    val detailsByPurchase = details.groupBy { it.purchaseId }
    purchases.map { purchase ->
      PurchaseWithSupplier(
        purchase = purchase,
        supplierName = purchase.supplierId?.let { supplierMap[it]?.name },
        details = detailsByPurchase[purchase.id].orEmpty(),
      )
    }
  }

  fun observeSuppliers(businessId: Long): Flow<List<SupplierEntity>> =
    purchaseDao.observeActiveSuppliers(businessId)

  fun observeExchangeRates(businessId: Long) =
    businessConfigDao.observeExchangeRates(businessId)

  fun observePaymentsForPurchase(purchaseId: Long): Flow<List<PaymentEntity>> =
    purchaseDao.observePaymentsForReference("PURCHASE", purchaseId)

  fun observePaymentsForDebt(debtId: Long): Flow<List<PaymentEntity>> =
    purchaseDao.observePaymentsForReference("DEBT_SUPPLIER", debtId)

  fun observeSupplierDetail(businessId: Long, supplierId: Long): Flow<SupplierDetailSnapshot> = combine(
    purchaseDao.observeSupplierById(supplierId),
    purchaseDao.observePurchasesForSupplier(supplierId),
    purchaseDao.observeDebtsForSupplier(businessId, supplierId),
  ) { supplier, purchases, debts ->
    SupplierDetailSnapshot(
      supplier = supplier,
      purchases = purchases,
      debts = debts,
    )
  }

  suspend fun saveSupplier(
    businessId: Long,
    supplierId: Long? = null,
    name: String,
    phone: String = "",
    address: String = "",
    notes: String = "",
  ): Long {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "El nombre del proveedor es obligatorio." }

    return db.withTransaction {
      if (supplierId == null || supplierId <= 0L) {
        val newId = purchaseDao.insertSupplier(
          SupplierEntity(
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
            action = "SUPPLIER_CREATED",
            entityType = "SUPPLIER",
            entityId = newId,
            details = "Proveedor creado: $cleanName",
          ),
        )
        newId
      } else {
        val current = purchaseDao.getSupplierById(supplierId)
          ?: error("Proveedor no encontrado.")
        purchaseDao.updateSupplier(
          current.copy(
            name = cleanName,
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim(),
          ),
        )
        supplierId
      }
    }
  }

  suspend fun archiveSupplier(businessId: Long, supplierId: Long) {
    db.withTransaction {
      val current = purchaseDao.getSupplierById(supplierId) ?: return@withTransaction
      require(current.balanceOwedBase <= BigDecimal.ZERO) {
        "No puedes desactivar un proveedor con deuda pendiente."
      }
      purchaseDao.deactivateSupplier(supplierId)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          action = "SUPPLIER_ARCHIVED",
          entityType = "SUPPLIER",
          entityId = supplierId,
          details = "Proveedor desactivado: ${current.name}",
        ),
      )
    }
  }

  suspend fun confirmPurchase(request: ConfirmPurchaseRequest): Long {
    require(request.lines.isNotEmpty()) { "Agrega al menos un producto a la compra." }
    require(request.exchangeRateToBase > BigDecimal.ZERO) { "La tasa de cambio debe ser mayor que cero." }
    require(request.discountOriginal >= BigDecimal.ZERO) { "El descuento no puede ser negativo." }
    require(request.transportCostOriginal >= BigDecimal.ZERO) { "El transporte no puede ser negativo." }
    require(request.extraExpensesOriginal >= BigDecimal.ZERO) { "Los gastos adicionales no pueden ser negativos." }
    require(request.paidAmountOriginal >= BigDecimal.ZERO) { "El monto pagado no puede ser negativo." }

    request.lines.forEach { line ->
      require(line.enteredQuantity > BigDecimal.ZERO) { "La cantidad de ${line.productName} debe ser mayor que cero." }
      require(line.conversionFactorToBase > BigDecimal.ZERO) { "La equivalencia de ${line.productName} debe ser positiva." }
      require(line.unitPriceOriginal >= BigDecimal.ZERO) { "El precio de compra de ${line.productName} no puede ser negativo." }
    }

    val subtotalOriginal = request.lines.fold(MoneyMath.ZERO) { acc, line ->
      val lineSub = MoneyMath.multiply(line.unitPriceOriginal, line.enteredQuantity)
      MoneyMath.add(acc, lineSub)
    }
    val totalOriginal = subtotalOriginal
      .subtract(request.discountOriginal)
      .add(request.transportCostOriginal)
      .add(request.extraExpensesOriginal)
      .max(BigDecimal.ZERO)
      .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)

    require(request.paidAmountOriginal <= totalOriginal) {
      "El monto pagado no puede superar el total de la compra."
    }

    val totalConvertedBase = MoneyMath.convertToBase(totalOriginal, request.exchangeRateToBase)
    val paidAmountBase = MoneyMath.convertToBase(request.paidAmountOriginal, request.exchangeRateToBase)
    val pendingDebtOriginal = totalOriginal.subtract(request.paidAmountOriginal)
      .max(BigDecimal.ZERO)
      .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
    val pendingDebtBase = totalConvertedBase.subtract(paidAmountBase)
      .max(BigDecimal.ZERO)
      .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)

    if (pendingDebtBase > BigDecimal.ZERO && request.supplierId == null) {
      error("Selecciona un proveedor para registrar deuda pendiente por esta compra.")
    }

    val costInputs = request.lines.map { line ->
      val baseAdded = UnitConverter.toBaseUnits(line.enteredQuantity, line.conversionFactorToBase)
      val lineSubOriginal = MoneyMath.multiply(line.unitPriceOriginal, line.enteredQuantity)
      PurchaseCostLineInput(
        productId = line.productId,
        baseQuantityAdded = baseAdded,
        lineSubtotalOriginal = lineSubOriginal,
      )
    }

    val costResults = CostEngine.allocatePurchaseCosts(
      lines = costInputs,
      exchangeRateToBase = request.exchangeRateToBase,
      discountOriginal = request.discountOriginal,
      transportOriginal = request.transportCostOriginal,
      extraExpensesOriginal = request.extraExpensesOriginal,
    )

    return db.withTransaction {
      val settings = businessConfigDao.getSettings(request.businessId)
      val gramsPerPound = settings?.gramsPerPound ?: UnitConverter.DEFAULT_GRAMS_PER_POUND
      val now = System.currentTimeMillis()

      val purchaseId = purchaseDao.insertPurchase(
        PurchaseEntity(
          businessId = request.businessId,
          supplierId = request.supplierId,
          userId = request.userId,
          invoiceNumber = request.invoiceNumber.trim(),
          purchaseDate = request.purchaseDate,
          currencyCode = request.currencyCode.trim().uppercase(),
          exchangeRateToBase = request.exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
          subtotalOriginal = subtotalOriginal,
          discountOriginal = request.discountOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          transportCostOriginal = request.transportCostOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          extraExpensesOriginal = request.extraExpensesOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          totalOriginal = totalOriginal,
          totalConvertedBase = totalConvertedBase,
          paidAmountBase = paidAmountBase,
          pendingDebtBase = pendingDebtBase,
          paymentMethod = request.paymentMethod,
          status = "CONFIRMED",
          notes = request.notes.trim(),
          createdAt = now,
        ),
      )

      val detailEntities = mutableListOf<PurchaseDetailEntity>()

      request.lines.forEachIndexed { idx, line ->
        val product = inventoryDao.getProductById(line.productId)
          ?: error("Producto no encontrado: ${line.productName}")
        val costResult = costResults[idx]
        val baseAdded = costInputs[idx].baseQuantityAdded
        val newStockBase = product.stockBaseQty.add(baseAdded)
          .setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)

        val modality = ProductModality.fromCode(product.modality)
        val newAvgCostPerSaleUnit = CostEngine.computeWeightedAverageCost(
          existingStockBase = product.stockBaseQty,
          currentAvgCostPerSaleUnit = product.avgCostPerSaleUnit,
          addedBaseQty = baseAdded,
          addedTotalCostBase = costResult.lineTotalRealCostBase,
          modality = modality,
          defaultSaleUnit = product.defaultSaleUnit,
          gramsPerPound = gramsPerPound,
        )

        inventoryDao.updateProduct(
          product.copy(
            stockBaseQty = newStockBase,
            avgCostPerSaleUnit = newAvgCostPerSaleUnit,
            updatedAt = now,
          ),
        )

        val realUnitCostPerEntered = if (line.enteredQuantity > BigDecimal.ZERO) {
          costResult.lineTotalRealCostBase.divide(line.enteredQuantity, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
        } else {
          MoneyMath.ZERO
        }

        detailEntities.add(
          PurchaseDetailEntity(
            purchaseId = purchaseId,
            productId = product.id,
            presentationLabel = line.unitLabel,
            enteredQuantity = line.enteredQuantity.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            appliedEquivalence = line.conversionFactorToBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            baseQuantityAdded = baseAdded,
            unitPriceOriginal = line.unitPriceOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            allocatedExtraCostBase = costResult.allocatedAdjustmentBase,
            realUnitCostBase = realUnitCostPerEntered,
            lineTotalBase = costResult.lineTotalRealCostBase,
          ),
        )

        inventoryDao.insertMovement(
          InventoryMovementEntity(
            businessId = request.businessId,
            productId = product.id,
            userId = request.userId,
            movementType = "PURCHASE_IN",
            quantityBaseDelta = baseAdded,
            stockAfterBase = newStockBase,
            enteredQuantity = line.enteredQuantity.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            enteredUnitLabel = line.unitLabel,
            appliedEquivalence = line.conversionFactorToBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            unitCostSnapshot = newAvgCostPerSaleUnit,
            referenceType = "PURCHASE",
            referenceId = purchaseId,
            note = if (request.invoiceNumber.isNotBlank()) "Compra #${request.invoiceNumber.trim()}" else "Compra #$purchaseId",
            timestamp = now,
          ),
        )
      }

      purchaseDao.insertPurchaseDetails(detailEntities)

      if (paidAmountBase > BigDecimal.ZERO) {
        purchaseDao.insertPayment(
          PaymentEntity(
            businessId = request.businessId,
            userId = request.userId,
            referenceType = "PURCHASE",
            referenceId = purchaseId,
            paymentMethod = request.paymentMethod,
            currencyCode = request.currencyCode.trim().uppercase(),
            originalAmount = request.paidAmountOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            exchangeRateToBase = request.exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
            convertedAmountBase = paidAmountBase,
            isPhysicalCash = request.paymentMethod == "CASH",
            paymentDate = now,
            note = "Pago inicial de compra #$purchaseId",
          ),
        )
      }

      if (pendingDebtBase > BigDecimal.ZERO && request.supplierId != null) {
        val supplier = purchaseDao.getSupplierById(request.supplierId)
          ?: error("Proveedor no encontrado.")
        purchaseDao.insertDebt(
          DebtEntity(
            businessId = request.businessId,
            partyType = "SUPPLIER_PAYABLE",
            partyId = supplier.id,
            referenceType = "PURCHASE",
            referenceId = purchaseId,
            currencyCode = request.currencyCode.trim().uppercase(),
            originalAmount = pendingDebtOriginal,
            exchangeRateToBase = request.exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
            totalBase = pendingDebtBase,
            paidBase = MoneyMath.ZERO,
            remainingBase = pendingDebtBase,
            status = "OPEN",
            createdAt = now,
          ),
        )
        purchaseDao.updateSupplierBalance(
          supplierId = supplier.id,
          newBalance = MoneyMath.add(supplier.balanceOwedBase, pendingDebtBase),
        )
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = request.businessId,
          userId = request.userId,
          action = "PURCHASE_CONFIRMED",
          entityType = "PURCHASE",
          entityId = purchaseId,
          details = "Compra #$purchaseId confirmada: ${MoneyMath.format(totalOriginal, request.currencyCode)} (Base: ${MoneyMath.format(totalConvertedBase, "")})",
          timestamp = now,
        ),
      )

      purchaseId
    }
  }

  suspend fun annulPurchase(
    businessId: Long,
    purchaseId: Long,
    reason: String = "Anulación de compra",
    userId: Long? = null,
  ) {
    val now = System.currentTimeMillis()
    db.withTransaction {
      val purchase = purchaseDao.getPurchaseById(purchaseId)
        ?: error("Compra no encontrada.")
      require(purchase.status != "ANNULLED") { "Esta compra ya fue anulada." }

      val settings = businessConfigDao.getSettings(businessId)
      val allowNegative = settings?.allowNegativeStock ?: false
      val details = purchaseDao.getPurchaseDetails(purchaseId)

      for (detail in details) {
        val product = inventoryDao.getProductById(detail.productId) ?: continue
        val newStock = product.stockBaseQty.subtract(detail.baseQuantityAdded)
          .setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
        if (!allowNegative && newStock < BigDecimal.ZERO) {
          error("No se puede anular: parte del stock de '${product.name}' ya fue utilizado.")
        }
        inventoryDao.updateProductStock(product.id, newStock, now)
        inventoryDao.insertMovement(
          InventoryMovementEntity(
            businessId = businessId,
            productId = product.id,
            userId = userId,
            movementType = "PURCHASE_ANNUL_OUT",
            quantityBaseDelta = detail.baseQuantityAdded.negate(),
            stockAfterBase = newStock,
            enteredQuantity = detail.enteredQuantity,
            enteredUnitLabel = detail.presentationLabel,
            appliedEquivalence = detail.appliedEquivalence,
            unitCostSnapshot = product.avgCostPerSaleUnit,
            referenceType = "PURCHASE_ANNUL",
            referenceId = purchaseId,
            note = reason,
            timestamp = now,
          ),
        )
      }

      val debt = purchaseDao.getDebtByReference("PURCHASE", purchaseId)
      if (debt != null && debt.status != "ANNULLED") {
        val supplier = purchaseDao.getSupplierById(debt.partyId)
        if (supplier != null) {
          val reduced = supplier.balanceOwedBase.subtract(debt.remainingBase).max(BigDecimal.ZERO)
            .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
          purchaseDao.updateSupplierBalance(supplier.id, reduced)
        }
        purchaseDao.updateDebt(debt.copy(remainingBase = MoneyMath.ZERO, status = "ANNULLED"))
      }

      purchaseDao.updatePurchaseStatus(purchaseId, "ANNULLED")

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "PURCHASE_ANNULLED",
          entityType = "PURCHASE",
          entityId = purchaseId,
          details = "Compra #$purchaseId anulada: $reason",
          timestamp = now,
        ),
      )
    }
  }

  suspend fun paySupplierDebt(
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
    val now = System.currentTimeMillis()

    db.withTransaction {
      val debt = purchaseDao.getDebtById(debtId) ?: error("Deuda no encontrada.")
      require(debt.status != "PAID" && debt.status != "ANNULLED") {
        "Esta deuda ya está saldada o anulada."
      }
      require(paymentBase <= debt.remainingBase) {
        "El abono supera el saldo pendiente (${debt.remainingBase})."
      }

      val newPaid = MoneyMath.add(debt.paidBase, paymentBase)
      val newRemaining = MoneyMath.subtract(debt.remainingBase, paymentBase).max(MoneyMath.ZERO)
      val newStatus = if (newRemaining.compareTo(BigDecimal.ZERO) == 0) "PAID" else "PARTIAL"

      purchaseDao.updateDebt(
        debt.copy(
          paidBase = newPaid,
          remainingBase = newRemaining,
          status = newStatus,
        ),
      )

      val supplier = purchaseDao.getSupplierById(debt.partyId)
      if (supplier != null) {
        val updatedBalance = MoneyMath.subtract(supplier.balanceOwedBase, paymentBase).max(MoneyMath.ZERO)
        purchaseDao.updateSupplierBalance(supplier.id, updatedBalance)
      }

      purchaseDao.insertPayment(
        PaymentEntity(
          businessId = businessId,
          userId = userId,
          referenceType = "DEBT_SUPPLIER",
          referenceId = debt.id,
          paymentMethod = paymentMethod,
          currencyCode = currencyCode.trim().uppercase(),
          originalAmount = amountOriginal.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          exchangeRateToBase = exchangeRateToBase.setScale(MoneyMath.RATE_SCALE, RoundingMode.HALF_UP),
          convertedAmountBase = paymentBase,
          isPhysicalCash = paymentMethod == "CASH",
          paymentDate = now,
          note = note.ifBlank { "Abono a deuda #${debt.id}" },
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "SUPPLIER_DEBT_PAYMENT",
          entityType = "DEBT",
          entityId = debt.id,
          details = "Abono a proveedor: $paymentBase (Saldo restante: $newRemaining)",
          timestamp = now,
        ),
      )
    }
  }
}
