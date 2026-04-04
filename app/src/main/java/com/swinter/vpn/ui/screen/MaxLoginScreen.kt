package com.swinter.vpn.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swinter.vpn.data.MaxAuthService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var verifyToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var resendTimer by remember { mutableIntStateOf(0) }
    var needs2FA by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    // Resend timer
    LaunchedEffect(resendTimer) {
        if (resendTimer > 0) {
            kotlinx.coroutines.delay(1000)
            resendTimer--
        }
    }

    fun requestCode() {
        val cleanPhone = phone.replace(Regex("[\\s\\-()]"), "")
        val fullPhone = if (cleanPhone.startsWith("+")) cleanPhone else "+$cleanPhone"

        scope.launch {
            isLoading = true
            errorText = ""
            val result = MaxAuthService.requestCode(fullPhone)
            result.onSuccess { (token, _) ->
                verifyToken = token
                codeSent = true
                resendTimer = 60
            }.onFailure { e ->
                errorText = e.message ?: "Ошибка"
            }
            isLoading = false
        }
    }

    fun confirmCode() {
        val cleanPhone = phone.replace(Regex("[\\s\\-()]"), "")
        val fullPhone = if (cleanPhone.startsWith("+")) cleanPhone else "+$cleanPhone"

        // Save 2FA password if provided
        if (password.isNotBlank()) {
            MaxAuthService.set2FAPassword(context, password)
        }

        scope.launch {
            isLoading = true
            errorText = ""
            val result = MaxAuthService.confirmAndLogin(context, fullPhone, verifyToken, code)
            result.onSuccess {
                onBack()
            }.onFailure { e ->
                if (e.message == "2FA_REQUIRED") {
                    needs2FA = true
                    errorText = "Введите пароль 2FA"
                } else {
                    errorText = e.message ?: "Ошибка"
                }
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вход в MAX") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Icon(
                Icons.Default.MarkEmailRead,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Вход в MAX", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Для получения TURN-серверов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (!codeSent) {
                // Phone input
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Номер телефона") },
                    placeholder = { Text("79991234567") },
                    prefix = { Text("+") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { requestCode() },
                    enabled = phone.length >= 10 && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "Отправляем..." else "Отправить код")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "SMS-код придёт на указанный номер",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Code input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Код отправлен на +$phone", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("SMS-код") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    ),
                )
                if (needs2FA) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль 2FA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { confirmCode() },
                    enabled = code.length >= 4 && (!needs2FA || password.isNotBlank()) && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoading) "Входим..." else "Войти")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = {
                        codeSent = false
                        code = ""
                        verifyToken = ""
                        errorText = ""
                    }) {
                        Text("Другой номер")
                    }
                    if (resendTimer > 0) {
                        Text(
                            "Повтор через ${resendTimer}с",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    } else {
                        TextButton(onClick = { requestCode() }) {
                            Text("Отправить снова")
                        }
                    }
                }
            }

            // Error
            if (errorText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
