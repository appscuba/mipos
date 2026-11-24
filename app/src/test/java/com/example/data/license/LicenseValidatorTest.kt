package com.example.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicenseValidatorTest {
  @Test
  fun activeResponseConvertsUnixSecondsToMilliseconds() {
    val result = parseLicenseResponse(
      """{"status":"active","expira":"2027-01-01","expira_ts":1800000000}""",
      200,
    )

    assertEquals("active", result.status)
    assertEquals(1_800_000_000_000L, result.expiresAt)
  }

  @Test
  fun lifetimeLicenseKeepsZeroExpiration() {
    val result = parseLicenseResponse("""{"status":"active","expira_ts":0}""", 200)

    assertEquals("active", result.status)
    assertEquals(0L, result.expiresAt)
  }

  @Test
  fun invalidRevokedAndExpiredStatusesArePreserved() {
    listOf("invalid", "revoked", "expired").forEach { expected ->
      assertEquals(expected, parseLicenseResponse("""{"status":"$expected"}""", 200).status)
    }
  }

  @Test
  fun serverErrorIsUnavailable() {
    val result = parseLicenseResponse("""{"status":"active","expira_ts":1800000000}""", 503)

    assertEquals("unavailable", result.status)
    assertTrue(result.message.orEmpty().isNotBlank())
  }

  @Test
  fun errorHttpResponseCannotGrantActiveLicense() {
    val result = parseLicenseResponse("""{"status":"active"}""", 403)

    assertEquals("unavailable", result.status)
  }

  @Test
  fun malformedJsonIsUnavailable() {
    assertEquals("unavailable", parseLicenseResponse("not-json", 200).status)
  }
}
