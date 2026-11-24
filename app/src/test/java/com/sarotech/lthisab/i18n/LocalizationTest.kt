package com.sarotech.lthisab.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {

    @Test
    fun allThreeLanguagesProvideValidBrandingAndStrings() {
        AppLanguage.entries.forEach { lang ->
            val s = StringsRepository.forLanguage(lang)
            assertEquals("SAROTECH", s.brandName)
            assertTrue(s.appName.isNotBlank())
            assertTrue(s.generatedBy.contains("LT Hisab"))
            assertTrue(s.cashIn.isNotBlank())
            assertTrue(s.cashOut.isNotBlank())
            assertTrue(s.balance.isNotBlank())
            assertTrue(s.pdfPageOf.contains("%d"))
            assertTrue(s.restoreConfirmBody.contains("%d"))
        }
    }
}
