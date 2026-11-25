package com.example.data.license

/**
 * Cuenta regresiva local de la licencia (modelo de validación única).
 *
 * La app valida UNA vez contra LicenseHub. De esa respuesta FIRMADA
 * salen dos datos del servidor: su hora actual (`server_time`) y el
 * vencimiento de la licencia (`expires_at`). Con ambos se calcula el
 * tiempo que le queda a la licencia en el momento de validar:
 *
 *     restante = expires_at − server_time
 *
 * A partir de ahí el tiempo corre EN EL TELÉFONO sin depender de la
 * hora del sistema:
 * - El descuento usa el reloj MONOTÓNICO del equipo
 *   (`SystemClock.elapsedRealtime()`, que cuenta también el sueño
 *   profundo y NO se mueve cuando el usuario cambia la hora).
 * - Atrasar o adelantar el reloj del sistema no cambia el restante.
 * - Si el equipo se reinicia, el reloj monotónico vuelve a cero: se
 *   detecta porque el valor actual es MENOR que el del ancla, y solo
 *   entonces se descuenta la diferencia del reloj de pared (el tiempo
 *   apagado), nunca negativa.
 * - El restante guardado nunca crece: solo baja, o se recalcula
 *   cuando el servidor vuelve a validar (renovaciones).
 *
 * El ancla (restante + marcas de ambos relojes) se persiste en las
 * preferencias y se renueva en cada arranque: el tiempo transcurrido
 * queda «doblado» dentro del ancla nueva.
 *
 * Funciones puras: reciben los relojes como parámetros para poder
 * probarse sin Android.
 */
object LicenseCountdown {

  /** Valor de «sin ancla» en `licenseAnchorRemainingMs`. */
  const val NO_ANCHOR: Long = -1L

  /**
   * Milisegundos que le quedan a la licencia según el ancla y los
   * relojes actuales. Nunca negativo.
   */
  fun remainingMs(
    anchorRemainingMs: Long,
    anchorElapsedMs: Long,
    anchorWallMs: Long,
    nowElapsedMs: Long,
    nowWallMs: Long,
  ): Long {
    if (anchorRemainingMs <= 0L) return 0L
    val delta = if (nowElapsedMs >= anchorElapsedMs) {
      // Mismo arranque: manda el reloj monotónico; la hora del
      // sistema puede moverse sin efecto alguno.
      nowElapsedMs - anchorElapsedMs
    } else {
      // El monotónico volvió a cero: el equipo se reinició. Se
      // descuenta el tiempo de pared transcurrido, nunca negativo
      // (un reloj atrasado durante el apagado no regala tiempo).
      maxOf(0L, nowWallMs - anchorWallMs)
    }
    return maxOf(0L, anchorRemainingMs - delta)
  }

  /**
   * Fecha de pared estimada del vencimiento, solo para mostrarla:
   * «ahora» según el reloj de pared más el restante real.
   */
  fun estimatedExpiryWallMs(
    anchorRemainingMs: Long,
    anchorElapsedMs: Long,
    anchorWallMs: Long,
    nowElapsedMs: Long,
    nowWallMs: Long,
  ): Long = nowWallMs + remainingMs(
    anchorRemainingMs, anchorElapsedMs, anchorWallMs, nowElapsedMs, nowWallMs,
  )
}
