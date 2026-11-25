package com.example.ui.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.data.license.PlanRules
import com.example.data.license.PlanState
import com.example.data.repository.AppPermissions
import com.example.ui.MainAppState
import com.example.ui.backups.BackupsScreen
import com.example.ui.backups.BackupsViewModel
import com.example.ui.cash.CashRegisterScreen
import com.example.ui.cash.CashRegisterViewModel
import com.example.ui.currencies.CurrenciesScreen
import com.example.ui.currencies.CurrenciesViewModel
import com.example.ui.customers.CustomersScreen
import com.example.ui.customers.CustomersViewModel
import com.example.ui.finance.FinanceScreen
import com.example.ui.finance.FinanceViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.inventory.InventoryScreen
import com.example.ui.inventory.InventoryViewModel
import com.example.ui.inventory.ProductDetailScreen
import com.example.ui.inventory.ProductDetailViewModel
import com.example.ui.license.LicenseScreen
import com.example.ui.license.LicenseViewModel
import com.example.ui.license.PlanLockedContent
import com.example.ui.navigation.AppDestination
import com.example.ui.navigation.BottomNavTab
import com.example.ui.pos.PosScreen
import com.example.ui.pos.PosViewModel
import com.example.ui.purchases.PurchasesScreen
import com.example.ui.purchases.PurchasesViewModel
import com.example.ui.reports.ReportsScreen
import com.example.ui.reports.ReportsViewModel
import com.example.ui.security.UsersAuditScreen
import com.example.ui.security.UsersAuditViewModel
import com.example.ui.settings.BusinessSettingsScreen
import com.example.ui.settings.BusinessSettingsViewModel
import com.example.ui.suppliers.SuppliersScreen
import com.example.ui.suppliers.SuppliersViewModel
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private data class DrawerMenuEntry(
  val label: String,
  val icon: ImageVector,
  val destination: AppDestination,
  val testTag: String,
  val requiredPermission: String? = null,
)

