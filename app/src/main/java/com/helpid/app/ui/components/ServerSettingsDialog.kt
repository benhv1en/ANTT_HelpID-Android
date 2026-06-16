package com.helpid.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpid.app.R
import com.helpid.app.data.HelpIdApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ServerSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(HelpIdApiConfig.getBaseUrl(context)) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; testResult = null },
                    label = { Text(stringResource(R.string.server_url_label)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                if (testResult != null) {
                    Text(
                        text = testResult!!,
                        fontSize = 13.sp,
                        color = if (testOk) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val (ok, detail) = withContext(Dispatchers.IO) {
                                    try {
                                        val conn = URL("${url.trimEnd('/')}/health")
                                            .openConnection() as HttpURLConnection
                                        conn.connectTimeout = 5_000
                                        conn.readTimeout = 5_000
                                        conn.requestMethod = "GET"
                                        val code = conn.responseCode
                                        conn.disconnect()
                                        Pair(code in 200..299, null as String?)
                                    } catch (e: Exception) {
                                        Pair(false, e.javaClass.simpleName + ": " + (e.message?.take(80) ?: ""))
                                    }
                                }
                                isTesting = false
                                testOk = ok
                                testResult = if (ok) {
                                    context.getString(R.string.server_url_test_ok)
                                } else {
                                    context.getString(R.string.server_url_test_fail) +
                                        if (detail != null) "\n$detail" else ""
                                }
                            }
                        },
                        enabled = !isTesting && url.isNotBlank()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(stringResource(R.string.server_url_test))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                HelpIdApiConfig.setBaseUrl(context, url.trim())
                onDismiss()
            }) {
                Text(stringResource(R.string.server_url_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
