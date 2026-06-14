package com.helpid.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpid.app.R
import com.helpid.app.data.AuthRepository
import com.helpid.app.data.AuthResult
import com.helpid.app.data.AuthTokenStore
import com.helpid.app.ui.components.GhostButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    tokenStore: AuthTokenStore,
    onLoginSuccess: (userId: String) -> Unit,
    onGoToRegister: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var apiError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    fun submit() {
        if (isLoading) return
        emailError = null
        passwordError = null
        apiError = null

        var hasError = false
        if (email.isBlank()) {
            emailError = context.getString(R.string.auth_error_email_required)
            hasError = true
        } else if (!email.contains("@")) {
            emailError = context.getString(R.string.auth_error_email_invalid)
            hasError = true
        }
        if (password.isEmpty()) {
            passwordError = context.getString(R.string.auth_error_password_required)
            hasError = true
        }
        if (hasError) return

        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                authRepository.login(email.trim(), password, Build.MODEL)
            }
            isLoading = false
            when (result) {
                is AuthResult.Success -> {
                    tokenStore.saveTokens(
                        result.accessToken,
                        result.refreshToken,
                        result.userId,
                        result.accessTokenExpiresAtEpochMs,
                        result.refreshTokenExpiresAtEpochMs
                    )
                    onLoginSuccess(result.userId)
                }
                is AuthResult.ApiError -> {
                    apiError = mapApiError(context, result.httpStatus, result.errorCode)
                }
                is AuthResult.ValidationError -> {
                    result.fieldErrors["email"]?.let { emailError = mapFieldCode(context, it) }
                    result.fieldErrors["password"]?.let { passwordError = mapFieldCode(context, it) }
                    if (emailError == null && passwordError == null) {
                        apiError = context.getString(R.string.auth_error_server)
                    }
                }
                is AuthResult.NetworkError -> {
                    apiError = context.getString(R.string.auth_error_network)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Dark header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 24.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (onDismiss != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_login_subtitle),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Form
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_login_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; apiError = null },
                label = { Text(stringResource(R.string.auth_field_email)) },
                isError = emailError != null,
                supportingText = emailError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; apiError = null },
                label = { Text(stringResource(R.string.auth_field_password)) },
                isError = passwordError != null,
                supportingText = passwordError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Outlined.VisibilityOff
                                          else Icons.Outlined.Visibility,
                            contentDescription = stringResource(
                                if (showPassword) R.string.auth_cd_hide_password
                                else R.string.auth_cd_show_password
                            )
                        )
                    }
                }
            )

            if (apiError != null) {
                Text(
                    text = apiError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            AuthLoadingButton(
                text = stringResource(
                    if (isLoading) R.string.auth_btn_logging_in else R.string.auth_btn_login
                ),
                isLoading = isLoading,
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth()
            )

            GhostButton(
                text = stringResource(R.string.auth_link_go_to_register),
                onClick = onGoToRegister,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AuthLoadingButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.4.sp
        )
    }
}

private fun mapApiError(context: Context, httpStatus: Int, errorCode: String?): String =
    when {
        httpStatus == 401 -> context.getString(R.string.auth_error_invalid_credentials)
        httpStatus == 423 -> context.getString(R.string.auth_error_account_locked)
        httpStatus == 409 -> context.getString(R.string.auth_error_email_taken)
        else -> context.getString(R.string.auth_error_server)
    }

private fun mapFieldCode(context: Context, code: String): String = when (code) {
    "email.required" -> context.getString(R.string.auth_error_email_required)
    "email.invalid" -> context.getString(R.string.auth_error_email_invalid)
    "email.too_long" -> context.getString(R.string.auth_error_email_too_long)
    "password.required" -> context.getString(R.string.auth_error_password_required)
    "password.too_short" -> context.getString(R.string.auth_error_password_too_short)
    "password.too_long" -> context.getString(R.string.auth_error_password_too_long)
    else -> context.getString(R.string.auth_error_server)
}
