package com.example.data.rates

import com.example.core.license.DefaultLicenseGate
import com.example.core.license.LicenseGate
import com.example.core.rates.ElToqueNetworkException
import com.example.core.rates.ElToqueParseException
import com.example.core.rates.ElToqueRatesClient
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class RateSyncSource {
  MANUAL,
  AUTOMATIC,
}

sealed interface RateSyncResult {
  data class Success(
    val updatedCodes: List<String>,
    val skippedCodes: List<String>,
    val fetchedAtEpochMillis: Long,
    val source: RateSyncSource,
  ) : RateSyncResult

  /** La función requiere licencia activa; no se hizo ninguna llamada de red. */
  data object LicenseRequired : RateSyncResult

  /** Sin conexión (o elTOQUE no responde): se conservan las tasas manuales. */
  data object NoNetwork : RateSyncResult

  /** La moneda base no es CUP, USD, EUR ni MLC: no se puede derivar la tasa. */
  data object UnsupportedBaseCurrency : RateSyncResult

  /** Falló la descarga o el parseo; las tasas manuales quedan intactas. */
  data class Failed(val reason: String) : RateSyncResult
}

/**
 * Sincroniza las tasas USD/EUR (y MLC si elTOQUE la publica) desde elTOQUE
 * y las aplica con [BusinessRepository.saveCustomExchangeRate], que ya
 * protege el historial (las operaciones pasadas conservan su tasa) y
 * escribe la auditoría.
 *
 * Reglas de seguridad de los datos:
 * - Solo se actualizan monedas que YA EXISTEN en el negocio y están
 *   ACTIVAS. Si el usuario desactivó el EUR, la sincronización no lo
 *   reactiva; si el negocio no tiene MLC, no se crea.
 * - Si la descarga o el parseo fallan, no se toca ninguna tasa.
 * - Sin licencia activa no se hace ninguna llamada de red.
 */
class RateSyncRepository(
  private val businessRepository: BusinessRepository,
  private val preferences: AppPreferencesDataStore,
  private val client: ElToqueRatesClient = ElToqueRatesClient(),
  private val licenseGate: LicenseGate = DefaultLicenseGate(),
) {
  suspend fun sync(
    businessId: Long,
    userId: Long?,
    source: RateSyncSource,
  ): RateSyncResult {
    if (businessId <= 0L) return RateSyncResult.Failed("Negocio no válido.")
    if (!licenseGate.isRatesAutoUpdateLicensed()) {
      return RateSyncResult.LicenseRequired
    }
    val primaryCurrency = businessRepository.getBusinessPrimaryCurrency(businessId)
      ?: return RateSyncResult.Failed("Negocio no encontrado.")
    val fetched = try {
      client.fetchRates()
    } catch (e: UnknownHostException) {
      return RateSyncResult.NoNetwork
    } catch (e: SocketTimeoutException) {
      return RateSyncResult.NoNetwork
    } catch (e: ElToqueNetworkException) {
      return RateSyncResult.NoNetwork
    } catch (e: ElToqueParseException) {
      preferences.recordRatesSync(
        atEpochMillis = System.currentTimeMillis(),
        source = source.name,
        codes = emptyList(),
        error = e.message,
      )
      return RateSyncResult.Failed(e.message ?: "No se pudieron leer las tasas de elTOQUE.")
    } catch (e: IOException) {
      return RateSyncResult.NoNetwork
    } catch (e: Exception) {
      return RateSyncResult.Failed(e.message ?: "Error inesperado al actualizar las tasas.")
    }
    val ratesToBase = fetched.toRatesToBase(primaryCurrency)
      ?: return RateSyncResult.UnsupportedBaseCurrency

    val updated = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    for ((code, rate) in ratesToBase) {
      val existing = businessRepository.getExchangeRate(businessId, code)
      if (existing == null || !existing.isActive) {
        skipped += code
        continue
      }
      businessRepository.saveCustomExchangeRate(
        businessId = businessId,
        currencyCode = code,
        currencyName = existing.currencyName,
        symbol = existing.symbol,
        rateToBase = rate,
        userId = userId,
      )
      updated += code
    }
    preferences.recordRatesSync(
      atEpochMillis = fetched.fetchedAtEpochMillis,
      source = source.name,
      codes = updated,
      error = null,
    )
    return RateSyncResult.Success(
      updatedCodes = updated,
      skippedCodes = skipped,
      fetchedAtEpochMillis = fetched.fetchedAtEpochMillis,
      source = source,
    )
  }
}
