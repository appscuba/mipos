package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.AppPermissions
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductModality
import com.example.data.repository.SaveProductRequest
import com.example.data.repository.SecurityRepository
import com.example.data.repository.StockAdjustmentMode
import com.example.data.repository.StockAdjustmentRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Fase 8 — Perfiles y permisos de inventario pedidos por el propietario:
 * crear y dar de baja productos es SOLO del Propietario; los perfiles
 * Administrador y Almacenero pueden dar entrada y salida a los productos
 * (compras y ajustes), y la siembra de roles llega también a las
 * instalaciones existentes sin duplicarse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase8PermissionsTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var securityRepo: SecurityRepository
  private lateinit var inventoryRepo: InventoryRepository
  private var businessId: Long = 0L

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefs = AppPreferencesDataStore(context)
    businessRepo = BusinessRepository(db = db, preferences = prefs)
    securityRepo = SecurityRepository(db = db, preferences = prefs)
    inventoryRepo = InventoryRepository(db)

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Tienda Permisos",
        businessType = "Tienda",
        logoUri = null,
        phone = "",
        email = "",
        address = "",
        primaryCurrency = "CUP",
        secondaryCurrencyCode = null,
        secondaryCurrencyRate = null,
        gramsPerPound = BigDecimal("500"),
        allowNegativeStock = false,
      ),
    )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `existen administrador y almacenero con sus permisos y el dependiente no gestiona productos`() = runBlocking {
    val dao = db.securityDao()

    val adminRole = dao.getRoleByCode("ADMIN")
    val warehouseRole = dao.getRoleByCode("WAREHOUSE")
    assertTrue("Debe existir el perfil Administrador", adminRole != null)
    assertTrue("Debe existir el perfil Almacenero", warehouseRole != null)
    assertTrue("El permiso de ajustes debe existir", dao.getPermissionByCode(AppPermissions.STOCK_ADJUST) != null)

    val adminPerms = dao.getPermissionsForRole(adminRole!!.id).map { it.code }.toSet()
    assertTrue(AppPermissions.PURCHASES_MANAGE in adminPerms)
    assertTrue(AppPermissions.STOCK_ADJUST in adminPerms)
    assertTrue(AppPermissions.POS_SELL in adminPerms)
    assertFalse("El Administrador no crea ni da de baja productos", AppPermissions.PRODUCTS_MANAGE in adminPerms)
    assertFalse("El Administrador no administra usuarios", AppPermissions.USERS_MANAGE in adminPerms)
    assertFalse("El Administrador no cambia la configuración", AppPermissions.SETTINGS_MANAGE in adminPerms)

    val warehousePerms = dao.getPermissionsForRole(warehouseRole!!.id).map { it.code }.toSet()
    assertEquals(
      setOf(AppPermissions.PURCHASES_MANAGE, AppPermissions.STOCK_ADJUST),
      warehousePerms,
    )

    val clerkPerms = dao.getPermissionsForRole(dao.getRoleByCode("CLERK")!!.id).map { it.code }.toSet()
    assertFalse(AppPermissions.PRODUCTS_MANAGE in clerkPerms)
    assertFalse(AppPermissions.STOCK_ADJUST in clerkPerms)
    assertFalse(AppPermissions.PURCHASES_MANAGE in clerkPerms)

    val ownerPerms = dao.getPermissionsForRole(dao.getRoleByCode("OWNER")!!.id).map { it.code }.toSet()
    assertTrue(AppPermissions.PRODUCTS_MANAGE in ownerPerms)
    assertTrue(AppPermissions.STOCK_ADJUST in ownerPerms)
  }

  @Test
  fun `la siembra de roles repetida no duplica roles ni permisos`() = runBlocking {
    val dao = db.securityDao()
    businessRepo.ensureSystemRolesAndPermissions()
    businessRepo.ensureSystemRolesAndPermissions()

    val roles = dao.observeRoles().first()
    assertEquals(4, roles.size)
    assertEquals(
      setOf("OWNER", "CLERK", "ADMIN", "WAREHOUSE"),
      roles.map { it.code }.toSet(),
    )
    val warehouse = dao.getRoleByCode("WAREHOUSE")!!
    assertEquals(2, dao.getPermissionsForRole(warehouse.id).size)
  }

  @Test
  fun `solo el propietario crea y da de baja productos y las entradas y salidas son por perfil`() = runBlocking {
    val dao = db.securityDao()
    val owner = dao.observeUsers(businessId).first()
      .first { it.roleId == dao.getRoleByCode("OWNER")!!.id }
    val clerkId = securityRepo.createUser(
      businessId = businessId, fullName = "Marta Cajera", username = "marta",
      roleCode = "CLERK", pin = "1234", actorUserId = owner.id,
    )
    val adminId = securityRepo.createUser(
      businessId = businessId, fullName = "Ana Admin", username = "ana",
      roleCode = "ADMIN", pin = "1234", actorUserId = owner.id,
    )
    val warehouseId = securityRepo.createUser(
      businessId = businessId, fullName = "Luis Almacen", username = "luis",
      roleCode = "WAREHOUSE", pin = "1234", actorUserId = owner.id,
    )

    fun productRequest(userId: Long?) = SaveProductRequest(
      businessId = businessId,
      categoryId = null,
      name = "Arroz 1 lb",
      sku = "ARR-1",
      modality = ProductModality.WEIGHT,
      defaultSaleUnit = "lb",
      salePrice = BigDecimal("200"),
      avgCostPerSaleUnit = BigDecimal("150"),
      initialStockEnteredQty = BigDecimal("10"),
      initialStockUnitLabel = "lb",
      initialStockFactorToBase = BigDecimal("500"),
      userId = userId,
    )

    // El Propietario sí puede crear productos.
    val productId = inventoryRepo.saveProduct(productRequest(owner.id))
    assertTrue(productId > 0)

    // Dependiente, Administrador y Almacenero NO pueden crear productos.
    for (uid in listOf(clerkId, adminId, warehouseId)) {
      var denied = false
      try {
        inventoryRepo.saveProduct(productRequest(uid))
      } catch (_: IllegalArgumentException) {
        denied = true
      }
      assertTrue("El usuario $uid no debió crear productos", denied)
    }

    fun adjustment(userId: Long?) = StockAdjustmentRequest(
      businessId = businessId,
      productId = productId,
      userId = userId,
      mode = StockAdjustmentMode.ADD,
      enteredQuantity = BigDecimal("1"),
      unitLabel = "lb",
      conversionFactorToBase = BigDecimal("500"),
      reason = "Entrada de prueba",
    )

    // Entradas y salidas manuales: Almacenero y Administrador sí, Dependiente no.
    inventoryRepo.recordStockAdjustment(adjustment(warehouseId))
    inventoryRepo.recordStockAdjustment(adjustment(adminId))
    inventoryRepo.recordStockAdjustment(adjustment(owner.id))
    var clerkDenied = false
    try {
      inventoryRepo.recordStockAdjustment(adjustment(clerkId))
    } catch (_: IllegalArgumentException) {
      clerkDenied = true
    }
    assertTrue("El Dependiente no debió ajustar existencias", clerkDenied)

    // Dar de baja: ni el Administrador puede; solo el Propietario.
    var archiveDenied = false
    try {
      inventoryRepo.archiveProduct(businessId, productId, adminId)
    } catch (_: IllegalArgumentException) {
      archiveDenied = true
    }
    assertTrue("El Administrador no debió dar de baja el producto", archiveDenied)
    inventoryRepo.archiveProduct(businessId, productId, owner.id)
  }
}
