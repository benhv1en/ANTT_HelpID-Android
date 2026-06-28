package com.helpid.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpid.app.data.AuthResult
import com.helpid.app.data.AuthTokenStore
import com.helpid.app.data.BiometricPreferenceStore
import com.helpid.app.data.FirebaseRepository
import com.helpid.app.data.HelpIdApiAuthRepository
import com.helpid.app.data.HelpIdApiEmergencyLinkRepository
import com.helpid.app.data.local.AppDatabase
import com.helpid.app.ui.AdminScreen
import com.helpid.app.ui.EditProfileScreen
import com.helpid.app.ui.EmergencyScreen
import com.helpid.app.ui.LanguageSelectionScreen
import com.helpid.app.ui.LoginScreen
import com.helpid.app.ui.QRScreen
import com.helpid.app.ui.RegisterScreen
import com.helpid.app.ui.components.ShimmerPlaceholder
import com.helpid.app.ui.components.SkeletonSpacer
import com.helpid.app.ui.components.SkeletonTextLine
import com.helpid.app.ui.theme.HelpIDTheme
import com.helpid.app.utils.BiometricAvailability
import com.helpid.app.utils.BiometricPromptError
import com.helpid.app.utils.BiometricUtils
import com.helpid.app.utils.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "HelpID"

sealed class AuthState {
    object Initializing : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data class BiometricLocked(val userId: String, val requiresRefresh: Boolean) : AuthState()
    data class LocalCacheOnly(val userId: String, val isOffline: Boolean = false) : AuthState()
    object Unauthenticated : AuthState()
}

class MainActivity : FragmentActivity() {
    private fun applyLockScreenFlagsIfNeeded() {
        val isFullscreenTest = intent?.getBooleanExtra("fullscreen_test", false) == true
        if (!isFullscreenTest) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applySavedLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        applyLockScreenFlagsIfNeeded()

        setContent {
            HelpIDTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startScreen = intent?.getStringExtra("open_screen") ?: "emergency"
                    AppNavigation(initialScreen = startScreen)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            applyLockScreenFlagsIfNeeded()
        }
    }
}

