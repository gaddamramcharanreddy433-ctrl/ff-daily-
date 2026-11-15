package com.ffdaily.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(user: UserDoc) {
    var name by remember(user.name) { mutableStateOf(user.name) }
    var gameName by remember(user.gameName) { mutableStateOf(user.gameName) }
    var upi by remember(user.upi) { mutableStateOf(user.upi) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mobile: ${user.mobile}", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Your name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = gameName, onValueChange = { gameName = it },
            label = { Text("Free Fire in-game name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = upi, onValueChange = { upi = it.trim() },
            label = { Text("Your UPI ID (for withdrawals)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Button(
            onClick = {
                busy = true; msg = null
                scope.launch {
                    try {
                        Repo.saveProfile(name, gameName, upi)
                        msg = "Saved"
                    } catch (e: Exception) { msg = e.message }
                    finally { busy = false }
                }
            },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        OutlinedButton(
            onClick = { Repo.logout() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Logout") }
    }
}
