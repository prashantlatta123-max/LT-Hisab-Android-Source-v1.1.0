package com.sarotech.lthisab.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarotech.lthisab.BuildConfig
import com.sarotech.lthisab.R
import com.sarotech.lthisab.data.AppDatabase
import com.sarotech.lthisab.data.AppSettings
import com.sarotech.lthisab.data.BackupManager
import com.sarotech.lthisab.data.BackupParseResult
import com.sarotech.lthisab.data.BackupPayload
import com.sarotech.lthisab.data.BalanceCalculator
import com.sarotech.lthisab.data.DateFormatOption
import com.sarotech.lthisab.data.PeriodFilter
import com.sarotech.lthisab.data.SettingsRepository
import com.sarotech.lthisab.i18n.AppLanguage
import com.sarotech.lthisab.i18n.LocalStrings
import com.sarotech.lthisab.ui.security.BiometricHelper
import com.sarotech.lthisab.ui.security.PinSetupDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    db: AppDatabase,
    settings: AppSettings,
    settingsRepo: SettingsRepository,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRows by db.cashDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var userName by remember(settings.userName) { mutableStateOf(settings.userName) }
    var userAddress by remember(settings.userAddress) { mutableStateOf(settings.userAddress) }
    var openingBalanceText by remember(settings.openingBalancePaise) {
        mutableStateOf(
            if (settings.openingBalancePaise == 0L) "0.00"
            else String.format(Locale.US, "%.2f", settings.openingBalancePaise / 100.0)
        )
    }

    var showPinSetup by remember { mutableStateOf(false) }
    var pinChangeMode by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<BackupPayload?>(null) }

    val biometricAvailable = remember(context) { BiometricHelper.isAvailable(context) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = BackupManager.serialize(
                        transactions = allRows,
                        openingBalancePaise = settings.openingBalancePaise,
                        userName = settings.userName,
                        userAddress = settings.userAddress
                    )
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Unable to write file")
                }.onSuccess {
                    Toast.makeText(context, strings.backupSaved, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "${strings.backupFailed}: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read file")
                }.onSuccess { raw ->
                    when (val res = BackupManager.parseAndValidate(raw)) {
                        is BackupParseResult.Success -> pendingRestore = res.payload
                        is BackupParseResult.Error -> {
                            Toast.makeText(context, "${strings.restoreFailed}: ${res.reason}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.onFailure {
                    Toast.makeText(context, "${strings.restoreFailed}: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareBackupFile() {
        runCatching {
            val json = BackupManager.serialize(
                transactions = allRows,
                openingBalancePaise = settings.openingBalancePaise,
                userName = settings.userName,
                userAddress = settings.userAddress
            )
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val outDir = context.getExternalFilesDir(null) ?: context.cacheDir
            val file = File(outDir, "LT-Hisab-Backup-$stamp.json")
            file.writeText(json)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, strings.shareBackup))
        }.onFailure {
            Toast.makeText(context, "${strings.backupFailed}: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text(strings.settings, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Language & Theme
            SettingsSectionCard(title = strings.sectionAppearance) {
                Text(strings.languageLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { scope.launch { settingsRepo.setLanguage(lang) } },
                            label = { Text(lang.nativeLabel) }
                        )
                    }
                }

                HorizontalDivider()

                SettingsSwitchRow(
                    title = strings.themeLabel,
                    subtitle = if (settings.darkTheme) strings.darkThemeDesc else strings.lightThemeDesc,
                    checked = settings.darkTheme,
                    onCheckedChange = { scope.launch { settingsRepo.setDarkTheme(it) } }
                )
            }

            // 2. Profile & Opening Balance
            SettingsSectionCard(title = strings.sectionProfile) {
                Text(strings.profileHelper, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text(strings.userNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = userAddress,
                    onValueChange = { userAddress = it },
                    label = { Text(strings.userAddressLabel) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingBalanceText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' || it == '-' || it == ',' }) {
                            openingBalanceText = input
                        }
                    },
                    label = { Text("${strings.openingBalanceTitle} (${settings.currencySymbol})") },
                    supportingText = { Text(strings.openingBalanceHelper, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val parsed = if (openingBalanceText.trim().isEmpty()) 0L
                        else BalanceCalculator.parseAmountToPaise(openingBalanceText, allowNegative = true)
                        if (parsed == null) {
                            Toast.makeText(context, strings.amountRequired, Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                settingsRepo.setUserProfile(userName, userAddress)
                                settingsRepo.setOpeningBalance(parsed)
                                Toast.makeText(context, strings.savedToast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.saveProfile)
                }
            }

            // 3. Display & Formats
            SettingsSectionCard(title = strings.sectionDisplay) {
                Text(strings.currencySymbolLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("₹", "Rs.", "$", "€", "£").forEach { sym ->
                        FilterChip(
                            selected = settings.currencySymbol == sym,
                            onClick = { scope.launch { settingsRepo.setCurrencySymbol(sym) } },
                            label = { Text(sym) }
                        )
                    }
                }

                SettingsSwitchRow(
                    title = strings.indianNumberFormatLabel,
                    subtitle = strings.indianNumberFormatDesc,
                    checked = settings.indianNumberFormat,
                    onCheckedChange = { scope.launch { settingsRepo.setIndianNumberFormat(it) } }
                )

                HorizontalDivider()

                Text(strings.dateFormatLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFormatOption.entries.forEach { opt ->
                        FilterChip(
                            selected = settings.dateFormat == opt,
                            onClick = { scope.launch { settingsRepo.setDateFormat(opt) } },
                            label = { Text(opt.label) }
                        )
                    }
                }

                SettingsSwitchRow(
                    title = strings.timeFormat24hLabel,
                    checked = settings.use24HourTime,
                    onCheckedChange = { scope.launch { settingsRepo.setUse24HourTime(it) } }
                )

                Text(strings.firstDayOfWeekLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.firstDayOfWeek == Calendar.MONDAY,
                        onClick = { scope.launch { settingsRepo.setFirstDayOfWeek(Calendar.MONDAY) } },
                        label = { Text(strings.monday) }
                    )
                    FilterChip(
                        selected = settings.firstDayOfWeek == Calendar.SUNDAY,
                        onClick = { scope.launch { settingsRepo.setFirstDayOfWeek(Calendar.SUNDAY) } },
                        label = { Text(strings.sunday) }
                    )
                }

                Text(strings.defaultPeriodLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        PeriodFilter.ALL to strings.allEntries,
                        PeriodFilter.DAILY to strings.daily,
                        PeriodFilter.WEEKLY to strings.weekly,
                        PeriodFilter.MONTHLY to strings.monthly,
                        PeriodFilter.YEARLY to strings.yearly
                    ).forEach { (pf, label) ->
                        FilterChip(
                            selected = settings.defaultPeriod == pf,
                            onClick = { scope.launch { settingsRepo.setDefaultPeriod(pf) } },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider()

                SettingsSwitchRow(
                    title = strings.showRunningBalanceLabel,
                    checked = settings.showRunningBalance,
                    onCheckedChange = { scope.launch { settingsRepo.setShowRunningBalance(it) } }
                )
                SettingsSwitchRow(
                    title = strings.showTimeLabel,
                    checked = settings.showTimeOnRows,
                    onCheckedChange = { scope.launch { settingsRepo.setShowTimeOnRows(it) } }
                )
                SettingsSwitchRow(
                    title = strings.notesSuggestionsToggleLabel,
                    checked = settings.notesSuggestionsEnabled,
                    onCheckedChange = { scope.launch { settingsRepo.setNotesSuggestionsEnabled(it) } }
                )
                SettingsSwitchRow(
                    title = strings.keepScreenOnLabel,
                    checked = settings.keepScreenOn,
                    onCheckedChange = { scope.launch { settingsRepo.setKeepScreenOn(it) } }
                )
            }

            // 4. Security (PIN & Biometric)
            SettingsSectionCard(title = strings.sectionSecurity) {
                SettingsSwitchRow(
                    title = strings.pinLockLabel,
                    subtitle = strings.pinLockDesc,
                    checked = settings.pinEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            pinChangeMode = false
                            showPinSetup = true
                        } else {
                            scope.launch {
                                settingsRepo.disablePin()
                                Toast.makeText(context, strings.pinDisabled, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                if (settings.pinEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pinChangeMode = true
                                showPinSetup = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.changePin)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settingsRepo.disablePin()
                                    Toast.makeText(context, strings.pinDisabled, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.disablePin, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    SettingsSwitchRow(
                        title = strings.biometricUnlockLabel,
                        subtitle = if (biometricAvailable) null else strings.biometricNotAvailable,
                        checked = settings.biometricEnabled && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = { scope.launch { settingsRepo.setBiometricEnabled(it) } }
                    )
                }
            }

            // 5. Local Backup & Restore
            SettingsSectionCard(title = strings.sectionBackup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            saveBackupLauncher.launch("LT-Hisab-Backup-$stamp.json")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.saveBackup, fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { shareBackupFile() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.shareBackup, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Button(
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.restoreBackup)
                }
            }

            // 6. About & Original Brand Logos
            SettingsSectionCard(title = strings.sectionAbout) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.lt_hisab_logo),
                        contentDescription = strings.appName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column(Modifier.weight(1f)) {
                        Text(strings.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "${strings.personalCashBook} • ${strings.versionLabel} ${BuildConfig.VERSION_NAME}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Image(
                    painter = painterResource(R.drawable.sarotech_logo),
                    contentDescription = strings.brandName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Text(
                    text = strings.aboutOfflineNote,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPinSetup) {
        PinSetupDialog(
            requireCurrentPin = pinChangeMode,
            settings = settings,
            onDismiss = { showPinSetup = false },
            onSavePin = { newPin, question, answer ->
                scope.launch {
                    settingsRepo.setPin(newPin, question, answer)
                    Toast.makeText(context, strings.pinUpdated, Toast.LENGTH_SHORT).show()
                }
                showPinSetup = false
            }
        )
    }

    val payload = pendingRestore
    if (payload != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(strings.restoreConfirmTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    String.format(
                        Locale.getDefault(),
                        strings.restoreConfirmBody,
                        payload.transactions.size,
                        payload.schemaVersion,
                        allRows.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            db.cashDao().replaceAll(payload.transactions)
                            if (payload.openingBalancePaise != null) {
                                settingsRepo.setOpeningBalance(payload.openingBalancePaise)
                            }
                            if (payload.userName != null || payload.userAddress != null) {
                                settingsRepo.setUserProfile(
                                    payload.userName ?: settings.userName,
                                    payload.userAddress ?: settings.userAddress
                                )
                            }
                        }.onSuccess {
                            Toast.makeText(context, strings.restoreSuccess, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "${strings.restoreFailed}: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    pendingRestore = null
                }) {
                    Text(strings.restoreNow, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
