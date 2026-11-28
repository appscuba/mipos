# Tasas automáticas desde elTOQUE (con licencia)

## Qué hace

- **Manual:** botón «Actualizar ahora» en Monedas y Tasas. Descarga las tasas
  informales de elTOQUE y actualiza USD y EUR (y MLC si está publicada y
  existe en el negocio).
- **Automática:** cada 1 hora con WorkManager (`RatesSyncWorker`), solo con
  red conectada y solo con licencia activa. Sin licencia o sin internet, el
  Worker no hace nada.
- **Sin licencia:** el botón y el interruptor aparecen bloqueados con el
  motivo. Las tasas manuales siguen funcionando exactamente igual que antes.
- **Origen visible:** la pantalla muestra «Actualizado hace X · Fuente:
  elTOQUE (manual/automática)» y la última sincronización queda guardada en
  DataStore (`rates_last_sync_*`).

## Fuente

- URL: `https://eltoque.com/tasas-de-cambio-cuba` (sección «Mercado Informal
  de Divisas en Cuba (Tiempo Real)»).
- **elTOQUE no tiene API pública** (verificado 2026-11-28). La página es un
  sitio Astro con la tabla renderizada en el HTML, así que el cliente
  descarga la página con `HttpURLConnection` (timeout 10 s, 3 intentos) y el
  parser (`ElToqueRatesParser`) extrae la primera ocurrencia de cada código
  («USD … 980.00 CUP»). La tabla informal aparece antes que la del mercado
  formal en la página; si elTOQUE reordena la página, el parseo puede
  devolver la tasa formal: por eso cada valor se valida (10–10.000 CUP) y
  la UI siempre muestra fuente y fecha.
- Las tasas son de REFERENCIA (mediana de ofertas del mercado informal),
  no la tasa oficial. No se aplica ninguna tasa si la descarga o el parseo
  fallan, y nunca se aplica una tasa parcial inventada.
- Valores observados el 2026-11-28: USD 980.00 CUP, EUR 1.040–1.050 CUP,
  MLC 768–805 CUP (varían durante el día).

## Licencia

- `core/license/LicenseGate.kt`: interfaz + `DefaultLicenseGate` (sin
  licencia). Para conectar LicenseHub: implementar la interfaz delegando en
  su verificador y sustituirla en `AppContainer`. Ver el comentario en el
  archivo. No hay servidor de licencias ni claves en este PR.

## Archivos

- Nuevos: `core/rates/ElToqueRates.kt`, `core/license/LicenseGate.kt`,
  `data/rates/RateSyncRepository.kt`, `data/rates/RatesSyncWork.kt`,
  `Phase9ElToqueRatesTest.kt`, este documento.
- Editados: `AppPreferencesDataStore.kt` (metadatos de sync),
  `BusinessRepository.kt` (`getBusinessPrimaryCurrency`, `getExchangeRate`),
  `GestorComercialApp.kt` (contenedor + programación horaria),
  `CurrenciesViewModel.kt` / `CurrenciesScreen.kt` (tarjeta elTOQUE),
  `MainAppShell.kt` (cableado), `AndroidManifest.xml` (INTERNET y
  ACCESS_NETWORK_STATE, solo para esta función),
  `gradle/libs.versions.toml` + `app/build.gradle.kts` (WorkManager 2.10.0).
- No se tocó nada de seguridad (BackupCrypto, PinHasher, codemagic.yaml,
  backup_rules): eso vive en la rama `fix/security-gaps`.

## Cómo probar

1. **Sin licencia (estado actual):** abrir Monedas y Tasas. El botón y el
   interruptor deben aparecer bloqueados con «Requiere licencia activa».
   Las tasas manuales siguen editables.
2. **Con licencia (cuando LicenseHub esté conectado):** pulsar «Actualizar
   ahora» con internet: USD/EUR deben actualizarse a los valores de
   elTOQUE y aparecer «Actualizado hace … · Fuente: elTOQUE (manual)».
3. **Sin internet:** pulsar «Actualizar ahora»: mensaje «Sin conexión» y
   las tasas quedan intactas.
4. **Automática:** activar el interruptor, esperar ~1 h (o forzar el Worker
   desde App Inspection): la tasa debe actualizarse sola y el origen debe
   decir «automática».
5. **Moneda desactivada:** desactivar el EUR y sincronizar: el EUR no debe
   reactivarse ni actualizarse.
6. Tests: `./gradlew :app:testDebugUnitTest --tests "com.example.Phase9ElToqueRatesTest"`.

## Si elTOQUE cambia su página

- El parseo lanza `ElToqueParseException`, la UI muestra el error y las
  tasas manuales quedan intactas. Hay que actualizar
  `ElToqueRatesParser` (y su test con una muestra del HTML nuevo). Si
  elTOQUE publica una API oficial, conviene migrar el cliente a esa API y
  dejar el parser HTML solo como respaldo.
