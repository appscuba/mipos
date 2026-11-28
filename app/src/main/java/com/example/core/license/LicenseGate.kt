package com.example.core.license

/**
 * Puerta de licencia para las funciones de pago de Stock+.
 *
 * Hoy la única función bajo licencia es la actualización de tasas desde
 * elTOQUE (manual y automática cada hora). Sin licencia activa, la app
 * sigue funcionando exactamente igual que antes, con las tasas manuales:
 * la puerta solo bloquea la novedad, nunca el modo offline actual.
 *
 * CÓMO CONECTAR LicenseHub (cuando se fusione el PR #1):
 * 1. Implementar esta interfaz delegando en el verificador de licencias
 *    de LicenseHub (por ejemplo, una clase `LicenseHubGate` que consulte
 *    si la licencia del negocio está activa y no vencida).
 * 2. Sustituir [DefaultLicenseGate] por esa implementación en
 *    `AppContainer` (GestorComercialApp.kt). Nada más cambia: ni el
 *    repositorio de tasas, ni el Worker, ni la UI consultan licencias
 *    directamente, solo esta interfaz.
 * 3. No se inventa aquí ningún servidor de licencias, clave ni
 *    validación local: la implementación por defecto devuelve `false`
 *    hasta que LicenseHub exista de verdad.
 */
enum class LicensedFeature {
  RATES_AUTO_UPDATE,
}

interface LicenseGate {
  suspend fun isLicensed(feature: LicensedFeature): Boolean

  suspend fun isRatesAutoUpdateLicensed(): Boolean =
    isLicensed(LicensedFeature.RATES_AUTO_UPDATE)
}

/** Implementación por defecto: sin licencia activa. */
class DefaultLicenseGate : LicenseGate {
  override suspend fun isLicensed(feature: LicensedFeature): Boolean = false
}
