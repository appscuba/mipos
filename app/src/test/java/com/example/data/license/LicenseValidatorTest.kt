package com.example.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LicenseValidatorTest {
  @Test
  fun freeAccessModeUnlocksAllFeaturesWithoutAnActiveServerLicense() {
    val snapshot = LicenseSnapshot("STP-TEST-1234", "", "expired", 1L, 0L, 1L)

    assertTrue(PlanLimits.FREE_ACCESS_MODE)
    assertEquals(PlanStatus.LICENSED, snapshot.plan(now = Long.MAX_VALUE))
  }

  @Test
  fun requestSendsOnlyDeviceIdAndRegisteredPhone() {
    val payload = org.json.JSONObject(createLicenseValidationPayload(" STP-ABCD-1234-EF56 ", " +53 5327 4074 "))

    assertEquals(setOf("device_id", "phone"), payload.keys().asSequence().toSet())
    assertEquals("STP-ABCD-1234-EF56", payload.getString("device_id"))
    assertEquals("+53 5327 4074", payload.getString("phone"))
  }

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
