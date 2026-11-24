package com.sarotech.lthisab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BalanceCalculatorTest {

    @Test
    fun calculatesCashInOutAndOpeningBalanceInPaise() {
        val rows = listOf(
            CashTransaction(id = 1, type = "IN", amountPaise = 100_000, note = "Opening receipt", timestamp = 1),
            CashTransaction(id = 2, type = "OUT", amountPaise = 25_050, note = "Expense", timestamp = 2),
            CashTransaction(id = 3, type = "IN", amountPaise = 10_000, note = "Refund", timestamp = 3)
        )
        assertEquals(
            mapOf(1L to 150_000L, 2L to 124_950L, 3L to 134_950L),
            BalanceCalculator.balanceAfter(rows, 50_000)
        )
        assertEquals(110_000L to 25_050L, BalanceCalculator.totals(rows))
    }

    @Test
    fun sameTimestampUsesStableIdOrdering() {
        val rows = listOf(
            CashTransaction(id = 2, type = "OUT", amountPaise = 100, note = "b", timestamp = 10),
            CashTransaction(id = 1, type = "IN", amountPaise = 500, note = "a", timestamp = 10)
        )
        assertEquals(mapOf(1L to 500L, 2L to 400L), BalanceCalculator.balanceAfter(rows))
    }

    @Test
    fun parsesExactDecimalStringsWithoutFloatingPointLoss() {
        assertEquals(113L, BalanceCalculator.parseAmountToPaise("1.13"))
        assertEquals(1999L, BalanceCalculator.parseAmountToPaise("19.99"))
        assertEquals(125050L, BalanceCalculator.parseAmountToPaise("1,250.5"))
        assertNull(BalanceCalculator.parseAmountToPaise("0"))
        assertNull(BalanceCalculator.parseAmountToPaise("-50", allowNegative = false))
        assertEquals(-5000L, BalanceCalculator.parseAmountToPaise("-50", allowNegative = true))
        assertEquals(0L, BalanceCalculator.parseAmountToPaise("0", allowNegative = true))
    }

    @Test
    fun carriesForwardOpeningBalanceForPeriod() {
        val rows = listOf(
            CashTransaction(id = 1, type = "IN", amountPaise = 50_000, note = "Before period", timestamp = 1000),
            CashTransaction(id = 2, type = "OUT", amountPaise = 10_000, note = "Before period 2", timestamp = 2000),
            CashTransaction(id = 3, type = "IN", amountPaise = 25_000, note = "Inside period", timestamp = 5000)
        )
        assertEquals(
            60_000L,
            BalanceCalculator.openingBalanceForPeriod(rows, baseOpeningBalancePaise = 20_000L, periodStartMillis = 3000L)
        )
    }

    @Test
    fun formatsIndianAndInternationalMoney() {
        assertEquals("₹1,00,000.00", BalanceCalculator.formatMoney(10_000_000L, "₹", useIndianFormat = true))
        assertEquals("₹100,000.00", BalanceCalculator.formatMoney(10_000_000L, "₹", useIndianFormat = false))
        assertEquals("-₹250.50", BalanceCalculator.formatMoney(-25_050L, "₹", useIndianFormat = true))
    }

    @Test
    fun weeklyWindowStartsAtMidnightOfFirstDayOfWeek() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 15, 45, 30)
            set(Calendar.MILLISECOND, 500)
        }
        val window = BalanceCalculator.computeWindow(
            period = PeriodFilter.WEEKLY,
            nowMillis = cal.timeInMillis,
            firstDayOfWeek = Calendar.MONDAY
        )
        assertNotNull(window.startMillis)
        assertNotNull(window.endExclusiveMillis)
        val startCal = Calendar.getInstance().apply { timeInMillis = window.startMillis!! }
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.SECOND))
        assertTrue(cal.timeInMillis >= window.startMillis!! && cal.timeInMillis < window.endExclusiveMillis!!)
    }
}
