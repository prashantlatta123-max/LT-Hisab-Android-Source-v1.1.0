package com.sarotech.lthisab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun roundTripsVersion2Backup() {
        val rows = listOf(
            CashTransaction(id = 1, type = "IN", amountPaise = 500_00, note = "Salary", timestamp = 1_700_000_000_000L),
            CashTransaction(id = 2, type = "OUT", amountPaise = 120_50, note = "Groceries", timestamp = 1_700_000_060_000L)
        )
        val json = BackupManager.serialize(
            transactions = rows,
            openingBalancePaise = 10_000L,
            userName = "Prashant",
            userAddress = "Kherwara, Udaipur"
        )
        val result = BackupManager.parseAndValidate(json)
        assertTrue(result is BackupParseResult.Success)
        val payload = (result as BackupParseResult.Success).payload
        assertEquals(BackupManager.CURRENT_SCHEMA_VERSION, payload.schemaVersion)
        assertEquals(10_000L, payload.openingBalancePaise)
        assertEquals("Prashant", payload.userName)
        assertEquals(2, payload.transactions.size)
        assertEquals("Groceries", payload.transactions[1].note)
    }

    @Test
    fun acceptsLegacyVersion1Backup() {
        val v1 = """
            {
              "app": "LT Hisab",
              "version": 1,
              "transactions": [
                {"id": 9, "type": "IN", "amountPaise": 2500, "note": "Old entry", "timestamp": 1700000000000}
              ]
            }
        """.trimIndent()
        val res = BackupManager.parseAndValidate(v1)
        assertTrue(res is BackupParseResult.Success)
        val payload = (res as BackupParseResult.Success).payload
        assertEquals(1, payload.schemaVersion)
        assertEquals(1, payload.transactions.size)
    }

    @Test
    fun rejectsMalformedOrInvalidBackups() {
        assertTrue(BackupManager.parseAndValidate("") is BackupParseResult.Error)
        assertTrue(BackupManager.parseAndValidate("{bad json") is BackupParseResult.Error)
        assertTrue(
            BackupManager.parseAndValidate("""{"app":"OtherApp","version":1,"transactions":[]}""")
                is BackupParseResult.Error
        )
        assertTrue(
            BackupManager.parseAndValidate(
                """{"app":"LT Hisab","version":2,"transactions":[{"type":"BAD","amountPaise":100,"note":"x","timestamp":1000}]}"""
            ) is BackupParseResult.Error
        )
        assertTrue(
            BackupManager.parseAndValidate(
                """{"app":"LT Hisab","version":2,"transactions":[{"type":"IN","amountPaise":0,"note":"x","timestamp":1000}]}"""
            ) is BackupParseResult.Error
        )
    }
}
