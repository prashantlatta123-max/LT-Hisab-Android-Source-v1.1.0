package com.sarotech.lthisab.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sarotech.lthisab.R
import com.sarotech.lthisab.data.AppSettings
import com.sarotech.lthisab.data.PinCrypto
import com.sarotech.lthisab.i18n.LocalStrings

object BiometricHelper {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun AppLockScreen(
    settings: AppSettings,
    onUnlocked: () -> Unit,
    onResetPin: (newPin: String, question: String, answer: String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canBiometric = remember(context, settings.biometricEnabled) {
        settings.biometricEnabled && BiometricHelper.isAvailable(context)
    }

    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }

    fun triggerBiometric() {
        if (activity != null && canBiometric) {
            BiometricHelper.authenticate(
                activity = activity,
                title = strings.biometricPromptTitle,
                subtitle = strings.biometricPromptSubtitle,
                negativeText = strings.cancel,
                onSuccess = onUnlocked,
                onError = { errorMsg = it }
            )
        }
    }

    LaunchedEffect(canBiometric) {
        if (canBiometric) {
            triggerBiometric()
        }
    }

    fun onDigit(d: String) {
        if (enteredPin.length >= 4) return
        errorMsg = ""
        val next = enteredPin + d
        enteredPin = next
        if (next.length == 4) {
            val hashed = PinCrypto.hashSecret(next, settings.pinSalt)
            if (hashed == settings.pinHash) {
                onUnlocked()
            } else {
                errorMsg = strings.wrongPin
                enteredPin = ""
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Image(
                    painter = painterResource(R.drawable.lt_hisab_logo),
                    contentDescription = strings.appName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = strings.enterPinTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.unlockAppSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { idx ->
                        val filled = idx < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(20.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { digit ->
                            PinPadButton(label = digit, onClick = { onDigit(digit) })
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canBiometric) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { triggerBiometric() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = strings.unlockWithBiometric,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.size(68.dp))
                    }

                    PinPadButton(label = "0", onClick = { onDigit("0") })

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = strings.delete,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showRecovery = true }) {
                    Text(strings.forgotPin)
                }
                if (canBiometric) {
                    OutlinedButton(onClick = { triggerBiometric() }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.unlockWithBiometric)
                    }
                }
            }
        }
    }

    if (showRecovery) {
        PinRecoveryDialog(
            settings = settings,
            onDismiss = { showRecovery = false },
            onRecovered = { newPin, question, answer ->
                onResetPin(newPin, question, answer)
                showRecovery = false
                onUnlocked()
            }
        )
    }
}

@Composable
private fun PinPadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PinRecoveryDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onRecovered: (newPin: String, question: String, answer: String) -> Unit
) {
    val strings = LocalStrings.current
    var answer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val hasRecovery = settings.securityQuestion.isNotBlank() && settings.securityAnswerHash.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.recoverPinTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasRecovery) {
                    Text(strings.noRecoverySet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(settings.securityQuestion, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it; error = "" },
                        label = { Text(strings.securityAnswerLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it; error = "" },
                        label = { Text(strings.newPinLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it; error = "" },
                        label = { Text(strings.confirmPinLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (hasRecovery) {
                TextButton(onClick = {
                    val trimmedAnswer = answer.trim()
                    if (trimmedAnswer.isEmpty()) {
                        error = strings.securityAnswerRequired
                    } else if (PinCrypto.hashSecret(trimmedAnswer, settings.pinSalt) != settings.securityAnswerHash) {
                        error = strings.wrongSecurityAnswer
                    } else if (newPin.length != 4) {
                        error = strings.pinMustBe4Digits
                    } else if (newPin != confirmPin) {
                        error = strings.pinsDoNotMatch
                    } else {
                        onRecovered(newPin, settings.securityQuestion, trimmedAnswer)
                    }
                }) {
                    Text(strings.save)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun PinSetupDialog(
    requireCurrentPin: Boolean,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSavePin: (newPin: String, question: String, answer: String) -> Unit
) {
    val strings = LocalStrings.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var question by remember { mutableStateOf(settings.securityQuestion) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (requireCurrentPin) strings.changePin else strings.setupPinTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (requireCurrentPin) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentPin = it; error = "" },
                        label = { Text(strings.currentPinLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it; error = "" },
                    label = { Text(strings.newPinLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it; error = "" },
                    label = { Text(strings.confirmPinLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it; error = "" },
                    label = { Text(strings.securityQuestionLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (question.isNotBlank()) {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it; error = "" },
                        label = { Text(strings.securityAnswerLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (requireCurrentPin && PinCrypto.hashSecret(currentPin, settings.pinSalt) != settings.pinHash) {
                    error = strings.wrongPin
                } else if (newPin.length != 4) {
                    error = strings.pinMustBe4Digits
                } else if (newPin != confirmPin) {
                    error = strings.pinsDoNotMatch
                } else if (question.isNotBlank() && answer.isBlank()) {
                    error = strings.securityAnswerRequired
                } else {
                    onSavePin(newPin, question, answer)
                }
            }) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
