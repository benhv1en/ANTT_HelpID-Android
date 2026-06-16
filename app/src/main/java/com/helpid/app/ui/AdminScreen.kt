package com.helpid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpid.app.R
import com.helpid.app.data.AdminApiResult
import com.helpid.app.data.AdminStats
import com.helpid.app.data.AdminUserItem
import com.helpid.app.data.AdminUsersPage
import com.helpid.app.data.AuthTokenStore
import com.helpid.app.data.HelpIdApiAdminRepository
import com.helpid.app.ui.components.PrimaryButton
import com.helpid.app.ui.components.ScreenHeader
import com.helpid.app.ui.components.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private const val ADMIN_PAGE_SIZE = 20

@Composable
fun AdminScreen(
    onBackClick: () -> Unit,
    onUnauthorized: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { HelpIdApiAdminRepository(context) }
    val currentUserId = remember { AuthTokenStore(context).getUserId().orEmpty() }
    val scope = rememberCoroutineScope()

    val selectedTab = remember { mutableStateOf(0) }

    val stats = remember { mutableStateOf<AdminStats?>(null) }
    val isLoadingStats = remember { mutableStateOf(true) }
    val statsErrorMessage = remember { mutableStateOf<String?>(null) }

    val usersPage = remember { mutableStateOf<AdminUsersPage?>(null) }
    val isLoadingUsers = remember { mutableStateOf(false) }
    val usersErrorMessage = remember { mutableStateOf<String?>(null) }
    val currentPage = remember { mutableStateOf(1) }

    val actionMessage = remember { mutableStateOf<String?>(null) }
    val busyUserId = remember { mutableStateOf<String?>(null) }

    fun loadStats() {
        scope.launch {
            isLoadingStats.value = true
            statsErrorMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) { repository.getStats() }
                when (result) {
                    is AdminApiResult.Ok -> stats.value = result.value
                    AdminApiResult.Forbidden -> {
                        statsErrorMessage.value =
                            context.getString(R.string.admin_error_unauthorized)
                        onUnauthorized()
                    }
                    else ->
                        statsErrorMessage.value = context.getString(R.string.admin_error_network)
                }
            } finally {
                isLoadingStats.value = false
            }
        }
    }

    fun loadUsers(page: Int) {
        scope.launch {
            isLoadingUsers.value = true
            usersErrorMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getUsers(page, ADMIN_PAGE_SIZE)
                }
                when (result) {
                    is AdminApiResult.Ok -> {
                        usersPage.value = result.value
                        currentPage.value = page
                    }
                    AdminApiResult.Forbidden -> {
                        usersErrorMessage.value =
                            context.getString(R.string.admin_error_unauthorized)
                        onUnauthorized()
                    }
                    else ->
                        usersErrorMessage.value = context.getString(R.string.admin_error_network)
                }
            } finally {
                isLoadingUsers.value = false
            }
        }
    }

    fun onAssignAdmin(userId: String) {
        busyUserId.value = userId
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.assignRole(userId, "role_admin")
                }
                when (result) {
                    is AdminApiResult.Ok -> {
                        actionMessage.value = context.getString(R.string.admin_action_success)
                        loadUsers(currentPage.value)
                    }
                    AdminApiResult.Forbidden -> {
                        actionMessage.value =
                            context.getString(R.string.admin_error_unauthorized)
                        onUnauthorized()
                    }
                    else ->
                        actionMessage.value = context.getString(R.string.admin_error_network)
                }
            } finally {
                busyUserId.value = null
            }
        }
    }

    fun onRevokeAdmin(userId: String) {
        if (userId == currentUserId) {
            actionMessage.value = context.getString(R.string.admin_self_revoke_blocked)
            return
        }
        busyUserId.value = userId
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.revokeRole(userId, "role_admin")
                }
                when (result) {
                    is AdminApiResult.Ok -> {
                        actionMessage.value = context.getString(R.string.admin_action_success)
                        loadUsers(currentPage.value)
                    }
                    AdminApiResult.Forbidden -> {
                        actionMessage.value =
                            context.getString(R.string.admin_error_unauthorized)
                        onUnauthorized()
                    }
                    else ->
                        actionMessage.value = context.getString(R.string.admin_error_network)
                }
            } finally {
                busyUserId.value = null
            }
        }
    }

    LaunchedEffect(Unit) { loadStats() }

    LaunchedEffect(selectedTab.value) {
        if (selectedTab.value == 1 && usersPage.value == null && !isLoadingUsers.value) {
            loadUsers(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = stringResource(R.string.admin_screen_title),
            onBackClick = onBackClick
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminTabChip(
                text = stringResource(R.string.admin_tab_dashboard),
                selected = selectedTab.value == 0,
                onClick = { selectedTab.value = 0 },
                modifier = Modifier.weight(1f)
            )
            AdminTabChip(
                text = stringResource(R.string.admin_tab_users),
                selected = selectedTab.value == 1,
                onClick = { selectedTab.value = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        when (selectedTab.value) {
            0 -> AdminDashboardContent(
                stats = stats.value,
                isLoading = isLoadingStats.value,
                errorMessage = statsErrorMessage.value,
                onRetry = { loadStats() }
            )
            1 -> AdminUsersContent(
                page = usersPage.value,
                isLoading = isLoadingUsers.value,
                errorMessage = usersErrorMessage.value,
                currentPage = currentPage.value,
                currentUserId = currentUserId,
                busyUserId = busyUserId.value,
                actionMessage = actionMessage.value,
                onPrevPage = { loadUsers(currentPage.value - 1) },
                onNextPage = { loadUsers(currentPage.value + 1) },
                onRetry = { loadUsers(currentPage.value) },
                onAssignAdmin = { onAssignAdmin(it) },
                onRevokeAdmin = { onRevokeAdmin(it) },
                onDismissMessage = { actionMessage.value = null }
            )
        }
    }
}

// ── Tab chip ──────────────────────────────────────────────────────────────────

@Composable
private fun AdminTabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Dashboard tab ─────────────────────────────────────────────────────────────

@Composable
private fun AdminDashboardContent(
    stats: AdminStats?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        when {
            isLoading -> repeat(4) { AdminLoadingCard() }
            errorMessage != null -> AdminErrorState(message = errorMessage, onRetry = onRetry)
            stats != null -> {
                AdminStatCard(
                    label = stringResource(R.string.admin_stat_total_users),
                    value = stats.totalUsers
                )
                AdminStatCard(
                    label = stringResource(R.string.admin_stat_total_profiles),
                    value = stats.totalProfiles
                )
                AdminStatCard(
                    label = stringResource(R.string.admin_stat_total_links),
                    value = stats.totalPublicLinks
                )
                AdminStatCard(
                    label = stringResource(R.string.admin_stat_audit_7d),
                    value = stats.auditEventsLast7Days
                )
            }
            else -> AdminErrorState(
                message = stringResource(R.string.admin_error_network),
                onRetry = onRetry
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AdminStatCard(label: String, value: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Users tab ─────────────────────────────────────────────────────────────────

@Composable
private fun AdminUsersContent(
    page: AdminUsersPage?,
    isLoading: Boolean,
    errorMessage: String?,
    currentPage: Int,
    currentUserId: String,
    busyUserId: String?,
    actionMessage: String?,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onRetry: () -> Unit,
    onAssignAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onDismissMessage: () -> Unit
) {
    val totalPages = if (page != null && page.pageSize > 0)
        ceil(page.totalCount.toDouble() / page.pageSize).toInt().coerceAtLeast(1)
    else 1

    Column(modifier = Modifier.fillMaxSize()) {

        if (actionMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                val dismissLabel = stringResource(R.string.admin_dismiss)
                TextButton(
                    onClick = onDismissMessage,
                    modifier = Modifier.semantics { contentDescription = dismissLabel }
                ) {
                    Text("✕", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        when {
            isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                repeat(5) { AdminLoadingCard() }
            }

            errorMessage != null -> AdminErrorState(
                message = errorMessage,
                onRetry = onRetry
            )

            page == null || page.users.isEmpty() -> AdminEmptyState()

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(page.users, key = { it.userId }) { user ->
                        AdminUserRow(
                            user = user,
                            isSelf = user.userId == currentUserId,
                            isBusy = busyUserId == user.userId,
                            onAssign = { onAssignAdmin(user.userId) },
                            onRevoke = { onRevokeAdmin(user.userId) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.admin_prev_page),
                        onClick = onPrevPage,
                        enabled = currentPage > 1,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = stringResource(R.string.admin_page_of, currentPage, totalPages),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SecondaryButton(
                        text = stringResource(R.string.admin_next_page),
                        onClick = onNextPage,
                        enabled = currentPage < totalPages,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminUserRow(
    user: AdminUserItem,
    isSelf: Boolean,
    isBusy: Boolean,
    onAssign: () -> Unit,
    onRevoke: () -> Unit
) {
    val isAdminRole = user.roles.any { it.equals("Admin", ignoreCase = true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = user.email,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.admin_user_role_label) + ": " +
                           user.roles.joinToString(", ").ifEmpty { stringResource(R.string.admin_role_empty) },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                val statusText = if (user.isLocked) stringResource(R.string.admin_user_locked)
                                 else stringResource(R.string.admin_user_active)
                val statusColor = if (user.isLocked) MaterialTheme.colorScheme.error
                                  else Color(0xFF2E7D32)
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (user.createdAtUtc.isNotBlank()) {
                Text(
                    text = user.createdAtUtc.take(10),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val assignCd = stringResource(R.string.admin_cd_assign_role, user.email)
            val revokeCd = stringResource(R.string.admin_cd_revoke_role, user.email)
            when {
                isBusy -> Text(
                    text = stringResource(R.string.admin_action_loading),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                isAdminRole -> SecondaryButton(
                    text = stringResource(R.string.admin_revoke_admin_role),
                    onClick = onRevoke,
                    enabled = !isSelf,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = revokeCd }
                )
                else -> PrimaryButton(
                    text = stringResource(R.string.admin_assign_admin_role),
                    onClick = onAssign,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = assignCd }
                )
            }
        }
    }
}

// ── Shared states ─────────────────────────────────────────────────────────────

@Composable
private fun AdminLoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {}
}

@Composable
private fun AdminErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        SecondaryButton(text = stringResource(R.string.retry), onClick = onRetry)
    }
}

@Composable
private fun AdminEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.admin_no_users),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
