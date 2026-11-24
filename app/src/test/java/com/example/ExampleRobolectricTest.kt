package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  private val resetDbRule = object : ExternalResource() {
    override fun before() {
      AppDatabase.resetForTests()
    }

    override fun after() {
      AppDatabase.resetForTests()
    }
  }

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(resetDbRule).around(composeTestRule)

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Gestor Comercial", appName)
  }

  @Test
  fun `onboarding configura negocio navega al shell y crea producto por peso en inventario`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("setup_business_name_input")
      .performScrollTo()
      .performTextInput("Bodega La Estrella")

    composeTestRule.onNodeWithTag("setup_submit_button")
      .performScrollTo()
      .performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("home_business_name")
      .assertIsDisplayed()
      .assertTextEquals("Bodega La Estrella")

    composeTestRule.onNodeWithTag("nav_tab_inventory").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("inventory_add_product_fab")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("inventory_add_product_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("product_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("product_name_input").performTextInput("Arroz Blanco")
    composeTestRule.onNodeWithTag("product_sale_price_input").performScrollTo().performTextInput("550")
    composeTestRule.onNodeWithTag("product_cost_input").performScrollTo().performTextInput("400")
    composeTestRule.onNodeWithTag("product_initial_stock_input").performScrollTo().performTextInput("10")
    composeTestRule.onNodeWithTag("product_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("1 productos")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("inventory_list").performScrollToNode(hasText("Arroz Blanco"))
    composeTestRule.onNodeWithText("Arroz Blanco").assertIsDisplayed()
    composeTestRule.onNodeWithText("5000 g (10 lb)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Arroz Blanco").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("detail_product_name")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("detail_product_name").assertTextEquals("Arroz Blanco")
    composeTestRule.onNodeWithTag("detail_kardex_list")
      .performScrollToNode(hasText("Kárdex de Movimientos (1)"))
    composeTestRule.onNodeWithText("Kárdex de Movimientos (1)").assertIsDisplayed()
  }

  @Test
  fun `fase 3 crea proveedor registra compra con transporte y actualiza inventario`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("setup_business_name_input")
      .performScrollTo()
      .performTextInput("Mercado El Sol")
    composeTestRule.onNodeWithTag("setup_submit_button")
      .performScrollTo()
      .performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    // 1. Crear producto base en Inventario
    composeTestRule.onNodeWithTag("nav_tab_inventory").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("inventory_add_product_fab")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("inventory_add_product_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("product_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("product_name_input").performTextInput("Frijol Negro")
    composeTestRule.onNodeWithTag("product_sale_price_input").performScrollTo().performTextInput("550")
    composeTestRule.onNodeWithTag("product_cost_input").performScrollTo().performTextInput("0")
    composeTestRule.onNodeWithTag("product_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("1 productos").fetchSemanticsNodes().isNotEmpty()
    }

    // 2. Ir al menú lateral -> Proveedores y crear proveedor
    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_suppliers").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_suppliers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("suppliers_add_fab").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("suppliers_add_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("supplier_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("supplier_name_input").performTextInput("Granos del Sur")
    composeTestRule.onNodeWithTag("supplier_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Granos del Sur").fetchSemanticsNodes().isNotEmpty()
    }

    // 3. Ir a Compras y Entradas -> Registrar compra de 10 lb a 400 CUP
    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_purchases").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_purchases").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("purchases_add_fab").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("purchases_add_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("purchase_line_qty_input").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("purchase_invoice_input").performScrollTo().performTextInput("F-101")
    composeTestRule.onNodeWithTag("purchase_line_qty_input").performScrollTo().performTextInput("10")
    composeTestRule.onNodeWithTag("purchase_line_price_input").performScrollTo().performTextInput("400")
    composeTestRule.onNodeWithTag("purchase_add_line_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("purchase_confirm_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("1 confirmadas").fetchSemanticsNodes().isNotEmpty()
    }

    // 4. Verificar que el stock del producto se actualizó a 5000 g (10 lb) en Inventario
    composeTestRule.onNodeWithTag("nav_tab_inventory").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("inventory_list").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("inventory_list").performScrollToNode(hasText("5000 g (10 lb)"))
    composeTestRule.onNodeWithText("5000 g (10 lb)").assertIsDisplayed()
  }

  @Test
  fun `fase 4 venta por peso en POS genera comprobante y aparece en historial`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithTag("setup_business_name_input")
      .performScrollTo()
      .performTextInput("Bodega Central")
    composeTestRule.onNodeWithTag("setup_submit_button")
      .performScrollTo()
      .performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    // 1. Crear producto Arroz (10 lb = 5000 g, precio 550, costo 400)
    composeTestRule.onNodeWithTag("nav_tab_inventory").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("inventory_add_product_fab")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("inventory_add_product_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("product_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("product_name_input").performTextInput("Arroz")
    composeTestRule.onNodeWithTag("product_sale_price_input").performScrollTo().performTextInput("550")
    composeTestRule.onNodeWithTag("product_cost_input").performScrollTo().performTextInput("400")
    composeTestRule.onNodeWithTag("product_initial_stock_input").performScrollTo().performTextInput("10")
    composeTestRule.onNodeWithTag("product_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("1 productos").fetchSemanticsNodes().isNotEmpty()
    }

    // 2. Ir al POS -> seleccionar Arroz -> ½ lb (0.5 lb = 275.00 CUP)
    composeTestRule.onNodeWithTag("nav_tab_pos").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Arroz").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Arroz").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_fraction_0.5").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_fraction_0.5").performClick()
    composeTestRule.onNodeWithTag("pos_add_to_cart_button").performClick()

    // 3. Cobrar desde barra inferior (o panel en pantalla ancha) y confirmar venta
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_open_cart_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_open_cart_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_confirm_sale_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_confirm_sale_button").performScrollTo().performClick()

    // 4. Verificar comprobante generado (V-00001) y cerrarlo
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("receipt_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("receipt_title").assertTextEquals("Comprobante V-00001")
    composeTestRule.onNodeWithTag("receipt_close_button").performClick()

    // 5. Ir a Historial y verificar la venta con ganancia bruta 75.00 CUP
    composeTestRule.onNodeWithTag("nav_tab_history").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("history_list").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("history_list").performScrollToNode(hasText("V-00001"))
    composeTestRule.onNodeWithText("V-00001").assertIsDisplayed()
    composeTestRule.onNodeWithText("Ganancia: 75.00 CUP").assertIsDisplayed()
  }

  @Test
  fun `fases 5 a 8 abre caja registra gasto verifica esperado navega a reportes y usuarios`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithTag("setup_business_name_input")
      .performScrollTo()
      .performTextInput("Cafetería Norte")
    composeTestRule.onNodeWithTag("setup_submit_button")
      .performScrollTo()
      .performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    // 1. Abrir Caja Diaria con 1000 CUP desde menú lateral
    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_cash_register").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_cash_register").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("cash_opening_amount_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("cash_opening_amount_input").performTextInput("1000")
    composeTestRule.onNodeWithTag("cash_open_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("cash_expected_amount").fetchSemanticsNodes().isNotEmpty()
    }

    // 2. Ir a Finanzas y registrar gasto operativo de 150 CUP desde caja
    composeTestRule.onNodeWithTag("nav_tab_finance").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("finance_add_expense_fab").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("finance_add_expense_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("expense_description_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("expense_description_input").performTextInput("Electricidad")
    composeTestRule.onNodeWithTag("expense_amount_input").performTextInput("150")
    composeTestRule.onNodeWithTag("expense_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Electricidad").fetchSemanticsNodes().isNotEmpty()
    }

    // 3. Verificar en Reportes y Usuarios/Auditoría
    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_reports").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_reports").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("report_net_profit").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_users_audit").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_users_audit").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("security_active_user_name").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("security_active_user_name").assertTextEquals("Propietario")
  }
}
