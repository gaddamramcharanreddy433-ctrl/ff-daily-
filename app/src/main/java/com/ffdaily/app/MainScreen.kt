package com.ffdaily.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen() {
    var tab by remember { mutableStateOf(0) }
    var isAdmin by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf(UserDoc()) }
    var config by remember { mutableStateOf(AppConfig()) }

    LaunchedEffect(Unit) {
        isAdmin = try { Repo.isAdmin() } catch (e: Exception) { false }
    }

    DisposableEffect(Unit) {
        val r1 = Repo.db.collection("users").document(Repo.uid!!)
            .addSnapshotListener { s, _ -> s?.toObject(UserDoc::class.java)?.let { user = it } }
        val r2 = Repo.db.collection("config").document("app")
            .addSnapshotListener { s, _ -> config = s?.toObject(AppConfig::class.java) ?: AppConfig() }
        onDispose { r1.remove(); r2.remove() }
    }

    val tabs = buildList {
        add("🎮" to "Matches")
        add("💰" to "Wallet")
        add("👤" to "Profile")
        if (isAdmin) add("🛠" to "Admin")
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(t.first) },
                        label = { Text(t.second) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Coins: ${user.coins}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            when (tabs.getOrNull(tab)?.second) {
                "Matches" -> MatchesScreen()
                "Wallet" -> WalletScreen(user, config)
                "Profile" -> ProfileScreen(user)
                "Admin" -> AdminScreen(config)
            }
        }
    }
}
