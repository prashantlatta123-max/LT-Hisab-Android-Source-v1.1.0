package com.sarotech.lthisab.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

enum class PeriodFilter(val key: String) {
    ALL("ALL"),
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY"),
    CUSTOM("CUSTOM");

    companion object {
        fun fromKey(key: String?): PeriodFilter =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ALL
    }
}

enum class SortOption(val key: String) {
    NEWEST("NEWEST"),
    OLDEST("OLDEST"),
    HIGHEST("HIGHEST"),
    LOWEST("LOWEST")
}

data class PeriodWindow(
    val startMillis: Long?,
    val endExclusiveMillis: Long?
)

/** Uses integer paise throughout; never uses floating point for accounting totals. */
object BalanceCalculator {

    private const val MAX_AMOUNT_PAISE = 99_999_999_999_99L

    fun signedAmount(item: CashTransaction): Long = when (item.type) {
        "IN" -> item.amountPaise
        "OUT" -> -item.amountPaise
        else -> 0L
    }

    fun parseAmountToPaise(input: String, allowNegative: Boolean = false): Long? {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            val scaled = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
            val value = scaled.longValueExact()
            if (!allowNegative && value <= 0L) return null
            if (abs(value) > MAX_AMOUNT_PAISE) return null
            value
        } catch (_: Exception) {
            null
        }
    }

    fun balanceAfter(
        items: List<CashTransaction>,
        openingBalancePaise: Long = 0L
    ): Map<Long, Long> {
        var balance = openingBalancePaise
        val result = linkedMapOf<Long, Long>()
        items.sortedWith(compareBy<CashTransaction> { it.timestamp }.thenBy { it.id }).forEach {
            balance = Math.addExact(balance, signedAmount(it))
            result[it.id] = balance
        }
        return result
    }

    fun totals(items: List<CashTransaction>): Pair<Long, Long> =
        items.fold(0L to 0L) { acc, item ->
            when (item.type) {
                "IN" -> Math.addExact(acc.first, item.amountPaise) to acc.second
                "OUT" -> acc.first to Math.addExact(acc.second, item.amountPaise)
                else -> acc
            }
        }

    fun openingBalanceForPeriod(
        allItems: List<CashTransaction>,
        baseOpeningBalancePaise: Long,
        periodStartMillis: Long?
    ): Long {
        if (periodStartMillis == null) return baseOpeningBalancePaise
        var bal = baseOpeningBalancePaise
        allItems.forEach { item ->
            if (item.timestamp < periodStartMillis) {
                bal = Math.addExact(bal, signedAmount(item))
            }
        }
        return bal
    }

    fun computeWindow(
        period: PeriodFilter,
        nowMillis: Long = System.currentTimeMillis(),
        firstDayOfWeek: Int = Calendar.MONDAY,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): PeriodWindow {
        if (period == PeriodFilter.ALL) return PeriodWindow(null, null)

        if (period == PeriodFilter.CUSTOM) {
            val start = customStartMillis?.let { startOfDay(it) }
            val end = customEndMillis?.let { startOfNextDay(it) }
            return PeriodWindow(start, end)
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            this.firstDayOfWeek = firstDayOfWeek
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (period) {
            PeriodFilter.DAILY -> {
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                PeriodWindow(start, cal.timeInMillis)
            }
            PeriodFilter.WEEKLY -> {
                val currentDow = cal.get(Calendar.DAY_OF_WEEK)
                val diff = (currentDow - firstDayOfWeek + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, -diff)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 7)
                PeriodWindow(start, cal.timeInMillis)
            }
            PeriodFilter.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                PeriodWindow(start, cal.timeInMillis)
            }
            PeriodFilter.YEARLY -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                PeriodWindow(start, cal.timeInMillis)
            }
            else -> PeriodWindow(null, null)
        }
    }

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfNextDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun formatMoney(
        paise: Long,
        currencySymbol: String = "₹",
        useIndianFormat: Boolean = true
    ): String {
        val negative = paise < 0
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val cents = absPaise % 100L
        val intPart = if (useIndianFormat) formatIndianDigits(rupees) else String.format(Locale.US, "%,d", rupees)
        val fracPart = String.format(Locale.US, "%02d", cents)
        val prefix = if (negative) "-$currencySymbol" else currencySymbol
        return "$prefix$intPart.$fracPart"
    }

    private fun formatIndianDigits(number: Long): String {
        val s = number.toString()
        if (s.length <= 3) return s
        val lastThree = s.takeLast(3)
        val rest = s.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$grouped,$lastThree"
    }
}
