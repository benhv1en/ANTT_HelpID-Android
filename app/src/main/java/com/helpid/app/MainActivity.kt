package com.helpid.app

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpid.app.data.AuthResult
import com.helpid.app.data.AuthTokenStore
import com.helpid.app.data.FirebaseRepository
import com.helpid.app.data.StubAuthRepository
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
import com.helpid.app.utils.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "HelpID"

sealed class AuthState {
    object Initializing : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data class LocalCacheOnly(val firebaseUserId: String) : AuthState()
    object Unauthenticated : AuthState()
}

class MainActivity : ComponentActivity() {
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
private fun AuthSessionBanner(onLoginClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.auth_banner_session_expired),
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
    val authRepository = remember { StubAuthRepository() }

    LaunchedEffect(Unit) {
        Log.d(TAG, "LaunchedEffect: starting auth init")
        try {
            // 1. Valid access token in store → skip network
            if (tokenStore.hasValidSession()) {
                val uid = tokenStore.getUserId().orEmpty()
                Log.d(TAG, "Token valid, userId=$uid")
                authState.value = AuthState.Authenticated(uid)
                return@LaunchedEffect
            }

            // 2. Attempt token refresh
            val storedRefresh = tokenStore.getRefreshToken()
            if (storedRefresh != null) {
                Log.d(TAG, "Attempting token refresh")
                val result = withContext(Dispatchers.IO) {
                    authRepository.refresh(storedRefresh, Build.MODEL)
                }
                if (result is AuthResult.Success) {
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
                Log.d(TAG, "Refresh failed, falling back to Firebase")
            }

            // 3. Firebase fallback → LocalCacheOnly for existing users
            withContext(Dispatchers.IO) {
                try {
                    withTimeout(10000L) {
                        val firebaseUserId = repository.initializeUser()
                        if (firebaseUserId.isNotEmpty()) {
                            Log.d(TAG, "Firebase fallback: firebaseUserId=$firebaseUserId")
                            authState.value = AuthState.LocalCacheOnly(firebaseUserId)
                        } else {
                            Log.w(TAG, "Firebase returned empty userId")
                            authState.value = AuthState.Unauthenticated
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Firebase init timed out")
                    authState.value = AuthState.Unauthenticated
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase init error: ${e.message}", e)
                    authState.value = AuthState.Unauthenticated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth init exception: ${e.message}", e)
            authState.value = AuthState.Unauthenticated
        }
    }

    when {
        authState.value is AuthState.Initializing -> {
            InitSkeleton(null)
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
            Column(modifier = Modifier.fillMaxSize()) {
                AuthSessionBanner(onLoginClick = { currentScreen.value = "login" })
                Box(modifier = Modifier.weight(1f)) {
                    EmergencyScreen(
                        userId = localState.firebaseUserId,
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
                                onLanguageClick = { currentScreen.value = "language" }
                            )
                        }
                        "qr" -> {
                            Log.d(TAG, "Rendering QRScreen")
                            QRScreen(
                                userId = userId,
                                onBackClick = { currentScreen.value = "emergency" }
                            )
                        }
                        "edit" -> {
                            Log.d(TAG, "Rendering EditProfileScreen")
                            EditProfileScreen(
                                userId = userId,
                                onBackClick = { currentScreen.value = "emergency" },
                                onSaveSuccess = { currentScreen.value = "emergency" }
                            )
                        }
                        "language" -> {
                            Log.d(TAG, "Rendering LanguageSelectionScreen")
                            LanguageSelectionScreen(
                                onLanguageSelected = { currentScreen.value = "emergency" },
                                onBackClick = { currentScreen.value = "emergency" }
                            )
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
