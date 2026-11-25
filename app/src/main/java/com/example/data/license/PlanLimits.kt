package com.example.data.license

import com.example.data.preferences.UserPreferences

/**
 * Estado comercial de la instalación (modo freemium):
 * - LICENSED: hay una licencia usable → acceso completo, sin límites.
 * - TRIAL: primeros 15 días desde el primer arranque → acceso completo.
 * - FREE: sin licencia y con la prueba vencida → acceso con los
 *   límites de [PlanLimits].
 */
enum class PlanState {
  LICENSED,
  TRIAL,
  FREE,
}

/**
 * Constantes del modo freemium, en un solo sitio para poder ajustar
 * la oferta comercial sin tocar pantallas ni ViewModels.
 */
object PlanLimits {
  /** Días de prueba completa desde el primer arranque. */
  const val TRIAL_DAYS = 15

  /** Máximo de productos en el plan gratuito. */
  const val MAX_FREE_PRODUCTS = 30

  /** Máximo de ventas por mes natural en el plan gratuito. */
  const val MAX_FREE_SALES_PER_MONTH = 100

  /** Máximo de clientes en el plan gratuito. */
  const val MAX_FREE_CUSTOMERS = 10

  /** Días de historial visibles en el plan gratuito (referencia). */
  const val FREE_HISTORY_DAYS = 30

  /** Porcentaje de uso a partir del cual se avisa (sin bloquear). */
  const val WARN_PERCENT = 80

  /** Umbral de aviso suave para un límite dado (80 % por defecto). */
  fun warnThreshold(max: Int): Int = max * WARN_PERCENT / 100
}

/**
 * Deriva el [PlanState] a partir de las preferencias guardadas.
 * Compartido por MainActivity, MainAppShell y los ViewModels.
 */
object PlanRules {
  const val TRIAL_MS = PlanLimits.TRIAL_DAYS * 24L * 60 * 60 * 1000
  private const val DAY_MS = 24L * 60 * 60 * 1000

  fun resolve(prefs: UserPreferences, nowMs: Long): PlanState = when {
    LicenseRules.isUsable(prefs, nowMs) -> PlanState.LICENSED
    // Sin marca de primer arranque todavía (se escribe al abrir):
    // se trata como prueba recién empezada, nunca como FREE.
    prefs.firstLaunchAt <= 0L -> PlanState.TRIAL
    nowMs - prefs.firstLaunchAt < TRIAL_MS -> PlanState.TRIAL
    else -> PlanState.FREE
  }

  /** Días completos que quedan de prueba (0 si ya no está en TRIAL). */
  fun trialDaysRemaining(prefs: UserPreferences, nowMs: Long): Int {
    if (prefs.firstLaunchAt <= 0L) return PlanLimits.TRIAL_DAYS
    val remainingMs = TRIAL_MS - (nowMs - prefs.firstLaunchAt)
    if (remainingMs <= 0L) return 0
    // Redondeo hacia arriba: el día en curso cuenta como día restante.
    return ((remainingMs + DAY_MS - 1) / DAY_MS).toInt()
  }

  /** True solo en FREE: es el único estado con límites y marca. */
  fun isLimited(plan: PlanState): Boolean = plan == PlanState.FREE
}
