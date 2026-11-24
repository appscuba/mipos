package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppDestination {
  @Serializable data object Setup : AppDestination
  @Serializable data object Home : AppDestination
  @Serializable data object History : AppDestination
  @Serializable data object Pos : AppDestination
  @Serializable data object Inventory : AppDestination
  @Serializable data class ProductDetail(val productId: Long) : AppDestination
  @Serializable data object Finance : AppDestination
  @Serializable data object Purchases : AppDestination
  @Serializable data object Suppliers : AppDestination
  @Serializable data object Customers : AppDestination
  @Serializable data object CashRegister : AppDestination
  @Serializable data object Currencies : AppDestination
  @Serializable data object Reports : AppDestination
  @Serializable data object UsersAudit : AppDestination
  @Serializable data object Backups : AppDestination
  @Serializable data object BusinessSettings : AppDestination
  @Serializable data object LicenseActivation : AppDestination
}

enum class BottomNavTab(
  val label: String,
  val route: AppDestination,
  val testTag: String,
) {
  HOME("Inicio", AppDestination.Home, "nav_tab_home"),
  HISTORY("Historial", AppDestination.History, "nav_tab_history"),
  POS("Tienda", AppDestination.Pos, "nav_tab_pos"),
  INVENTORY("Inventario", AppDestination.Inventory, "nav_tab_inventory"),
  FINANCE("Finanzas", AppDestination.Finance, "nav_tab_finance"),
}
