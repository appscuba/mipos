package com.example.data.rates

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.GestorComercialApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Actualización automática de tasas cada 1 hora.
 *
 * El Worker no hace nada si no hay negocio activo o si la licencia no está
 * activa (la puerta de licencia se comprueba dentro de
 * [RateSyncRepository] antes de cualquier llamada de red). Con
 * [Constraints] de red conectada, WorkManager no despierta el trabajo sin
 * internet y no gasta batería ni datos de más; el backoff exponencial
 * evita reintentos agresivos si elTOQUE no responde.
 */
class RatesSyncWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as GestorComercialApp
    val prefs = app.container.preferences.preferencesFlow.first()
    val businessId = prefs.activeBusinessId
    if (businessId <= 0L) return Result.success()
    val userId = prefs.activeUserId.takeIf { it > 0L }
    return when (
      val result = app.container.rateSyncRepository.sync(
        businessId = businessId,
        userId = userId,
        source = RateSyncSource.AUTOMATIC,
      )
    ) {
      is RateSyncResult.Success,
      RateSyncResult.LicenseRequired,
      RateSyncResult.UnsupportedBaseCurrency,
      is RateSyncResult.Failed,
      -> Result.success()
      RateSyncResult.NoNetwork -> Result.retry()
    }
  }
}

class RatesSyncScheduler(private val context: Context) {
  companion object {
    const val UNIQUE_WORK_NAME = "rates-eltoque-hourly-sync"
  }

  /** Programa la sincronización horaria; es idempotente (KEEP). */
  fun schedule() {
    val request = PeriodicWorkRequestBuilder<RatesSyncWorker>(1, TimeUnit.HOURS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .setBackoffPolicy(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  fun cancel() {
    WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
  }
}
