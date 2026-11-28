package com.example.core.rates

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Tasas de referencia del mercado informal de divisas en Cuba, publicadas por
 * elTOQUE en https://eltoque.com/tasas-de-cambio-cuba
 * (sección «Mercado Informal de Divisas en Cuba (Tiempo Real)»).
 *
 * IMPORTANTE: elTOQUE no publica una API pública (verificado el 2026-11-28: la
 * página es un sitio Astro con la tabla renderizada en el HTML; no hay
 * endpoint JSON documentado ni clave pública). Por eso [ElToqueRatesClient]
 * descarga la página y [ElToqueRatesParser] extrae las tasas del HTML.
 * Si elTOQUE cambia el diseño de la página, el parseo fallará de forma
 * explícita ([ElToqueParseException]) y la app conservará las tasas
 * manuales anteriores: nunca se aplica una tasa inventada ni parcial.
 *
 * Las tasas de elTOQUE son valores de REFERENCIA (mediana de ofertas de
 * compra/venta del mercado informal), no la tasa oficial. La UI debe
 * mostrar siempre la fuente («elTOQUE») y la fecha de actualización.
 */
data class ElToqueRates(
  val usdToCup: BigDecimal,
  val eurToCup: BigDecimal,
  val mlcToCup: BigDecimal?,
  val fetchedAtEpochMillis: Long,
  val sourceUrl: String = ElToqueRatesClient.SOURCE_URL,
) {
  /**
   * Convierte las tasas (expresadas en CUP por unidad) a tasas contra la
   * moneda base del negocio (`rateToBase`, como las guarda
   * [com.example.data.local.db.entity.ExchangeRateEntity]).
   *
   * Devuelve `null` si la moneda base no es CUP, USD, EUR ni MLC, porque
   * entonces no hay forma de derivar la tasa sin otra referencia.
   * Solo incluye las monedas que elTOQUE publicó en esta lectura.
   */
  fun toRatesToBase(primaryCurrency: String): Map<String, BigDecimal>? {
    val cupPerUnit = mapOf(
      "USD" to usdToCup,
      "EUR" to eurToCup,
    ) + (mlcToCup?.let { mapOf("MLC" to it) } ?: emptyMap())
    return when (primaryCurrency.uppercase()) {
      "CUP" -> cupPerUnit
      "USD", "EUR", "MLC" -> {
        val cupPerBase = cupPerUnit[primaryCurrency.uppercase()] ?: return null
        cupPerUnit.mapValues { (_, cup) ->
          cup.divide(cupPerBase, 4, java.math.RoundingMode.HALF_UP)
        }
      }
      else -> null
    }
  }
}

class ElToqueParseException(message: String) : Exception(message)

class ElToqueNetworkException(message: String, cause: Throwable? = null) :
  IOException(message, cause)

/**
 * Parser defensivo de la tabla informal de elTOQUE.
 *
 * Estrategia: se eliminan las etiquetas HTML, y para cada código se toma
 * la PRIMERA ocurrencia de «CÓDIGO … número CUP» en el documento. En la
 * página actual la tabla informal aparece antes que la tabla del mercado
 * formal, así que la primera ocurrencia es la informal. Si elTOQUE
 * reordena la página, el parseo puede devolver la tasa formal: por eso
 * cada valor se valida contra un rango plausible (10–10.000 CUP) y el
 * resultado se muestra siempre con su fuente y su fecha en la UI.
 */
object ElToqueRatesParser {
  private val SUPPORTED_CODES = listOf("USD", "EUR", "MLC")

  // Número con decimales, aceptando «980.00», «1,050.00» y «980,00».
  private val NUMBER = """(\d[\d.,]*)"""

