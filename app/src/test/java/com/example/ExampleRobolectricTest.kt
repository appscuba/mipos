package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
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
import androidx.compose.ui.test.performTextReplacement
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
    assertEquals("Stock+", appName)
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

    // Abrir la Caja Diaria: sin sesión de caja abierta el POS rechaza la venta
    // y el comprobante nunca aparece (misma regla que en producción).
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
    composeTestRule.onNodeWithTag("pos_confirm_sale_button").performClick()

    // 4. Verificar comprobante generado (V-00001) y cerrarlo
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("receipt_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("receipt_title").assertTextEquals("Comprobante V-00001")
    composeTestRule.onNodeWithTag("receipt_close_button").performClick()

    // 5. Ir a Historial y verificar la venta con ganancia bruta 75.00 CUP
    composeTestRule.onNodeWithTag("nav_tab_history").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("V-00001").fetchSemanticsNodes().isNotEmpty()
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
      composeTestRule.onAllNodesWithText("150.00 CUP").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("finance_list").performScrollToNode(hasText("Electricidad"))
    composeTestRule.onNodeWithText("Electricidad").assertIsDisplayed()

    // 3. Verificar en Reportes y Usuarios/Auditoría
    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_reports").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_reports").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("reports_list").fetchSemanticsNodes().isNotEmpty()
    }
    // La tarjeta de ganancia neta puede tardar en entrar en la lista
    // (los datos de Reportes cargan asíncronos): se reintenta el
    // desplazamiento hasta que aparezca, en vez de fallar a la primera.
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      try {
        composeTestRule.onNodeWithTag("reports_list").performScrollToNode(hasTestTag("report_net_profit"))
        true
      } catch (e: AssertionError) {
        false
      }
    }
    composeTestRule.onNodeWithTag("report_net_profit").assertIsDisplayed()

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

  @Test
  fun `cliente permite crear deuda agrandarla y quitarla desde su detalle`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("setup_business_name_input").performScrollTo().performTextInput("Bodega Sol")
    composeTestRule.onNodeWithTag("setup_submit_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_customers").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_customers").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customers_add_fab").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customers_add_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_name_input").performTextInput("Mario Díaz")
    composeTestRule.onNodeWithTag("customer_save_button").performClick()

    // Espera a que el formulario se cierre y el cliente aparezca en la lista.
    // Buscar solo el texto también encontraba el contenido del campo de edición,
    // provocando un clic sobre el formulario en lugar de abrir el detalle.
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_1").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_card_1").performClick()

    // Crear nueva deuda de 300
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_add_debt_btn").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_add_debt_btn").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("debt_adjust_amount_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("debt_adjust_amount_input").performTextInput("300")
    composeTestRule.onNodeWithTag("debt_adjust_confirm_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_debt_increase_btn_1").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_balance").assertTextEquals("300.00 CUP")

    // Agrandar la deuda en +200 -> 500.00 CUP
    composeTestRule.onNodeWithTag("customer_debt_increase_btn_1").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("debt_adjust_amount_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("debt_adjust_amount_input").performTextInput("200")
    composeTestRule.onNodeWithTag("debt_adjust_confirm_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("500.00 CUP").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_balance").assertTextEquals("500.00 CUP")

    // Quitar toda la deuda -> 0.00 CUP
    composeTestRule.onNodeWithTag("customer_debt_remove_btn_1").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("debt_remove_full_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("debt_remove_full_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_debt_remove_btn_1").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_balance").assertTextEquals("0.00 CUP")
  }

  @Test
  fun `pos simplificado permite elegir cliente rapido anadir con un toque y vender al fiado`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("setup_business_name_input").performScrollTo().performTextInput("Tienda Rápida")
    composeTestRule.onNodeWithTag("setup_submit_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name").fetchSemanticsNodes().isNotEmpty()
    }

    // Abrir la Caja Diaria: sin sesión de caja abierta el POS rechaza la venta
    // y el comprobante nunca aparece (misma regla que en producción).
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

    // Crear producto por unidad
    composeTestRule.onNodeWithTag("nav_tab_inventory").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("inventory_add_product_fab").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("inventory_add_product_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("product_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("product_modality_UNIT").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("product_name_input").performTextInput("Refresco")
    composeTestRule.onNodeWithTag("product_sale_price_input").performScrollTo().performTextInput("150")
    composeTestRule.onNodeWithTag("product_cost_input").performScrollTo().performTextInput("100")
    composeTestRule.onNodeWithTag("product_initial_stock_input").performScrollTo().performTextInput("12")
    composeTestRule.onNodeWithTag("product_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("1 productos").fetchSemanticsNodes().isNotEmpty()
    }

    // Ir al POS -> crear cliente rápido directo en la barra superior -> añadir con 1 toque -> vender fiado
    composeTestRule.onNodeWithTag("nav_tab_pos").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_bar_quick_customer_btn").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_bar_quick_customer_btn").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("quick_customer_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("quick_customer_name_input").performTextInput("Ana López")
    composeTestRule.onNodeWithTag("quick_customer_save_button").performClick()

    // Esperar a que el diálogo de cliente rápido se cierre del todo antes de
    // seguir: el árbol del POS existe bajo el diálogo, así que buscar el botón
    // «+1» no sincroniza nada; si el toque cae sobre el diálogo aún visible,
    // el producto nunca entra al carrito y el checkout sale vacío.
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("quick_customer_name_input").fetchSemanticsNodes().isEmpty()
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_quick_add_1").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_quick_add_1").performClick()

    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag("pos_open_cart_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_open_cart_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag("pos_pay_mode_CREDIT").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_pay_mode_CREDIT").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("pos_confirm_sale_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("receipt_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("receipt_title").assertTextEquals("Comprobante V-00001")
  }

  @Test
  fun `categorias permite editar y eliminar y POS calcula vuelto con 100 en efectivo y transferencia`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("setup_business_name_input").performScrollTo().performTextInput("Mini Market")
    composeTestRule.onNodeWithTag("setup_submit_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("home_business_name").fetchSemanticsNodes().isNotEmpty()
    }

    // Abrir la Caja Diaria: sin sesión de caja abierta el POS rechaza la venta
    // y el comprobante nunca aparece (misma regla que en producción).
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

    // 1. Inventario -> Categorías -> Editar y Eliminar
    composeTestRule.onNodeWithTag("nav_tab_inventory").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("inventory_categories_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("inventory_categories_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("category_edit_btn_1").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("category_edit_btn_1").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("category_name_input").performScrollTo().performTextReplacement("Comestibles")
    composeTestRule.onNodeWithTag("category_add_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Comestibles").fetchSemanticsNodes().isNotEmpty()
    }

    // Eliminar categoría id 1
    composeTestRule.onNodeWithTag("category_delete_btn_1").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("category_delete_confirm_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("category_delete_confirm_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("category_edit_btn_1").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithText("Cerrar").performClick()

    // 2. Crear producto de 80 CUP para probar pago con 100
    composeTestRule.onNodeWithTag("inventory_add_product_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("product_name_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("product_modality_UNIT").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("product_name_input").performTextInput("Jugo")
    composeTestRule.onNodeWithTag("product_sale_price_input").performScrollTo().performTextInput("80")
    composeTestRule.onNodeWithTag("product_cost_input").performScrollTo().performTextInput("50")
    composeTestRule.onNodeWithTag("product_initial_stock_input").performScrollTo().performTextInput("10")
    composeTestRule.onNodeWithTag("product_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("1 productos").fetchSemanticsNodes().isNotEmpty()
    }

    // 3. POS -> añadir Jugo (80 CUP) -> probar vuelto con 100 en Efectivo y en Transferencia
    composeTestRule.onNodeWithTag("nav_tab_pos").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_quick_add_1").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_quick_add_1").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_open_cart_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_open_cart_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_tendered_chip_100").fetchSemanticsNodes().isNotEmpty()
    }
    // En Efectivo con 100 -> devuelve 20.00 CUP
    composeTestRule.onNodeWithTag("pos_tendered_chip_100").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pos_change_to_return_text").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pos_change_to_return_text").assertTextEquals("20.00 CUP")

    // En Transferencia con 100 -> también calcula devolver 20.00 CUP
    composeTestRule.onNodeWithTag("pos_pay_mode_TRANSFER").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("pos_tendered_chip_100").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("pos_change_to_return_text").assertTextEquals("20.00 CUP")

    composeTestRule.onNodeWithTag("pos_confirm_sale_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("receipt_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("receipt_close_button").performClick()

    // 4. Ajustes -> Restablecer sistema en cero con advertencia
    composeTestRule.onNodeWithTag("top_bar_menu_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("drawer_settings").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("drawer_settings").performScrollTo().performClick()
    // Verificar soporte y derechos de autor en Ajustes
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("settings_reset_system_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("support_website_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("support_email_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("support_telegram_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("support_whatsapp_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("support_copyright_text").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_reset_system_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("settings_reset_confirm_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("settings_reset_confirm_button").performClick()

    // Debe regresar limpio a Setup inicial
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("setup_business_name_input").fetchSemanticsNodes().isNotEmpty()
    }
  }
}
