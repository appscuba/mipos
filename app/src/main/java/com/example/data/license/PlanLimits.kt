package com.example.data.license

object PlanLimits {
  /** Acceso temporal abierto: todas las funciones están disponibles sin validar una licencia. */
  const val FREE_ACCESS_MODE = true

  const val TRIAL_DAYS = 15
  const val LICENSE_GRACE_DAYS = 7
  const val REVALIDATE_AFTER_HOURS = 24
  const val MAX_PRODUCTS = 30
  const val MAX_MONTHLY_SALES = 100
  const val MAX_CUSTOMERS = 10
  const val PRODUCT_WARNING_AT = 24
  const val SALES_WARNING_AT = 80
  const val PRODUCT_SLUG = "stockplus"
  const val VALIDATION_URL = "https://pedritotech.cu/wp-json/ptls/v1/app-validate"
  const val WHATSAPP_NUMBER = "5353274074"
}

enum class PlanStatus { LICENSED, TRIAL, FREE }

data class LicenseSnapshot(
  val deviceId: String,
  val licensePhone: String,
  val serverStatus: String,
  val expiresAt: Long,
  val lastSuccessfulValidation: Long,
  val firstLaunchAt: Long,
) {
  fun isUsable(now: Long = System.currentTimeMillis()): Boolean =
    serverStatus == "active" &&
      (expiresAt == 0L || expiresAt > now) &&
      lastSuccessfulValidation > 0L &&
      now - lastSuccessfulValidation <= PlanLimits.LICENSE_GRACE_DAYS * 24L * 60L * 60L * 1000L

  fun plan(now: Long = System.currentTimeMillis()): PlanStatus = when {
    PlanLimits.FREE_ACCESS_MODE -> PlanStatus.LICENSED
    isUsable(now) -> PlanStatus.LICENSED
    firstLaunchAt > 0L && now < firstLaunchAt + PlanLimits.TRIAL_DAYS * 24L * 60L * 60L * 1000L -> PlanStatus.TRIAL
    else -> PlanStatus.FREE
  }

  fun trialDaysRemaining(now: Long = System.currentTimeMillis()): Int =
    (((firstLaunchAt + PlanLimits.TRIAL_DAYS * 86_400_000L - now).coerceAtLeast(0L) + 86_399_999L) / 86_400_000L).toInt()
}
