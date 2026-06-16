---
name: project-helpid-android
description: HelpID Android — auth/biometric architecture, key files, current implementation state
metadata:
  type: project
---

Repo chứa 3 components độc lập: Android app (Kotlin/Compose), backend ASP.NET Core, web React/Vite.

## Biometric authentication — trạng thái hiện tại (16/06/2026)

**Đã hoàn thành:**
- `BiometricUtils` (utils/BiometricManager.kt): wrapper `BiometricPrompt`, `getAvailability`, `showBiometricPrompt`, `availabilityFromStatus`, `errorFromCode`
- `BiometricPreferenceStore` (data/BiometricPreferenceStore.kt): lưu enabled/lastUnlocked theo user ID đã hash SHA-256 bằng `SecurePrefs`
- `BiometricAuthDecision.kt`: hàm thuần `resolveAuthState(userId, isBiometricEnabled, requiresRefresh)` → `AuthState`
- `EditProfileScreen.kt`: toggle bật/tắt biometric, dialog xác nhận tắt, error messages locale-aware
- `MainActivity.kt`: `AuthState.BiometricLocked(userId, requiresRefresh)`, `BiometricLockScreen` composable, `refreshAfterBiometricUnlock` xử lý 401/403, logout clear token + biometric setting

**Luồng mở app:**
1. `hasValidSession()` → BiometricLocked(requiresRefresh=false) hoặc Authenticated
2. Refresh token tồn tại + biometric enabled → BiometricLocked(requiresRefresh=true) luôn
3. Biometric success: nếu requiresRefresh=false → Authenticated trực tiếp; nếu true → refresh backend → 401/403 clear tokens+biometric, NetworkError → LocalCacheOnly(isOffline=true)

**Unit tests (59 tests, 0 failures):**
- `BiometricAuthDecisionTest`: 11 tests toàn bộ nhánh resolveAuthState
- `BiometricPreferenceStoreTest`: 9 tests (SHA256, key isolation user A≠B)
- `BiometricUtilsTest`: 8 tests (availability + error code mapping)
- `HelpIdApiAuthRepositoryTest`: 28 tests
- `EmergencyNumberResolverTest`: 3 tests

**Checklist test thủ công còn pending:** TC-M01 đến TC-M17 trong `passed-testcases.md` (cần emulator/device).

## Auth state machine

`AuthState` sealed class (MainActivity.kt:85): Initializing, Authenticated(userId), BiometricLocked(userId, requiresRefresh), LocalCacheOnly(userId, isOffline), Unauthenticated.

Navigation là state machine thủ công (`currentScreen: MutableState<String>`) — không dùng Navigation Compose.

## Key files

- `MainActivity.kt`: entry, AppNavigation, BiometricLockScreen, HelpIdBottomBar
- `BiometricAuthDecision.kt`: pure function resolveAuthState
- `data/AuthTokenStore.kt`: EncryptedSharedPrefs cho access/refresh token
- `data/HelpIdApiAuthRepository.kt`: gọi /api/v1/auth/* endpoints
- `data/BiometricPreferenceStore.kt`: prefs biometric theo user
- `utils/BiometricManager.kt`: wrapper BiometricPrompt + enums
- `ui/EditProfileScreen.kt`: toggle biometric settings

**Why:** Backend JWT/refresh token là nguồn xác thực API. Biometric chỉ là local UI gate, không thay thế backend auth.
