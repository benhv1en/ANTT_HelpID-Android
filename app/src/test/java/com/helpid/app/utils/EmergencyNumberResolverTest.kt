package com.helpid.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyNumberResolverTest {
    @Test
    fun vietnameseLanguageUsesVietnamMedicalEmergencyNumber() {
        assertEquals("115", EmergencyNumberResolver.resolveFor("vi", "US"))
    }

    @Test
    fun knownCountryUsesMappedEmergencyNumber() {
        assertEquals("911", EmergencyNumberResolver.resolveFor("en", "US"))
    }

    @Test
    fun unknownCountryFallsBackTo112() {
        assertEquals("112", EmergencyNumberResolver.resolveFor("en", "ZZ"))
    }
}