private val DRAWER_ENTRIES = listOf(
  DrawerMenuEntry("Compras y Entradas", Icons.Filled.ShoppingBag, AppDestination.Purchases, "drawer_purchases", AppPermissions.PURCHASES_MANAGE),
  DrawerMenuEntry("Proveedores", Icons.Filled.LocalShipping, AppDestination.Suppliers, "drawer_suppliers", AppPermissions.PURCHASES_MANAGE),
  DrawerMenuEntry("Clientes y Deudas", Icons.Filled.Groups, AppDestination.Customers, "drawer_customers", AppPermissions.POS_SELL),
  DrawerMenuEntry("Caja y Cierre Diario", Icons.AutoMirrored.Filled.ReceiptLong, AppDestination.CashRegister, "drawer_cash_register", AppPermissions.CASH_CLOSE),
  DrawerMenuEntry("Monedas y Tasas", Icons.Filled.CurrencyExchange, AppDestination.Currencies, "drawer_currencies", AppPermissions.SETTINGS_MANAGE),
  DrawerMenuEntry("Reportes y Ganancias", Icons.Filled.Assessment, AppDestination.Reports, "drawer_reports", AppPermissions.REPORTS_COST_PROFIT),
  DrawerMenuEntry("Usuarios y Auditoría", Icons.Filled.AdminPanelSettings, AppDestination.UsersAudit, "drawer_users_audit"),
  DrawerMenuEntry("Configuración", Icons.Filled.Settings, AppDestination.BusinessSettings, "drawer_settings", AppPermissions.SETTINGS_MANAGE),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(
  appState: MainAppState,
  activeBusinessIdFlow: StateFlow<Long>,
  container: AppContainer,
  onToggleTheme: () -> Unit,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val currentUserIdProvider: () -> Long? = { appState.activeSession.user?.id }
  val activeSession = appState.activeSession

  // Plan comercial (freemium) derivado de las preferencias guardadas.
  val planState = PlanRules.resolve(appState.preferences, System.currentTimeMillis())
  val isFreePlan = planState == PlanState.FREE

  fun navigateToTab(dest: AppDestination) {
    navController.navigate(dest) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = false
      }
      launchSingleTop = true
      restoreState = false
    }
  }

  fun navigateFromDrawer(dest: AppDestination) {
    scope.launch { drawerState.close() }
    navController.navigate(dest) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = false
      }
      launchSingleTop = true
    }
  }

  val screenTitle = when {
    currentDestination?.hasRoute<AppDestination.Home>() == true -> appState.workspace.business?.name ?: "Inicio"
    currentDestination?.hasRoute<AppDestination.History>() == true -> "Historial"
    currentDestination?.hasRoute<AppDestination.Pos>() == true -> "Tienda / POS"
    currentDestination?.hasRoute<AppDestination.Inventory>() == true -> "Inventario"
    currentDestination?.hasRoute<AppDestination.ProductDetail>() == true -> "Detalle de Producto"
    currentDestination?.hasRoute<AppDestination.Finance>() == true -> "Finanzas"
    currentDestination?.hasRoute<AppDestination.Purchases>() == true -> "Compras y Entradas"
    currentDestination?.hasRoute<AppDestination.Suppliers>() == true -> "Proveedores"
    currentDestination?.hasRoute<AppDestination.Customers>() == true -> "Clientes y Deudas"
    currentDestination?.hasRoute<AppDestination.CashRegister>() == true -> "Caja y Cierre Diario"
    currentDestination?.hasRoute<AppDestination.Currencies>() == true -> "Monedas y Tasas"
    currentDestination?.hasRoute<AppDestination.Reports>() == true -> "Reportes y Ganancias"
    currentDestination?.hasRoute<AppDestination.UsersAudit>() == true -> "Usuarios y Auditoría"
    currentDestination?.hasRoute<AppDestination.Backups>() == true -> "Respaldos"
    currentDestination?.hasRoute<AppDestination.BusinessSettings>() == true -> "Configuración"
    currentDestination?.hasRoute<AppDestination.License>() == true -> "Licencia de Stock+"
    else -> "Stock+"
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        ) {
          Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
              text = appState.workspace.business?.name ?: "Stock+",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "${appState.workspace.business?.primaryCurrency ?: "CUP"} • ${activeSession.user?.fullName ?: "Propietario"} (${activeSession.role?.name ?: "Propietario"})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          DRAWER_ENTRIES.filter { entry ->
            entry.requiredPermission == null || activeSession.hasPermission(entry.requiredPermission)
          }.forEach { entry ->
            NavigationDrawerItem(
              label = { Text(entry.label) },
              selected = when (entry.destination) {
                AppDestination.Purchases -> currentDestination?.hasRoute<AppDestination.Purchases>() == true
                AppDestination.Suppliers -> currentDestination?.hasRoute<AppDestination.Suppliers>() == true
                AppDestination.Customers -> currentDestination?.hasRoute<AppDestination.Customers>() == true
                AppDestination.CashRegister -> currentDestination?.hasRoute<AppDestination.CashRegister>() == true
                AppDestination.Currencies -> currentDestination?.hasRoute<AppDestination.Currencies>() == true
                AppDestination.Reports -> currentDestination?.hasRoute<AppDestination.Reports>() == true
                AppDestination.UsersAudit -> currentDestination?.hasRoute<AppDestination.UsersAudit>() == true
                AppDestination.Backups -> currentDestination?.hasRoute<AppDestination.Backups>() == true
                AppDestination.BusinessSettings -> currentDestination?.hasRoute<AppDestination.BusinessSettings>() == true
                else -> false
              },
              onClick = { navigateFromDrawer(entry.destination) },
              icon = { Icon(entry.icon, contentDescription = null) },
              modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .testTag(entry.testTag),
            )
          }
          Spacer(Modifier.height(12.dp))
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
          Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
              text = "Soporte: PedritoTech.cu",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "appscuba@gmail.com • @pedritotech • +5353274074",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = "© Pedro Martínez Figueredo",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isWideLayout = maxWidth >= 600.dp

      Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
          TopAppBar(
            title = { Text(screenTitle, maxLines = 1) },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            navigationIcon = {
              IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier.testTag("top_bar_menu_button"),
              ) {
                Icon(Icons.Filled.Menu, contentDescription = "Abrir menú lateral")
              }
            },
            actions = {
              val isDark = appState.preferences.themeMode == ThemeMode.DARK
              IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.testTag("top_bar_theme_toggle"),
              ) {
                Icon(
                  imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                  contentDescription = if (isDark) "Cambiar a tema claro" else "Cambiar a tema oscuro",
                )
              }
            },
          )
        },
        bottomBar = {
          if (!isWideLayout) {
            NavigationBar(
              windowInsets = WindowInsets.navigationBars,
              containerColor = MaterialTheme.colorScheme.surface,
            ) {
              BottomNavTab.entries.forEach { tab ->
                val selected = isTabSelected(tab, currentDestination)
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTab(tab.route) },
                  icon = {
                    Icon(
                      imageVector = iconForTab(tab, selected),
                      contentDescription = tab.label,
                    )
                  },
                  label = {
                    Text(
                      text = tab.label,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  alwaysShowLabel = true,
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
          if (isWideLayout) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
            ) {
              Spacer(Modifier.height(8.dp))
              BottomNavTab.entries.forEach { tab ->
                val selected = isTabSelected(tab, currentDestination)
                NavigationRailItem(
                  selected = selected,
                  onClick = { navigateToTab(tab.route) },
                  icon = {
                    Icon(
                      imageVector = iconForTab(tab, selected),
                      contentDescription = tab.label,
                    )
                  },
                  label = {
                    Text(
                      text = tab.label,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  alwaysShowLabel = true,
                  modifier = Modifier.testTag(tab.testTag),
                )
              }
            }
          }

          NavHost(
            navController = navController,
            startDestination = AppDestination.Home,
            modifier = Modifier.weight(1f).fillMaxSize(),
          ) {
            composable<AppDestination.Home> {
              HomeScreen(
                workspace = appState.workspace,
                onOpenSettings = { navigateFromDrawer(AppDestination.BusinessSettings) },
                onOpenMenu = { scope.launch { drawerState.open() } },
              )
            }
            composable<AppDestination.History> {
              val historyVm: HistoryViewModel = viewModel(
                factory = HistoryViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  saleRepository = container.saleRepository,
                  businessRepository = container.businessRepository,
                ),
              )
              HistoryScreen(
                viewModel = historyVm,
                freeVersionFooter = isFreePlan,
              )
            }
            composable<AppDestination.Pos> {
              val posVm: PosViewModel = viewModel(
                factory = PosViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  saleRepository = container.saleRepository,
                  inventoryRepository = container.inventoryRepository,
                  businessRepository = container.businessRepository,
                  preferences = container.preferences,
                ),
              )
              PosScreen(viewModel = posVm)
            }
            composable<AppDestination.Inventory> {
              val inventoryVm: InventoryViewModel = viewModel(
                factory = InventoryViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  inventoryRepository = container.inventoryRepository,
                  preferences = container.preferences,
                ),
              )
              InventoryScreen(
                viewModel = inventoryVm,
                onOpenProductDetail = { productId ->
                  navController.navigate(AppDestination.ProductDetail(productId))
                },
              )
            }
            composable<AppDestination.ProductDetail> { backStackEntry ->
              val route = backStackEntry.toRoute<AppDestination.ProductDetail>()
              val inventoryVm: InventoryViewModel = viewModel(
                factory = InventoryViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  inventoryRepository = container.inventoryRepository,
                  preferences = container.preferences,
                ),
              )
              val invState by inventoryVm.uiState.collectAsStateWithLifecycle()
              val detailVm: ProductDetailViewModel = viewModel(
                key = "product_detail_${route.productId}",
                factory = ProductDetailViewModel.Factory(
                  productId = route.productId,
                  inventoryRepository = container.inventoryRepository,
                  businessRepository = container.businessRepository,
                ),
              )
              ProductDetailScreen(
                viewModel = detailVm,
                categories = invState.categories,
                onNavigateBack = { navController.popBackStack() },
              )
            }
            composable<AppDestination.Finance> {
              if (!activeSession.hasPermission(AppPermissions.FINANCE_VIEW)) {
                ModuleRoadmapScreen(
                  title = "Acceso Restringido",
                  phaseLabel = "Rol: ${activeSession.role?.name ?: "Dependiente"}",
                  description = "El módulo financiero completo requiere perfil Propietario. Puedes operar ventas, caja y cobros.",
                  icon = Icons.Filled.AccountBalanceWallet,
                  onNavigateHome = { navigateToTab(AppDestination.Pos) },
                  onNavigateSettings = { navigateFromDrawer(AppDestination.CashRegister) },
                  primaryButtonLabel = "Ir al POS",
                  secondaryButtonLabel = "Ir a Caja",
                )
              } else {
                val financeVm: FinanceViewModel = viewModel(
                  factory = FinanceViewModel.Factory(
                    businessIdFlow = activeBusinessIdFlow,
                    financeRepository = container.financeRepository,
                    currentUserIdProvider = currentUserIdProvider,
                  ),
                )
                FinanceScreen(
                  viewModel = financeVm,
                  onOpenCashRegister = { navigateFromDrawer(AppDestination.CashRegister) },
                  onOpenCustomers = { navigateFromDrawer(AppDestination.Customers) },
                )
              }
            }
            composable<AppDestination.Purchases> {
              val purchasesVm: PurchasesViewModel = viewModel(
                factory = PurchasesViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  purchaseRepository = container.purchaseRepository,
                  inventoryRepository = container.inventoryRepository,
                ),
              )
              PurchasesScreen(viewModel = purchasesVm)
            }
            composable<AppDestination.Suppliers> {
              val suppliersVm: SuppliersViewModel = viewModel(
                factory = SuppliersViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  purchaseRepository = container.purchaseRepository,
                  businessRepository = container.businessRepository,
                ),
              )
              SuppliersScreen(viewModel = suppliersVm)
            }
            composable<AppDestination.Customers> {
              val customersVm: CustomersViewModel = viewModel(
                factory = CustomersViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  financeRepository = container.financeRepository,
                  businessRepository = container.businessRepository,
                  preferences = container.preferences,
                  currentUserIdProvider = currentUserIdProvider,
                ),
              )
              CustomersScreen(viewModel = customersVm)
            }
            composable<AppDestination.CashRegister> {
              val cashVm: CashRegisterViewModel = viewModel(
                factory = CashRegisterViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  financeRepository = container.financeRepository,
                  businessRepository = container.businessRepository,
                  currentUserIdProvider = currentUserIdProvider,
                ),
              )
              CashRegisterScreen(viewModel = cashVm)
            }
            composable<AppDestination.Currencies> {
              if (isFreePlan) {
                PlanLockedContent(
                  featureName = "Monedas y Tasas",
                  onOpenLicense = { navigateFromDrawer(AppDestination.License) },
                )
              } else {
                val currenciesVm: CurrenciesViewModel = viewModel(
                  factory = CurrenciesViewModel.Factory(
                    businessIdFlow = activeBusinessIdFlow,
                    businessRepository = container.businessRepository,
                    currentUserIdProvider = currentUserIdProvider,
                  ),
                )
                CurrenciesScreen(viewModel = currenciesVm)
              }
            }
            composable<AppDestination.Reports> {
              if (!activeSession.hasPermission(AppPermissions.REPORTS_COST_PROFIT)) {
                ModuleRoadmapScreen(
                  title = "Reportes Restringidos",
                  phaseLabel = "Rol: ${activeSession.role?.name ?: "Dependiente"}",
                  description = "Los reportes de costos y ganancias netas están reservados para el perfil Propietario.",
                  icon = Icons.Filled.Assessment,
                  onNavigateHome = { navigateToTab(AppDestination.Home) },
                  onNavigateSettings = { navigateFromDrawer(AppDestination.UsersAudit) },
                )
              } else if (isFreePlan) {
                PlanLockedContent(
                  featureName = "Reportes y Ganancias",
                  onOpenLicense = { navigateFromDrawer(AppDestination.License) },
                )
              } else {
                val reportsVm: ReportsViewModel = viewModel(
                  factory = ReportsViewModel.Factory(
                    businessIdFlow = activeBusinessIdFlow,
                    reportsRepository = container.reportsRepository,
                    businessRepository = container.businessRepository,
                  ),
                )
                ReportsScreen(viewModel = reportsVm)
              }
            }
            composable<AppDestination.UsersAudit> {
              val usersVm: UsersAuditViewModel = viewModel(
                factory = UsersAuditViewModel.Factory(
                  businessIdFlow = activeBusinessIdFlow,
                  securityRepository = container.securityRepository,
                ),
              )
              UsersAuditScreen(viewModel = usersVm)
            }
            composable<AppDestination.Backups> {
              if (isFreePlan) {
                PlanLockedContent(
                  featureName = "Respaldos",
                  onOpenLicense = { navigateFromDrawer(AppDestination.License) },
                )
              } else {
                val backupsVm: BackupsViewModel = viewModel(
                  factory = BackupsViewModel.Factory(
                    businessIdFlow = activeBusinessIdFlow,
                    backupRepository = container.backupRepository,
                    businessRepository = container.businessRepository,
                    preferences = container.preferences,
                    currentUserIdProvider = currentUserIdProvider,
                  ),
                )
                BackupsScreen(viewModel = backupsVm)
              }
            }
            composable<AppDestination.BusinessSettings> {
              val settingsVm: BusinessSettingsViewModel = viewModel(
                factory = BusinessSettingsViewModel.Factory(
                  businessRepository = container.businessRepository,
                  preferences = container.preferences,
                ),
              )
              BusinessSettingsScreen(
                viewModel = settingsVm,
                onNavigateToBackups = { navController.navigate(AppDestination.Backups) },
                onOpenLicense = { navigateFromDrawer(AppDestination.License) },
              )
            }
            composable<AppDestination.License> {
              val licenseVm: LicenseViewModel = viewModel(
                factory = LicenseViewModel.Factory(
                  context = LocalContext.current.applicationContext,
                  preferences = container.preferences,
                ),
              )
              LicenseScreen(viewModel = licenseVm)
            }
          }
        }
      }
    }
  }
}

private fun isTabSelected(
  tab: BottomNavTab,
  currentDestination: androidx.navigation.NavDestination?,
): Boolean = when (tab) {
  BottomNavTab.HOME -> currentDestination?.hasRoute<AppDestination.Home>() == true
  BottomNavTab.HISTORY -> currentDestination?.hasRoute<AppDestination.History>() == true
  BottomNavTab.POS -> currentDestination?.hasRoute<AppDestination.Pos>() == true
  BottomNavTab.INVENTORY -> currentDestination?.hasRoute<AppDestination.Inventory>() == true
  BottomNavTab.FINANCE -> currentDestination?.hasRoute<AppDestination.Finance>() == true
}

private fun iconForTab(tab: BottomNavTab, selected: Boolean): ImageVector = when (tab) {
  BottomNavTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
  BottomNavTab.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
  BottomNavTab.POS -> if (selected) Icons.Filled.PointOfSale else Icons.Outlined.PointOfSale
  BottomNavTab.INVENTORY -> if (selected) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2
  BottomNavTab.FINANCE -> if (selected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
}