@Composable
private fun InitSkeleton(errorText: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShimmerPlaceholder(
            modifier = Modifier
                .size(48.dp)
        )
        SkeletonSpacer(16.dp)
        SkeletonTextLine(widthFraction = 0.4f, height = 12.dp)
        if (errorText != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.error_message, errorText),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AuthSessionBanner(message: String, onLoginClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onLoginClick) {
            Text(
                text = stringResource(R.string.auth_banner_login_again),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun AppNavigation(initialScreen: String = "emergency") {
    Log.d(TAG, "AppNavigation composable called")

    val currentScreen = remember { mutableStateOf(initialScreen) }
    val authState = remember { mutableStateOf<AuthState>(AuthState.Initializing) }
    val context = LocalContext.current
    val repository = remember { FirebaseRepository(context) }
    val tokenStore = remember { AuthTokenStore(context) }
    val biometricStore = remember { BiometricPreferenceStore(context) }
    val authRepository = remember { HelpIdApiAuthRepository(context) }
    val emergencyLinkRepository = remember { HelpIdApiEmergencyLinkRepository(context, tokenStore, authRepository) }
    val scope = rememberCoroutineScope()

    suspend fun hasCachedProfile(userId: String): Boolean {
        if (userId.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context)
                    .userProfileDao().getUserProfile(userId) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    fun authenticatedOrBiometricLocked(userId: String, requiresRefresh: Boolean): AuthState =
        resolveAuthState(
            userId = userId,
            isBiometricEnabled = biometricStore.isEnabledForUser(userId),
            requiresRefresh = requiresRefresh
        )

    fun refreshAfterBiometricUnlock(userId: String) {
        scope.launch {
            val storedRefresh = tokenStore.getRefreshToken()
            if (storedRefresh.isNullOrBlank()) {
                authState.value = if (hasCachedProfile(userId)) {
                    AuthState.LocalCacheOnly(userId, isOffline = false)
                } else {
                    AuthState.Unauthenticated
                }
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                authRepository.refresh(storedRefresh, Build.MODEL)
            }

            authState.value = when (result) {
                is AuthResult.Success -> {
                    tokenStore.saveTokens(
                        result.accessToken,
                        result.refreshToken,
                        result.userId,
                        result.accessTokenExpiresAtEpochMs,
                        result.refreshTokenExpiresAtEpochMs
                    )
                    AuthState.Authenticated(result.userId)
                }
                is AuthResult.NetworkError -> {
                    if (hasCachedProfile(userId)) {
                        AuthState.LocalCacheOnly(userId, isOffline = true)
                    } else {
                        AuthState.Unauthenticated
                    }
                }
                is AuthResult.ApiError -> {
                    if (result.httpStatus in 400..403) {
                        tokenStore.clearTokens()
                        biometricStore.clearForUser(userId)
                    }
                    if (hasCachedProfile(userId)) {
                        AuthState.LocalCacheOnly(userId, isOffline = false)
                    } else {
                        AuthState.Unauthenticated
                    }
                }
                else -> {
                    if (hasCachedProfile(userId)) {
                        AuthState.LocalCacheOnly(userId, isOffline = false)
                    } else {
                        AuthState.Unauthenticated
                    }
                }
            }
        }
    }

    fun performLogout() {
        scope.launch {
            val userId = tokenStore.getUserId().orEmpty()
            val storedRefresh = tokenStore.getRefreshToken()
            if (!storedRefresh.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try { authRepository.logout(storedRefresh) } catch (_: Exception) { }
                }
            }
            tokenStore.clearTokens()
            biometricStore.clearForUser(userId)
            val hasCache = hasCachedProfile(userId)
            currentScreen.value = "emergency"
            authState.value = if (hasCache)
                AuthState.LocalCacheOnly(userId, isOffline = false)
            else
                AuthState.Unauthenticated
        }
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "LaunchedEffect: starting auth init")
        try {
            // 1. Valid access token in store → skip network entirely
            if (tokenStore.hasValidSession()) {
                val uid = tokenStore.getUserId().orEmpty()
                Log.d(TAG, "Token valid; session restored")
                authState.value = authenticatedOrBiometricLocked(uid, requiresRefresh = false)
                return@LaunchedEffect
            }

            // 2. Attempt token refresh — read userId before any state mutation
            val storedRefresh = tokenStore.getRefreshToken()
            if (storedRefresh != null) {
                val storedUserId = tokenStore.getUserId().orEmpty()
                if (storedUserId.isNotBlank() && biometricStore.isEnabledForUser(storedUserId)) {
                    Log.d(TAG, "Session requires biometric before refresh")
                    authState.value = AuthState.BiometricLocked(storedUserId, requiresRefresh = true)
                    return@LaunchedEffect
                }
                Log.d(TAG, "Attempting token refresh")
                val result = withContext(Dispatchers.IO) {
                    authRepository.refresh(storedRefresh, Build.MODEL)
                }
                when (result) {
                    is AuthResult.Success -> {
                        tokenStore.saveTokens(
                            result.accessToken,
                            result.refreshToken,
                            result.userId,
                            result.accessTokenExpiresAtEpochMs,
                            result.refreshTokenExpiresAtEpochMs
                        )
                        authState.value = AuthState.Authenticated(result.userId)
                        return@LaunchedEffect
                    }
                    is AuthResult.NetworkError -> {
                        // Offline — show cached profile only; do not sync remote without a valid session.
                        Log.w(TAG, "Refresh failed: device offline")
                        val hasCache = hasCachedProfile(storedUserId)
                        authState.value = if (hasCache)
                            AuthState.LocalCacheOnly(storedUserId, isOffline = true)
                        else
                            AuthState.Unauthenticated
                        return@LaunchedEffect
                    }
                    is AuthResult.ApiError -> {
                        if (result.httpStatus in 400..403) {
                            // Invalid or revoked refresh token — discard credentials
                            Log.w(TAG, "Refresh failed: invalid token (${result.httpStatus})")
                            tokenStore.clearTokens()
                            biometricStore.clearForUser(storedUserId)
                            val hasCache = hasCachedProfile(storedUserId)
                            authState.value = if (hasCache)
                                AuthState.LocalCacheOnly(storedUserId, isOffline = false)
                            else
                                AuthState.Unauthenticated
                            return@LaunchedEffect
                        }
                        // Server-side errors (5xx) — fall through to Firebase fallback
                        Log.w(TAG, "Refresh ApiError ${result.httpStatus}, falling back to Firebase")
                    }
                    else -> {
                        Log.w(TAG, "Refresh unexpected result, falling back to Firebase")
                    }
                }
            }

            // 3. Firebase fallback → LocalCacheOnly for anonymous / legacy users
            withContext(Dispatchers.IO) {
                try {
                    withTimeout(10000L) {
                        val firebaseUserId = repository.initializeUser()
                        if (firebaseUserId.isNotEmpty()) {
                            Log.d(TAG, "Firebase fallback initialized")
                            authState.value = AuthState.LocalCacheOnly(firebaseUserId, isOffline = false)
                        } else {
                            Log.w(TAG, "Firebase returned empty userId")
                            authState.value = AuthState.Unauthenticated
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Firebase init timed out")
                    authState.value = AuthState.Unauthenticated
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase init error")
                    authState.value = AuthState.Unauthenticated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth init exception")
            authState.value = AuthState.Unauthenticated
        }
    }

    when {
        authState.value is AuthState.Initializing -> {
            InitSkeleton(null)
        }

        authState.value is AuthState.BiometricLocked && currentScreen.value != "login" -> {
            val lockedState = authState.value as AuthState.BiometricLocked
            BiometricLockScreen(
                userId = lockedState.userId,
                requiresRefresh = lockedState.requiresRefresh,
                biometricStore = biometricStore,
                onUnlocked = { userId, requiresRefresh ->
                    if (requiresRefresh) {
                        refreshAfterBiometricUnlock(userId)
                    } else {
                        authState.value = AuthState.Authenticated(userId)
                    }
                },
                onUsePassword = { currentScreen.value = "login" }
            )
        }

        currentScreen.value == "register" -> {
            RegisterScreen(
                authRepository = authRepository,
                tokenStore = tokenStore,
                onRegisterSuccess = { userId ->
                    authState.value = AuthState.Authenticated(userId)
                    currentScreen.value = "emergency"
                },
                onGoToLogin = { currentScreen.value = "login" }
            )
        }

        authState.value is AuthState.Unauthenticated || currentScreen.value == "login" -> {
            LoginScreen(
                authRepository = authRepository,
                tokenStore = tokenStore,
                onLoginSuccess = { userId ->
                    authState.value = AuthState.Authenticated(userId)
                    currentScreen.value = "emergency"
                },
                onGoToRegister = { currentScreen.value = "register" },
                onDismiss = if (authState.value is AuthState.LocalCacheOnly) {
                    { currentScreen.value = "" }
                } else null
            )
        }

        authState.value is AuthState.LocalCacheOnly -> {
            val localState = authState.value as AuthState.LocalCacheOnly
            val bannerMessage = if (localState.isOffline)
                stringResource(R.string.auth_banner_offline)
            else
                stringResource(R.string.auth_banner_session_expired)
            Column(modifier = Modifier.fillMaxSize()) {
                AuthSessionBanner(
                    message = bannerMessage,
                    onLoginClick = { currentScreen.value = "login" }
                )
                Box(modifier = Modifier.weight(1f)) {
                    EmergencyScreen(
                        userId = localState.userId,
                        onLanguageClick = { currentScreen.value = "language" }
                    )
                }
            }
            if (currentScreen.value == "language") {
                LanguageSelectionScreen(
                    onLanguageSelected = { currentScreen.value = "" },
                    onBackClick = { currentScreen.value = "" }
                )
            }
        }

        else -> {
            val authenticatedState = authState.value as? AuthState.Authenticated
            val userId = authenticatedState?.userId.orEmpty()
            val isAdminUser = remember(authState.value) { tokenStore.isAdmin() }

            Scaffold(
                contentWindowInsets = WindowInsets.navigationBars,
                bottomBar = {
                    HelpIdBottomBar(
                        currentRoute = currentScreen.value,
                        onRouteSelected = { route -> currentScreen.value = route }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen.value) {
                        "emergency" -> {
                            Log.d(TAG, "Rendering EmergencyScreen")
                            EmergencyScreen(
                                userId = userId,
                                onLanguageClick = { currentScreen.value = "language" },
                                onMintLink = { emergencyLinkRepository.mintOrEmpty() },
                                onAdminClick = if (isAdminUser) {
                                    { currentScreen.value = "admin" }
                                } else null
                            )
                        }
                        "qr" -> {
                            Log.d(TAG, "Rendering QRScreen")
                            QRScreen(
                                userId = userId,
                                onMintLink = { emergencyLinkRepository.mintOrEmpty() },
                                onBackClick = { currentScreen.value = "emergency" }
                            )
                        }
                        "edit" -> {
                            Log.d(TAG, "Rendering EditProfileScreen")
                            EditProfileScreen(
                                userId = userId,
                                onBackClick = { currentScreen.value = "emergency" },
                                onSaveSuccess = { currentScreen.value = "emergency" },
                                onLogout = ::performLogout
                            )
                        }
                        "language" -> {
                            Log.d(TAG, "Rendering LanguageSelectionScreen")
                            LanguageSelectionScreen(
                                onLanguageSelected = { currentScreen.value = "emergency" },
                                onBackClick = { currentScreen.value = "emergency" }
                            )
                        }
                        "admin" -> {
                            LaunchedEffect(isAdminUser) {
                                if (!isAdminUser) currentScreen.value = "emergency"
                            }
                            if (isAdminUser) {
                                AdminScreen(
                                    onBackClick = { currentScreen.value = "emergency" },
                                    onUnauthorized = { currentScreen.value = "emergency" }
                                )
                            }
                        }
                        else -> {
                            currentScreen.value = "emergency"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(
    userId: String,
    requiresRefresh: Boolean,
    biometricStore: BiometricPreferenceStore,
    onUnlocked: (String, Boolean) -> Unit,
    onUsePassword: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val statusMessage = remember { mutableStateOf<Int?>(null) }

    fun availabilityMessage(availability: BiometricAvailability): Int {
        return when (availability) {
            BiometricAvailability.NoneEnrolled -> R.string.biometric_not_enrolled
            BiometricAvailability.NoHardware,
            BiometricAvailability.HardwareUnavailable,
            BiometricAvailability.SecurityUpdateRequired,
            BiometricAvailability.Unsupported -> R.string.biometric_not_available
            BiometricAvailability.Unknown -> R.string.biometric_system_error
            BiometricAvailability.Available -> R.string.biometric_unlock_subtitle
        }
    }

    fun requestUnlock() {
        if (activity == null) {
            statusMessage.value = R.string.biometric_system_error
            return
        }

        val availability = BiometricUtils.getAvailability(context, allowDeviceCredential = true)
        if (availability != BiometricAvailability.Available) {
            statusMessage.value = availabilityMessage(availability)
            return
        }

        BiometricUtils.showBiometricPrompt(
            activity = activity,
            executor = ContextCompat.getMainExecutor(context),
            onSuccess = {
                biometricStore.markUnlockedForUser(userId)
                onUnlocked(userId, requiresRefresh)
            },
            onError = { failure ->
                statusMessage.value = when (failure.error) {
                    BiometricPromptError.Canceled -> R.string.biometric_canceled
                    else -> failure.messageResId
                }
            },
            allowDeviceCredential = true
        )
    }

    LaunchedEffect(userId, requiresRefresh) {
        requestUnlock()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.biometric_unlock_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(statusMessage.value ?: R.string.biometric_unlock_subtitle),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(22.dp))
        TextButton(onClick = { requestUnlock() }) {
            Text(
                text = stringResource(R.string.biometric_try_again),
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(onClick = onUsePassword) {
            Text(stringResource(R.string.biometric_use_password))
        }
    }
}

@Composable
private fun HelpIdBottomBar(
    currentRoute: String,
    onRouteSelected: (String) -> Unit
) {
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val active = MaterialTheme.colorScheme.primary
    val glow = active.copy(alpha = 0.18f)
    val pillGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, RoundedCornerShape(30.dp)),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pillGradient)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BottomItem(
                isSelected = currentRoute == "emergency",
                label = stringResource(R.string.bottom_nav_id),
                icon = Icons.Outlined.Home,
                activeColor = active,
                inactiveColor = inactive,
                glowColor = glow,
                onClick = { onRouteSelected("emergency") }
            )
            BottomItem(
                isSelected = currentRoute == "qr",
                label = stringResource(R.string.bottom_nav_qr),
                icon = Icons.Outlined.QrCode,
                activeColor = active,
                inactiveColor = inactive,
                glowColor = glow,
                onClick = { onRouteSelected("qr") }
            )
            BottomItem(
                isSelected = currentRoute == "edit",
                label = stringResource(R.string.bottom_nav_profile),
                icon = Icons.Outlined.Person,
                activeColor = active,
                inactiveColor = inactive,
                glowColor = glow,
                onClick = { onRouteSelected("edit") }
            )
        }
    }
}

@Composable
private fun BottomItem(
    isSelected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    inactiveColor: Color,
    glowColor: Color,
    onClick: () -> Unit
) {
    val targetColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        label = "bottomItemColor"
    )
    val targetWidth by animateDpAsState(
        targetValue = 52.dp,
        label = "bottomItemWidth"
    )
    val bg by animateColorAsState(
        targetValue = if (isSelected) glowColor else Color.Transparent,
        label = "bottomItemBg"
    )

    Surface(
        color = bg,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .width(targetWidth)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Transparent, CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = targetColor
                )
            }
        }
    }
}
