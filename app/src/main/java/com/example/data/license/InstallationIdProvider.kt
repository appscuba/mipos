package com.example.data.license

import com.example.data.preferences.AppPreferencesDataStore

/**
 * ID ÚNICO de instalación de esta app para LicenseHub.
 *
 * Adaptación del proveedor del kit de LicenseHub al almacén que la
 * app YA usa: el ID vive en el DataStore `gestor_comercial_prefs`,
 * clave `license_device_id`, con el formato STP-XXXX-XXXX-XXXX que
 * la app genera desde la versión 1.1. Este proveedor LEE primero el
 * ID existente y solo se genera uno nuevo si no hay ninguno, así el
 * dispositivo conserva su ID actual al cambiar de servidor de
 * licencias (antes pedritotech.cu, ahora LicenseHub).
 *
 * Reglas de LicenseHub:
 * - NUNCA es el teléfono, el IMEI ni otro identificador de hardware:
 *   es un valor aleatorio propio de la instalación.
 * - Si la app se reinstala y el ID cambia, el administrador puede
 *   desvincular el anterior desde LicenseHub y autorizar el nuevo.
 */
class InstallationIdProvider(
    private val preferences: AppPreferencesDataStore
) {
    /** Devuelve el ID existente o crea uno nuevo y lo persiste. */
    suspend fun getOrCreate(): String = preferences.ensureLicenseDeviceId()
}