  fun parse(html: String, fetchedAtEpochMillis: Long = System.currentTimeMillis()): ElToqueRates {
    val text = html
      .replace(Regex("<[^>]+>"), " ")
      .replace("&nbsp;", " ")
      .replace(Regex("\\s+"), " ")
    val values = mutableMapOf<String, BigDecimal>()
    val missing = mutableListOf<String>()
    for (code in SUPPORTED_CODES) {
      val match = Regex(
        """\b$code\b.{0,120}?$NUMBER\s*CUP""",
        RegexOption.IGNORE_CASE,
      ).find(text)
      val parsed = match?.groupValues?.getOrNull(1)?.let { parseNumberOrNull(it) }
      if (parsed == null || parsed < BigDecimal("10") || parsed > BigDecimal("10000")) {
        if (code != "MLC") missing += code
      } else {
        values[code] = parsed
      }
    }
    if (missing.isNotEmpty()) {
      throw ElToqueParseException(
        "No se pudieron leer las tasas de elTOQUE para: ${missing.joinToString(", ")}. " +
          "Es posible que elTOQUE haya cambiado el diseño de su página.",
      )
    }
    return ElToqueRates(
      usdToCup = values.getValue("USD"),
      eurToCup = values.getValue("EUR"),
      mlcToCup = values["MLC"],
      fetchedAtEpochMillis = fetchedAtEpochMillis,
    )
  }

  /** Normaliza «1,050.00» / «980,00» / «980.00» a BigDecimal, o null. */
  fun parseNumberOrNull(raw: String): BigDecimal? {
    val cleaned = raw.trim().replace(" ", "")
    if (cleaned.isEmpty()) return null
    val normalized = when {
      cleaned.contains('.') && cleaned.contains(',') -> cleaned.replace(",", "")
      cleaned.contains(',') -> {
        // «980,00» = coma decimal; «1,050» = separador de miles.
        val afterComma = cleaned.substringAfterLast(',')
        if (afterComma.length == 2) cleaned.replace(",", ".") else cleaned.replace(",", "")
      }
      else -> cleaned
    }
    return try {
      BigDecimal(normalized)
    } catch (_: NumberFormatException) {
      null
    }
  }
}

/**
 * Cliente HTTP mínimo de elTOQUE, sin Retrofit/OkHttp: la app es
 * offline-first y esta es su única llamada de red, así que no compensa
 * añadir una pila de red completa. Usa [HttpURLConnection] con timeouts
 * cortos (10 s) y hasta 3 intentos con espera creciente.
 *
 * El parámetro [fetcher] existe para poder probar el cliente sin red:
 * en producción se usa la descarga real de [SOURCE_URL].
 */
class ElToqueRatesClient(
  private val fetcher: suspend (String) -> String = ::defaultFetch,
  private val maxAttempts: Int = 3,
) {
  companion object {
    const val SOURCE_URL = "https://eltoque.com/tasas-de-cambio-cuba"
    const val SOURCE_NAME = "elTOQUE"
  }

  suspend fun fetchRates(): ElToqueRates {
    var lastError: IOException? = null
    repeat(maxAttempts) { attempt ->
      try {
        val html = fetcher(SOURCE_URL)
        return ElToqueRatesParser.parse(html)
      } catch (e: ElToqueParseException) {
        // El HTML llegó pero no se entiende: reintentar no ayuda.
        throw e
      } catch (e: IOException) {
        lastError = e
        if (attempt < maxAttempts - 1) delay(1_000L * (attempt + 1))
      }
    }
    throw ElToqueNetworkException(
      "No se pudo contactar a elTOQUE tras $maxAttempts intentos. Revisa la conexión a internet.",
      lastError,
    )
  }
}

private suspend fun defaultFetch(url: String): String = withContext(Dispatchers.IO) {
  val connection = URL(url).openConnection() as HttpURLConnection
  try {
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("Accept", "text/html")
    connection.setRequestProperty("Accept-Language", "es-CU,es;q=0.9")
    // Identificarse como la app, no como un navegador genérico.
    connection.setRequestProperty("User-Agent", "Stock+/1.0 (Android; tasas elTOQUE)")
    connection.connect()
    val code = connection.responseCode
    if (code != HttpURLConnection.HTTP_OK) {
      throw IOException("elTOQUE respondió HTTP $code")
    }
    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
  } finally {
    connection.disconnect()
  }
}
