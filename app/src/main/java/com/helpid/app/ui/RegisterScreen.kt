package com.helpid.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private const val MAX_DISPLAY_NAME_LENGTH = 80

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    tokenStore: AuthTokenStore,
    onRegisterSuccess: (userId: String) -> Unit,
    onGoToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var displayNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var apiError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    fun submit() {
        if (isLoading) return
        displayNameError = null
        emailError = null
        passwordError = null
        confirmPasswordError = null
        apiError = null

        var hasError = false
        if (displayName.length > MAX_DISPLAY_NAME_LENGTH) {
            displayNameError = context.getString(R.string.auth_error_display_name_too_long)
            hasError = true
        }
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
        } else if (password.length < 12) {
            passwordError = context.getString(R.string.auth_error_password_too_short)
            hasError = true
        }
        if (confirmPassword != password) {
            confirmPasswordError = context.getString(R.string.auth_error_passwords_mismatch)
            hasError = true
        }
        if (hasError) return

        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                authRepository.register(
                    email = email.trim(),
                    password = password,
                    displayName = displayName.trim().ifBlank { null },
                    deviceName = Build.MODEL
                )
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
                    onRegisterSuccess(result.userId)
                }
                is AuthResult.ApiError -> {
                    apiError = mapRegisterApiError(context, result.httpStatus, result.errorCode)
                }
                is AuthResult.ValidationError -> {
                    result.fieldErrors["displayname"]?.let {
                        displayNameError = context.getString(R.string.auth_error_display_name_too_long)
                    }
                    result.fieldErrors["email"]?.let { emailError = mapRegisterFieldCode(context, it) }
                    result.fieldErrors["password"]?.let { passwordError = mapRegisterFieldCode(context, it) }
                    if (displayNameError == null && emailError == null && passwordError == null) {
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
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_register_subtitle),
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
                text = stringResource(R.string.auth_register_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; displayNameError = null },
                label = { Text(stringResource(R.string.auth_field_display_name)) },
                isError = displayNameError != null,
                supportingText = {
                    Text(
                        text = displayNameError.orEmpty(),
                        color = MaterialTheme.colorScheme.error.copy(
                            alpha = if (displayNameError != null) 1f else 0f
                        ),
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { emailFocusRequester.requestFocus() }
                )
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; apiError = null },
                label = { Text(stringResource(R.string.auth_field_email)) },
                isError = emailError != null,
                supportingText = {
                    Text(
                        text = emailError.orEmpty(),
                        color = MaterialTheme.colorScheme.error.copy(
                            alpha = if (emailError != null) 1f else 0f
                        ),
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocusRequester),
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
                supportingText = {
                    Text(
                        text = passwordError.orEmpty(),
                        color = MaterialTheme.colorScheme.error.copy(
                            alpha = if (passwordError != null) 1f else 0f
                        ),
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { confirmPasswordFocusRequester.requestFocus() }
                ),
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

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; confirmPasswordError = null },
                label = { Text(stringResource(R.string.auth_field_confirm_password)) },
                isError = confirmPasswordError != null,
                supportingText = {
                    Text(
                        text = confirmPasswordError.orEmpty(),
                        color = MaterialTheme.colorScheme.error.copy(
                            alpha = if (confirmPasswordError != null) 1f else 0f
                        ),
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(confirmPasswordFocusRequester),
                singleLine = true,
                visualTransformation = if (showConfirmPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Outlined.VisibilityOff
                                          else Icons.Outlined.Visibility,
                            contentDescription = stringResource(
                                if (showConfirmPassword) R.string.auth_cd_hide_password
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

            RegisterLoadingButton(
                text = stringResource(
                    if (isLoading) R.string.auth_btn_registering else R.string.auth_btn_register
                ),
                isLoading = isLoading,
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth()
            )

            GhostButton(
                text = stringResource(R.string.auth_link_go_to_login),
                onClick = onGoToLogin,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RegisterLoadingButton(
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

private fun mapRegisterApiError(context: Context, httpStatus: Int, errorCode: String?): String =
    when {
        httpStatus == 409 -> context.getString(R.string.auth_error_email_taken)
        else -> context.getString(R.string.auth_error_server)
    }

private fun mapRegisterFieldCode(context: Context, code: String): String = when (code) {
    "email.required" -> context.getString(R.string.auth_error_email_required)
    "email.invalid" -> context.getString(R.string.auth_error_email_invalid)
    "email.too_long" -> context.getString(R.string.auth_error_email_too_long)
    "email.taken" -> context.getString(R.string.auth_error_email_taken)
    "password.required" -> context.getString(R.string.auth_error_password_required)
    "password.too_short" -> context.getString(R.string.auth_error_password_too_short)
    "password.too_long" -> context.getString(R.string.auth_error_password_too_long)
    else -> context.getString(R.string.auth_error_server)
}
