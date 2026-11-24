package com.sarotech.lthisab.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val openingBalancePaise: Long?,
    val userName: String?,
    val userAddress: String?,
    val transactions: List<CashTransaction>
)

sealed class BackupParseResult {
    data class Success(val payload: BackupPayload) : BackupParseResult()
    data class Error(val reason: String) : BackupParseResult()
}

object BackupManager {
    const val CURRENT_SCHEMA_VERSION = 2
    private const val APP_IDENTIFIER = "LT Hisab"

    fun serialize(
        transactions: List<CashTransaction>,
        openingBalancePaise: Long,
        userName: String,
        userAddress: String,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val array = JSONArray()
        transactions.sortedWith(compareBy<CashTransaction> { it.timestamp }.thenBy { it.id })
            .forEach { item ->
                array.put(
                    JSONObject()
                        .put("type", item.type)
                        .put("amountPaise", item.amountPaise)
                        .put("note", item.note)
                        .put("timestamp", item.timestamp)
                )
            }
        return JSONObject()
            .put("app", APP_IDENTIFIER)
            .put("brand", "SAROTECH")
            .put("schemaVersion", CURRENT_SCHEMA_VERSION)
            .put("version", CURRENT_SCHEMA_VERSION)
            .put("exportedAt", exportedAt)
            .put("openingBalancePaise", openingBalancePaise)
            .put("userName", userName)
            .put("userAddress", userAddress)
            .put("transactionCount", transactions.size)
            .put("transactions", array)
            .toString(2)
    }

    fun parseAndValidate(rawJson: String): BackupParseResult {
        if (rawJson.isBlank()) return BackupParseResult.Error("Backup file is empty")
        return try {
            val root = JSONObject(rawJson)
            val app = root.optString("app", "")
            if (app != APP_IDENTIFIER) {
                return BackupParseResult.Error("Not a valid LT Hisab backup file")
            }

            val version = when {
                root.has("schemaVersion") -> root.optInt("schemaVersion", -1)
                root.has("version") -> root.optInt("version", -1)
                else -> -1
            }
            if (version < 1 || version > CURRENT_SCHEMA_VERSION) {
                return BackupParseResult.Error("Unsupported backup version: $version")
            }

            if (!root.has("transactions")) {
                return BackupParseResult.Error("Missing transactions array")
            }

            val array = root.getJSONArray("transactions")
            val parsed = mutableListOf<CashTransaction>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                    ?: return BackupParseResult.Error("Corrupt entry at index $i")
                val type = obj.optString("type", "")
                if (type != "IN" && type != "OUT") {
                    return BackupParseResult.Error("Invalid transaction type '$type' at entry #${i + 1}")
                }
                if (!obj.has("amountPaise")) {
                    return BackupParseResult.Error("Missing amount at entry #${i + 1}")
                }
                val amountPaise = obj.getLong("amountPaise")
                if (amountPaise <= 0L) {
                    return BackupParseResult.Error("Invalid amount at entry #${i + 1}")
                }
                val note = obj.optString("note", "").trim()
                if (note.isEmpty()) {
                    return BackupParseResult.Error("Empty note at entry #${i + 1}")
                }
                val timestamp = obj.optLong("timestamp", -1L)
                if (timestamp <= 0L) {
                    return BackupParseResult.Error("Invalid timestamp at entry #${i + 1}")
                }
                parsed.add(
                    CashTransaction(
                        id = 0,
                        type = type,
                        amountPaise = amountPaise,
                        note = note,
                        timestamp = timestamp
                    )
                )
            }

            val openingBal = if (root.has("openingBalancePaise") && !root.isNull("openingBalancePaise")) {
                root.getLong("openingBalancePaise")
            } else null

            val userName = if (root.has("userName") && !root.isNull("userName")) {
                root.optString("userName", "").trim()
            } else null

            val userAddress = if (root.has("userAddress") && !root.isNull("userAddress")) {
                root.optString("userAddress", "").trim()
            } else null

            BackupParseResult.Success(
                BackupPayload(
                    schemaVersion = version,
                    exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
                    openingBalancePaise = openingBal,
                    userName = userName,
                    userAddress = userAddress,
                    transactions = parsed
                )
            )
        } catch (e: Exception) {
            BackupParseResult.Error("Invalid JSON format: ${e.message ?: "parse error"}")
        }
    }
}
