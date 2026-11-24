package com.sarotech.lthisab.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarotech.lthisab.R
import com.sarotech.lthisab.data.AppDatabase
import com.sarotech.lthisab.data.AppSettings
import com.sarotech.lthisab.data.BalanceCalculator
import com.sarotech.lthisab.data.CashTransaction
import com.sarotech.lthisab.data.PeriodFilter
import com.sarotech.lthisab.data.SortOption
import com.sarotech.lthisab.export.StatementData
import com.sarotech.lthisab.export.StatementPdfGenerator
import com.sarotech.lthisab.i18n.AppStrings
import com.sarotech.lthisab.i18n.LocalStrings
import com.sarotech.lthisab.ui.theme.CashInGreenDark
import com.sarotech.lthisab.ui.theme.CashInGreenLight
import com.sarotech.lthisab.ui.theme.CashOutRedDark
import com.sarotech.lthisab.ui.theme.CashOutRedLight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBookScreen(
    db: AppDatabase,
    settings: AppSettings,
    onOpenSettings: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRows by db.cashDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var period by remember { mutableStateOf(settings.defaultPeriod) }
    var periodInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(settings.defaultPeriod) {
        if (!periodInitialized) {
            period = settings.defaultPeriod
            periodInitialized = true
        }
    }

    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }
    var showDateRangeDialog by remember { mutableStateOf(false) }

    var typeFilter by remember { mutableStateOf("ALL") }
    var sortOption by remember { mutableStateOf(SortOption.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<CashTransaction?>(null) }
    var deletingItem by remember { mutableStateOf<CashTransaction?>(null) }

    val inColor = if (settings.darkTheme) CashInGreenDark else CashInGreenLight
    val outColor = if (settings.darkTheme) CashOutRedDark else CashOutRedLight

    val dateFmt = remember(settings.dateFormat) {
        SimpleDateFormat(settings.dateFormat.pattern, Locale.getDefault())
    }
    val rowDateFmt = remember(settings.dateFormat, settings.use24HourTime, settings.showTimeOnRows) {
        SimpleDateFormat(
            if (settings.showTimeOnRows) settings.dateTimePattern() else settings.dateFormat.pattern,
            Locale.getDefault()
        )
    }

    val recentNotes = remember(allRows) {
        allRows.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct().take(15)
    }

    val globalRunningBalances = remember(allRows, settings.openingBalancePaise) {
        BalanceCalculator.balanceAfter(allRows, settings.openingBalancePaise)
    }

    val window = remember(period, settings.firstDayOfWeek, customStartMillis, customEndMillis) {
        BalanceCalculator.computeWindow(
            period = period,
            nowMillis = System.currentTimeMillis(),
            firstDayOfWeek = settings.firstDayOfWeek,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis
        )
    }

    val periodOpeningPaise = remember(allRows, settings.openingBalancePaise, window) {
        BalanceCalculator.openingBalanceForPeriod(
            allItems = allRows,
            baseOpeningBalancePaise = settings.openingBalancePaise,
            periodStartMillis = window.startMillis
        )
    }

    val periodRows = remember(allRows, window) {
        allRows.filter { item ->
            val afterStart = window.startMillis == null || item.timestamp >= window.startMillis
            val beforeEnd = window.endExclusiveMillis == null || item.timestamp < window.endExclusiveMillis
            afterStart && beforeEnd
        }
    }

    val displayedRows = remember(periodRows, typeFilter, searchQuery, sortOption, settings.currencySymbol, settings.indianNumberFormat) {
        val q = searchQuery.trim()
        val filtered = periodRows.filter { item ->
            val typeOk = typeFilter == "ALL" || item.type == typeFilter
            val searchOk = if (q.isEmpty()) true else {
                item.note.contains(q, ignoreCase = true) ||
                    BalanceCalculator.formatMoney(item.amountPaise, settings.currencySymbol, settings.indianNumberFormat)
                        .contains(q, ignoreCase = true) ||
                    (item.amountPaise / 100.0).toString().contains(q, ignoreCase = true)
            }
            typeOk && searchOk
        }
        when (sortOption) {
            SortOption.NEWEST -> filtered.sortedWith(compareByDescending<CashTransaction> { it.timestamp }.thenByDescending { it.id })
            SortOption.OLDEST -> filtered.sortedWith(compareBy<CashTransaction> { it.timestamp }.thenBy { it.id })
            SortOption.HIGHEST -> filtered.sortedWith(compareByDescending<CashTransaction> { it.amountPaise }.thenByDescending { it.timestamp })
            SortOption.LOWEST -> filtered.sortedWith(compareBy<CashTransaction> { it.amountPaise }.thenByDescending { it.timestamp })
        }
    }

    val (totalIn, totalOut) = remember(displayedRows) { BalanceCalculator.totals(displayedRows) }
    val includeOpeningInSummary = typeFilter == "ALL" && searchQuery.isBlank()
    val closingBalance = if (includeOpeningInSummary) {
        periodOpeningPaise + totalIn - totalOut
    } else {
        totalIn - totalOut
    }

    val periodDisplayLabel = remember(period, customStartMillis, customEndMillis, strings, dateFmt) {
        periodLabel(period, customStartMillis, customEndMillis, strings, dateFmt)
    }

    fun buildStatement(): StatementData {
        val stmtRows = displayedRows
        val (stmtIn, stmtOut) = BalanceCalculator.totals(stmtRows)
        val openBal = if (includeOpeningInSummary) periodOpeningPaise else 0L
        val running = if (includeOpeningInSummary) globalRunningBalances
        else BalanceCalculator.balanceAfter(stmtRows, 0L)
        return StatementData(
            periodLabel = periodDisplayLabel,
            openingBalancePaise = openBal,
            transactions = stmtRows,
            runningBalances = running,
            totalInPaise = stmtIn,
            totalOutPaise = stmtOut,
            closingBalancePaise = openBal + stmtIn - stmtOut
        )
    }

    fun runExport(action: (StatementData) -> Unit) {
        runCatching { action(buildStatement()) }
            .onFailure {
                Toast.makeText(context, "${strings.pdfFailed}: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.lt_hisab_logo),
                            contentDescription = strings.appName,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strings.brandName,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = strings.searchHint
                        )
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = strings.sortBy)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                SortOption.NEWEST to strings.sortNewest,
                                SortOption.OLDEST to strings.sortOldest,
                                SortOption.HIGHEST to strings.sortHighest,
                                SortOption.LOWEST to strings.sortLowest
                            ).forEach { (option, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (sortOption == option) "✓ $label" else label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = strings.exportPdf)
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.exportPdf) },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    runExport { StatementPdfGenerator.sharePdf(context, it, settings, strings) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.printStatement) },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    runExport { StatementPdfGenerator.printPdf(context, it, settings, strings) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.emailStatement) },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    runExport { StatementPdfGenerator.emailPdf(context, it, settings, strings) }
                                }
                            )
                        }
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settings)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.addTransaction)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = strings.cancel)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periods = listOf(
                    PeriodFilter.ALL to strings.allEntries,
                    PeriodFilter.DAILY to strings.daily,
                    PeriodFilter.WEEKLY to strings.weekly,
                    PeriodFilter.MONTHLY to strings.monthly,
                    PeriodFilter.YEARLY to strings.yearly
                )
                periods.forEach { (pf, label) ->
                    FilterChip(
                        selected = period == pf,
                        onClick = { period = pf },
                        label = { Text(label, maxLines = 1) }
                    )
                }
                FilterChip(
                    selected = period == PeriodFilter.CUSTOM,
                    onClick = { showDateRangeDialog = true },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(
                            text = if (period == PeriodFilter.CUSTOM && customStartMillis != null && customEndMillis != null) {
                                "${dateFmt.format(Date(customStartMillis!!))} - ${dateFmt.format(Date(customEndMillis!!))}"
                            } else strings.customRange,
                            maxLines = 1
                        )
                    }
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    title = strings.cashIn,
                    value = BalanceCalculator.formatMoney(totalIn, settings.currencySymbol, settings.indianNumberFormat),
                    valueColor = inColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = strings.cashOut,
                    value = BalanceCalculator.formatMoney(totalOut, settings.currencySymbol, settings.indianNumberFormat),
                    valueColor = outColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = strings.balance,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        if (includeOpeningInSummary && periodOpeningPaise != 0L) {
                            Text(
                                text = "${strings.openingBalance}: ${
                                    BalanceCalculator.formatMoney(
                                        periodOpeningPaise,
                                        settings.currencySymbol,
                                        settings.indianNumberFormat
                                    )
                                }",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = BalanceCalculator.formatMoney(
                            closingBalance,
                            settings.currencySymbol,
                            settings.indianNumberFormat
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.transactions} (${displayedRows.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = typeFilter == "ALL",
                        onClick = { typeFilter = "ALL" },
                        label = { Text(strings.all, fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = typeFilter == "IN",
                        onClick = { typeFilter = "IN" },
                        label = { Text(strings.inShort, fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = typeFilter == "OUT",
                        onClick = { typeFilter = "OUT" },
                        label = { Text(strings.outShort, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (displayedRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (allRows.isEmpty()) strings.noEntriesYet else strings.noMatchingEntries,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 76.dp)
                ) {
                    items(displayedRows, key = { it.id }) { item ->
                        val runBal = globalRunningBalances[item.id] ?: settings.openingBalancePaise
                        TransactionRowCard(
                            item = item,
                            runningBalancePaise = runBal,
                            formattedDate = rowDateFmt.format(Date(item.timestamp)),
                            settings = settings,
                            inColor = inColor,
                            outColor = outColor,
                            onEdit = { editingItem = item },
                            onDelete = { deletingItem = item }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 68.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        runExport { StatementPdfGenerator.sharePdf(context, it, settings, strings) }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportPdf, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        runExport { StatementPdfGenerator.printPdf(context, it, settings, strings) }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.printStatement, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditTransactionDialog(
            existing = null,
            settings = settings,
            recentNotes = recentNotes,
            onDismiss = { showAddDialog = false },
            onSave = { type, paise, note, ts ->
                scope.launch {
                    db.cashDao().insert(
                        CashTransaction(type = type, amountPaise = paise, note = note, timestamp = ts)
                    )
                    Toast.makeText(context, strings.entrySaved, Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }

    val itemToEdit = editingItem
    if (itemToEdit != null) {
        AddEditTransactionDialog(
            existing = itemToEdit,
            settings = settings,
            recentNotes = recentNotes,
            onDismiss = { editingItem = null },
            onSave = { type, paise, note, ts ->
                scope.launch {
                    db.cashDao().update(
                        itemToEdit.copy(type = type, amountPaise = paise, note = note, timestamp = ts)
                    )
                    Toast.makeText(context, strings.entryUpdated, Toast.LENGTH_SHORT).show()
                }
                editingItem = null
            }
        )
    }

    val itemToDelete = deletingItem
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(strings.confirmDeleteTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.confirmDeleteMessage)
                    Text(
                        text = "${itemToDelete.note} — ${
                            BalanceCalculator.formatMoney(
                                itemToDelete.amountPaise,
                                settings.currencySymbol,
                                settings.indianNumberFormat
                            )
                        }",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.cashDao().deleteById(itemToDelete.id)
                        Toast.makeText(context, strings.entryDeleted, Toast.LENGTH_SHORT).show()
                    }
                    deletingItem = null
                }) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showDateRangeDialog) {
        CustomDateRangeDialog(
            initialStart = customStartMillis ?: System.currentTimeMillis(),
            initialEnd = customEndMillis ?: System.currentTimeMillis(),
            dateFmt = dateFmt,
            onDismiss = { showDateRangeDialog = false },
            onApply = { start, end ->
                customStartMillis = minOf(start, end)
                customEndMillis = maxOf(start, end)
                period = PeriodFilter.CUSTOM
                showDateRangeDialog = false
            }
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TransactionRowCard(
    item: CashTransaction,
    runningBalancePaise: Long,
    formattedDate: String,
    settings: AppSettings,
    inColor: Color,
    outColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    val amountColor = if (item.type == "IN") inColor else outColor
    val signPrefix = if (item.type == "IN") "+ " else "- "

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.note,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
                if (settings.showRunningBalance) {
                    Text(
                        text = "${strings.balance}: ${
                            BalanceCalculator.formatMoney(
                                runningBalancePaise,
                                settings.currencySymbol,
                                settings.indianNumberFormat
                            )
                        }",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = signPrefix + BalanceCalculator.formatMoney(
                        item.amountPaise,
                        settings.currencySymbol,
                        settings.indianNumberFormat
                    ),
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = strings.edit,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.delete,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomDateRangeDialog(
    initialStart: Long,
    initialEnd: Long,
    dateFmt: SimpleDateFormat,
    onDismiss: () -> Unit,
    onApply: (startMillis: Long, endMillis: Long) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var startMillis by remember { mutableStateOf(initialStart) }
    var endMillis by remember { mutableStateOf(initialEnd) }

    fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val chosen = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(chosen.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectDateRange, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { pickDate(startMillis) { startMillis = it } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${strings.fromDate}: ${dateFmt.format(Date(startMillis))}")
                }
                OutlinedButton(
                    onClick = { pickDate(endMillis) { endMillis = it } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${strings.toDate}: ${dateFmt.format(Date(endMillis))}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(startMillis, endMillis) }) { Text(strings.apply) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

private fun periodLabel(
    period: PeriodFilter,
    customStart: Long?,
    customEnd: Long?,
    strings: AppStrings,
    dateFmt: SimpleDateFormat
): String = when (period) {
    PeriodFilter.ALL -> strings.allEntries
    PeriodFilter.DAILY -> "${strings.daily} (${dateFmt.format(Date())})"
    PeriodFilter.WEEKLY -> strings.weekly
    PeriodFilter.MONTHLY -> strings.monthly
    PeriodFilter.YEARLY -> strings.yearly
    PeriodFilter.CUSTOM -> {
        if (customStart != null && customEnd != null) {
            "${dateFmt.format(Date(customStart))} - ${dateFmt.format(Date(customEnd))}"
        } else strings.customRange
    }
}
