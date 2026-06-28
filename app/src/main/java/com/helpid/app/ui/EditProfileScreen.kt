package com.helpid.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.NumberParseException
import com.helpid.app.R
import com.helpid.app.data.BiometricPreferenceStore
import com.helpid.app.data.EmergencyContactData
import com.helpid.app.data.HelpIdApiProfileRepository
import com.helpid.app.data.UserProfile
import com.helpid.app.ui.theme.HelpIDTheme
import com.helpid.app.utils.BiometricAvailability
import com.helpid.app.utils.BiometricPromptError
import com.helpid.app.utils.BiometricUtils
import com.helpid.app.utils.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.helpid.app.ui.components.ShimmerPlaceholder
import com.helpid.app.ui.components.SkeletonSpacer
import com.helpid.app.ui.components.SkeletonTextLine
import com.helpid.app.ui.components.GhostButton
import com.helpid.app.ui.components.PrimaryButton
import com.helpid.app.ui.components.ScreenHeader
import com.helpid.app.ui.components.SecondaryButton
import com.helpid.app.ui.components.SecureScreenWrapper
import javax.net.ssl.SSLHandshakeException

@Composable
fun EditProfileScreen(
    userId: String,
    onBackClick: () -> Unit = {},
    onSaveSuccess: () -> Unit = {},
    onLogout: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { HelpIdApiProfileRepository(context) }
    val biometricStore = remember { BiometricPreferenceStore(context) }
    val scope = rememberCoroutineScope()
    
    val profile = remember { mutableStateOf<UserProfile?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val isSaving = remember { mutableStateOf(false) }
    val saveError = remember { mutableStateOf<String?>(null) }
    val mitmError = remember { mutableStateOf(false) }
    val showLogoutDialog = remember { mutableStateOf(false) }
    val showDisableBiometricDialog = remember { mutableStateOf(false) }
    val biometricEnabled = remember { mutableStateOf(false) }
    val biometricStatusMessage = remember { mutableStateOf<Int?>(null) }
    
    val name = remember { mutableStateOf("") }
    val bloodGroup = remember { mutableStateOf("") }
    val address = remember { mutableStateOf("") }
    val allergies = remember { mutableStateOf("") }
    val medicalNotes = remember { mutableStateOf("") }
    val emergencyContacts = remember { mutableStateListOf<EmergencyContactData>() }

    fun ensureMinContacts() {
        if (emergencyContacts.isEmpty()) {
            emergencyContacts.add(EmergencyContactData())
            emergencyContacts.add(EmergencyContactData())
        } else if (emergencyContacts.size == 1) {
            emergencyContacts.add(EmergencyContactData())
        }
    }

    fun resolvePickedContact(uri: Uri): EmergencyContactData? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = resolver.query(uri, projection, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val pickedName = if (nameIdx >= 0) it.getString(nameIdx) else ""
            val pickedNumber = if (numberIdx >= 0) it.getString(numberIdx) else ""
            if (pickedName.isBlank() && pickedNumber.isBlank()) return null
            return EmergencyContactData(name = pickedName ?: "", phone = pickedNumber ?: "")
        }
    }

    val bloodGroupOptions = remember {
        setOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    }

    fun sanitizeNameInput(input: String): String {
        return input.filter { it.isLetter() || it == ' ' }.take(40)
    }

    fun sanitizeBloodGroupInput(input: String): String {
        val filtered = input.filter { it.isLetter() || it == '+' || it == '-' }
        return filtered.uppercase().take(3)
    }

    fun sanitizePhoneInternational(input: String): String {
        val trimmed = input.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        val normalized = (if (hasPlus) "+" else "") + digits
        return if (normalized.length > 16) normalized.take(16) else normalized
    }

    val phoneUtil = remember { PhoneNumberUtil.getInstance() }
    val defaultRegion = remember { java.util.Locale.getDefault().country.ifBlank { "US" } }

    fun toE164OrNull(input: String): String? {
        val sanitized = sanitizePhoneInternational(input)
        if (sanitized.isBlank()) return null
        if (!sanitized.startsWith("+")) return null
        return try {
            val number = phoneUtil.parse(sanitized, defaultRegion)
            if (!phoneUtil.isValidNumber(number)) return null
            phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            null
        }
    }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val contact = resolvePickedContact(uri) ?: return@rememberLauncherForActivityResult
        val phoneSanitized = sanitizePhoneInternational(contact.phone)
        val phoneNormalized = toE164OrNull(phoneSanitized) ?: phoneSanitized
        emergencyContacts.add(
            contact.copy(
                name = sanitizeNameInput(contact.name),
                phone = phoneNormalized
            )
        )
    }

    fun isValidName(value: String): Boolean {
        val v = value.trim()
        return v.isNotEmpty() && v.all { it.isLetter() || it == ' ' }
    }

    fun isValidBloodGroup(value: String): Boolean {
        val v = value.trim().uppercase()
        return v in bloodGroupOptions
    }

    fun isValidPhoneInternational(value: String): Boolean {
        return toE164OrNull(value) != null
    }

    val isFormValid by remember {
        derivedStateOf {
            val profileNameValid = isValidName(name.value)
            val bloodValid = isValidBloodGroup(bloodGroup.value)

            val contactsValid = emergencyContacts
                .filter { it.name.isNotBlank() || it.phone.isNotBlank() }
                .all { contact ->
                    isValidName(contact.name) && isValidPhoneInternational(contact.phone)
                }

            profileNameValid && bloodValid && contactsValid
        }
    }

    // Load profile on first launch
    LaunchedEffect(userId) {
        try {
            withContext(Dispatchers.IO) {
                val loadedProfile = repository.getProfile()
                profile.value = loadedProfile

                name.value = loadedProfile.name
                bloodGroup.value = loadedProfile.bloodGroup
                address.value = loadedProfile.address
                allergies.value = loadedProfile.allergies.joinToString("\n")
                medicalNotes.value = loadedProfile.medicalNotes.joinToString("\n")

                emergencyContacts.clear()
                emergencyContacts.addAll(loadedProfile.emergencyContacts)
                ensureMinContacts()
            }
        } catch (e: SSLHandshakeException) {
            mitmError.value = true
        } catch (_: Exception) {
            // generic load failure — Room cache fallback already handled in repository
        } finally {
            isLoading.value = false
        }
    }

    LaunchedEffect(userId) {
        biometricEnabled.value = biometricStore.isEnabledForUser(userId)
        biometricStatusMessage.value = null
    }

    fun biometricAvailabilityMessage(availability: BiometricAvailability): Int {
        return when (availability) {
            BiometricAvailability.NoneEnrolled -> R.string.biometric_not_enrolled
            BiometricAvailability.NoHardware,
            BiometricAvailability.HardwareUnavailable,
            BiometricAvailability.SecurityUpdateRequired,
            BiometricAvailability.Unsupported -> R.string.biometric_not_available
            BiometricAvailability.Unknown -> R.string.biometric_system_error
            BiometricAvailability.Available -> R.string.biometric_enabled
        }
    }

    fun requestEnableBiometric() {
        val availability = BiometricUtils.getAvailability(context, allowDeviceCredential = true)
        if (availability != BiometricAvailability.Available) {
            biometricStatusMessage.value = biometricAvailabilityMessage(availability)
            biometricEnabled.value = false
            biometricStore.setEnabledForUser(userId, false)
            return
        }

        val activity = context.findFragmentActivity()
        if (activity == null) {
            biometricStatusMessage.value = R.string.biometric_system_error
            biometricEnabled.value = false
            return
        }

        BiometricUtils.showBiometricPrompt(
            activity = activity,
            executor = ContextCompat.getMainExecutor(context),
            onSuccess = {
                biometricStore.setEnabledForUser(userId, true)
                biometricStore.markUnlockedForUser(userId)
                biometricEnabled.value = true
                biometricStatusMessage.value = R.string.biometric_enabled
            },
            onError = { failure ->
                biometricEnabled.value = false
                biometricStatusMessage.value = when (failure.error) {
                    BiometricPromptError.Canceled -> R.string.biometric_canceled
                    else -> failure.messageResId
                }
            },
            allowDeviceCredential = true
        )
    }

    fun disableBiometric() {
        biometricStore.setEnabledForUser(userId, false)
        biometricEnabled.value = false
        biometricStatusMessage.value = R.string.biometric_disabled
    }

    SecureScreenWrapper {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(
            title = stringResource(R.string.edit_profile),
            subtitle = stringResource(R.string.update_emergency_information),
            onBackClick = onBackClick
        )

        if (mitmError.value) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.error_mitm_detected),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        if (isLoading.value) {
            Spacer(modifier = Modifier.height(16.dp))
            EditProfileSkeleton()
        } else {
            Spacer(modifier = Modifier.height(16.dp))

            // Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_personal_info),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A),
                        letterSpacing = 0.2.sp
                    )

                    // Name Field
                    val nameError = !isValidName(name.value)
                    FormField(
                        label = stringResource(R.string.full_name),
                        value = name.value,
                        isError = nameError,
                        supportingText = if (nameError) stringResource(R.string.validation_valid_name) else null,
                        onValueChange = { name.value = sanitizeNameInput(it) }
                    )

                    val bloodError = !isValidBloodGroup(bloodGroup.value)
                    FormField(
                        label = stringResource(R.string.blood_group),
                        value = bloodGroup.value,
                        isError = bloodError,
                        supportingText = if (bloodError) stringResource(R.string.validation_blood_group) else null,
                        onValueChange = { bloodGroup.value = sanitizeBloodGroupInput(it) }
                    )

                    FormField(
                        label = stringResource(R.string.address),
                        value = address.value,
                        onValueChange = { address.value = it.take(120) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.section_medical_info),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A),
                        letterSpacing = 0.2.sp
                    )

                    Text(
                        text = stringResource(R.string.allergies),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.3.sp
                    )
                    TextField(
                        value = allergies.value,
                        onValueChange = { allergies.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        placeholder = { Text(stringResource(R.string.one_per_line)) },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text(
                        text = stringResource(R.string.medical_conditions),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.3.sp
                    )
                    TextField(
                        value = medicalNotes.value,
                        onValueChange = { medicalNotes.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        placeholder = { Text(stringResource(R.string.one_per_line)) },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text(
                        text = stringResource(R.string.section_contacts),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A),
                        letterSpacing = 0.2.sp
                    )

                    // Emergency Contacts
                    Text(
                        text = stringResource(R.string.emergency_contacts),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.3.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        emergencyContacts.forEachIndexed { index, contact ->
                            ContactEditor(
                                index = index,
                                name = contact.name,
                                phone = contact.phone,
                                onNameChange = { newName ->
                                    emergencyContacts[index] = emergencyContacts[index].copy(name = sanitizeNameInput(newName))
                                },
                                onPhoneChange = { newPhone ->
                                    val sanitized = sanitizePhoneInternational(newPhone)
                                    val normalized = toE164OrNull(sanitized) ?: sanitized
                                    emergencyContacts[index] = emergencyContacts[index].copy(phone = normalized)
                                },
                                onRemove = {
                                    if (emergencyContacts.size > 1) {
                                        emergencyContacts.removeAt(index)
                                        ensureMinContacts()
                                    }
                                },
                                canRemove = emergencyContacts.size > 2,
                                isPhoneInvalid = (contact.phone.isNotBlank() && !isValidPhoneInternational(contact.phone)) ||
                                    ((contact.name.isNotBlank() || contact.phone.isNotBlank()) && !isValidName(contact.name))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = stringResource(R.string.add_contact),
                            onClick = {
                                emergencyContacts.add(EmergencyContactData())
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = stringResource(R.string.pick_contact),
                            onClick = { pickContactLauncher.launch(null) },
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BiometricSettingsCard(
                enabled = biometricEnabled.value,
                statusMessageRes = biometricStatusMessage.value,
                onToggle = { checked ->
                    if (checked) {
                        requestEnableBiometric()
                    } else {
                        showDisableBiometricDialog.value = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (saveError.value != null) {
                    Text(
                        text = saveError.value!!,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Save Button
                PrimaryButton(
                    text = if (isSaving.value) stringResource(R.string.saving) else stringResource(R.string.save),
                    onClick = {
                        isSaving.value = true
                        saveError.value = null

                        val updatedProfile = UserProfile(
                            userId = userId,
                            name = name.value,
                            bloodGroup = bloodGroup.value,
                            address = address.value.trim(),
                            allergies = allergies.value
                                .replace(",", "\n")
                                .split("\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() },
                            medicalNotes = medicalNotes.value.split("\n").filter { it.isNotBlank() },
                            emergencyContacts = emergencyContacts
                                .filter { it.name.isNotBlank() && it.phone.isNotBlank() },
                            language = LanguageManager.getSelectedLanguage(context).code
                        )

                        scope.launch {
                            try {
                                val success = withContext(Dispatchers.IO) {
                                    repository.updateProfile(updatedProfile)
                                }
                                isSaving.value = false
                                if (success) {
                                    onSaveSuccess()
                                    onBackClick()
                                } else {
                                    saveError.value = context.getString(R.string.save_error)
                                }
                            } catch (_: Exception) {
                                isSaving.value = false
                                saveError.value = context.getString(R.string.save_error)
                            }
                        }
                    },
                    enabled = !isSaving.value && isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                )

                // Cancel Button
                GhostButton(
                    text = stringResource(R.string.cancel),
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(40.dp)
                        .align(Alignment.CenterHorizontally),
                    enabled = !isSaving.value
                )

                if (onLogout != null) {
                    GhostButton(
                        text = stringResource(R.string.auth_logout),
                        onClick = { showLogoutDialog.value = true },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(40.dp)
                            .align(Alignment.CenterHorizontally),
                        enabled = !isSaving.value
                    )
                }
            }

            if (showDisableBiometricDialog.value) {
                AlertDialog(
                    onDismissRequest = { showDisableBiometricDialog.value = false },
                    title = { Text(stringResource(R.string.biometric_disable_confirm_title)) },
                    text = { Text(stringResource(R.string.biometric_disable_confirm_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDisableBiometricDialog.value = false
                            disableBiometric()
                        }) {
                            Text(
                                text = stringResource(R.string.biometric_turn_off),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDisableBiometricDialog.value = false }) {
                            Text(stringResource(R.string.biometric_keep_on))
                        }
                    }
                )
            }

            if (showLogoutDialog.value && onLogout != null) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog.value = false },
                    title = { Text(stringResource(R.string.auth_logout_confirm_title)) },
                    text = { Text(stringResource(R.string.auth_logout_confirm_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showLogoutDialog.value = false
                            onLogout()
                        }) {
                            Text(
                                text = stringResource(R.string.auth_logout_confirm_yes),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog.value = false }) {
                            Text(stringResource(R.string.auth_logout_confirm_no))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
}

@Composable
private fun BiometricSettingsCard(
    enabled: Boolean,
    statusMessageRes: Int?,
    onToggle: (Boolean) -> Unit
) {
    val switchLabel = stringResource(R.string.biometric_settings_title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.biometric_settings_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.biometric_settings_body),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                if (statusMessageRes != null) {
                    Text(
                        text = stringResource(statusMessageRes),
                        fontSize = 12.sp,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = switchLabel }
            )
        }
    }
}

@Composable
private fun EditProfileSkeleton() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SkeletonTextLine(widthFraction = 0.4f, height = 10.dp)
            SkeletonSpacer(10.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                cornerRadius = 8.dp
            )
            SkeletonSpacer(12.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                cornerRadius = 8.dp
            )
            SkeletonSpacer(12.dp)
            SkeletonTextLine(widthFraction = 0.5f, height = 10.dp)
            SkeletonSpacer(8.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                cornerRadius = 8.dp
            )
            SkeletonSpacer(16.dp)
            SkeletonTextLine(widthFraction = 0.6f, height = 10.dp)
            SkeletonSpacer(8.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                cornerRadius = 8.dp
            )
            SkeletonSpacer(8.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                cornerRadius = 8.dp
            )
            SkeletonSpacer(12.dp)
            SkeletonTextLine(widthFraction = 0.6f, height = 10.dp)
            SkeletonSpacer(8.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                cornerRadius = 8.dp
            )
            SkeletonSpacer(8.dp)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                cornerRadius = 8.dp
            )
        }
    }

    SkeletonSpacer(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            cornerRadius = 10.dp
        )
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(40.dp),
            cornerRadius = 8.dp
        )
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    supportingText: String? = null
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.3.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                errorIndicatorColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )

        if (supportingText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = supportingText,
                fontSize = 11.sp,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactEditor(
    index: Int,
    name: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    isPhoneInvalid: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 0.dp, shape = RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.contact_number, index + 1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.cd_remove_contact),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val nameError = (name.isBlank() && phone.isNotBlank())
            FormField(
                label = stringResource(R.string.full_name),
                value = name,
                isError = nameError,
                supportingText = if (nameError) stringResource(R.string.validation_required) else null,
                onValueChange = onNameChange
            )

            val phoneError = isPhoneInvalid
            FormField(
                label = stringResource(R.string.phone),
                value = phone,
                isError = phoneError,
                supportingText = if (phoneError) stringResource(R.string.validation_phone_international) else null,
                onValueChange = onPhoneChange
            )
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
