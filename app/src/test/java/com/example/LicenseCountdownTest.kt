package com.example

import com.example.data.license.LicenseCountdown
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas de la cuenta regresiva de la licencia (validación única):
 * el restante corre con el reloj monotónico y los cambios de hora
 * del sistema no pueden alargar la licencia.
 */
class LicenseCountdownTest {

  private val hour = 60L * 60 * 1000
  private val day = 24 * hour

  // Ancla: quedan 30 días; monotónico en 10 h; pared en T0.
  private val anchorRemaining = 30 * day
  private val anchorElapsed = 10 * hour
  private val anchorWall = 1_700_000_000_000L

  @Test
  fun `el tiempo transcurrido descuenta del restante`() {
    val remaining = LicenseCountdown.remainingMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = anchorElapsed + 5 * day,
      nowWallMs = anchorWall + 5 * day,
    )
    assertEquals(25 * day, remaining)
  }

  @Test
  fun `atrasar la hora del sistema no regala tiempo`() {
    // Pasa 1 día real (monotónico) pero el reloj de pared se atrasó 10 días.
    val remaining = LicenseCountdown.remainingMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = anchorElapsed + day,
      nowWallMs = anchorWall - 10 * day,
    )
    assertEquals(29 * day, remaining)
  }

  @Test
  fun `adelantar la hora del sistema no quita tiempo`() {
    val remaining = LicenseCountdown.remainingMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = anchorElapsed + day,
      nowWallMs = anchorWall + 365 * day,
    )
    assertEquals(29 * day, remaining)
  }

  @Test
  fun `tras un reinicio descuenta el tiempo apagado`() {
    // El monotónico volvió a cero (reinicio) y pasaron 3 días de pared.
    val remaining = LicenseCountdown.remainingMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = 2 * hour,
      nowWallMs = anchorWall + 3 * day,
    )
    assertEquals(27 * day, remaining)
  }

  @Test
  fun `tras un reinicio con la hora atrasada no suma tiempo`() {
    val remaining = LicenseCountdown.remainingMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = 2 * hour,
      nowWallMs = anchorWall - 10 * day,
    )
    assertEquals(anchorRemaining, remaining)
  }

  @Test
  fun `el restante nunca baja de cero`() {
    val remaining = LicenseCountdown.remainingMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = anchorElapsed + 90 * day,
      nowWallMs = anchorWall + 90 * day,
    )
    assertEquals(0L, remaining)
  }

  @Test
  fun `sin ancla el restante es cero`() {
    val remaining = LicenseCountdown.remainingMs(
      LicenseCountdown.NO_ANCHOR, anchorElapsed, anchorWall,
      nowElapsedMs = anchorElapsed,
      nowWallMs = anchorWall,
    )
    assertEquals(0L, remaining)
  }

  @Test
  fun `la fecha estimada es ahora mas el restante`() {
    val expiry = LicenseCountdown.estimatedExpiryWallMs(
      anchorRemaining, anchorElapsed, anchorWall,
      nowElapsedMs = anchorElapsed + 5 * day,
      nowWallMs = anchorWall + 5 * day,
    )
    assertEquals(anchorWall + 30 * day, expiry)
  }
}
