package com.helpid.app.utils

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import java.util.Locale

object EmergencyNumberResolver {
    private val numberByCountry = mapOf(
        "US" to "911",
        "CA" to "911",
        "VN" to "115",
        "IN" to "112",
        "GB" to "999",
        "IE" to "112",
        "AU" to "000",
        "NZ" to "111",
        "JP" to "119",
        "KR" to "119",
        "CN" to "120",
        "BR" to "190",
        "MX" to "911",
        "DE" to "112",
        "FR" to "112",
        "ES" to "112",
        "IT" to "112",
        "NL" to "112",
        "BE" to "112",
        "SE" to "112",
        "NO" to "112",
        "DK" to "112",
        "FI" to "112",
        "CH" to "112",
        "AT" to "112",
        "PL" to "112",
        "PT" to "112",
        "RO" to "112",
        "HU" to "112",
        "CZ" to "112",
        "GR" to "112",
        "TR" to "112",
        "ZA" to "112",
        "SG" to "995",
        "AE" to "999"
    )

    fun resolve(context: Context): String {
        val languageCode = LanguageManager.getSelectedLanguage(context).code
        val country = detectCountryIso(context)
        return resolveFor(languageCode, country)
    }

    fun resolveFor(languageCode: String, countryIso: String): String {
        if (languageCode == "vi") return "115"
        val country = countryIso.uppercase(Locale.US)
        return numberByCountry[country] ?: "112"
    }

    private fun detectCountryIso(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        val simCountry = telephonyManager?.simCountryIso?.uppercase(Locale.US).orEmpty()
        if (simCountry.length == 2) return simCountry

        val networkCountry = telephonyManager?.networkCountryIso?.uppercase(Locale.US).orEmpty()
        if (networkCountry.length == 2) return networkCountry

        val localeCountry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]?.country
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.country
        }?.uppercase(Locale.US).orEmpty()

        return if (localeCountry.length == 2) localeCountry else "US"
    }
}
