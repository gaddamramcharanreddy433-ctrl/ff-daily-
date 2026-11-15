package com.ffdaily.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen() {
    var user by remember { mutableStateOf(UserDoc()) }
    var config by remember { mutableStateOf(AppConfig()) }
    var isAdmin by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try { isAdmin = Repo.isAdmin() } catch (_: Exception) { }
    }

    DisposableEffect(Unit) {
        val id = Repo.uid
        if (id == null) {
            onDispose { }
        } else {
            val r1 = Repo.db.collection("users").document(id)
                .addSnapshotListener { s, _ ->
                    s?.toObject(UserDoc::class.java)?.let { user = it }
                }
            val r2 = Repo.db.collection("config").document("app")
                .addSnapshotListener { s, _ ->
                    config = s?.toObject(AppConfig::class.java) ?: AppConfig()
                }
            onDispose { r1.remove(); r2.remove() }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🎮") }, label = { Text("Matches") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("💰") }, label = { Text("Wallet") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("👤") }, label = { Text("Profile") }
                )
                if (isAdmin) {
                    NavigationBarItem(
                        selected = tab == 3, onClick = { tab = 3 },
                        icon = { Text("🛠") }, label = { Text("Admin") }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "FF Daily · ${user.coins} coins",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> MatchesScreen()
                    1 -> WalletScreen(user, config)
                    2 -> ProfileScreen(user)
                    else -> AdminScreen(config)
                }
            }
        }
    }
}
