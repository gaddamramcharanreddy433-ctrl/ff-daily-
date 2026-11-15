package com.ffdaily.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

const val MIN_PLAYERS = 8

@Composable
fun MatchesScreen() {
    var matches by remember { mutableStateOf(listOf<MatchDoc>()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { Repo.ensureTodayMatches() } catch (_: Exception) { }
    }

    DisposableEffect(Unit) {
        val reg = Repo.db.collection("matches").whereEqualTo("date", todayKey())
            .addSnapshotListener { s, _ ->
                matches = s?.documents
                    ?.mapNotNull { d -> d.toObject(MatchDoc::class.java)?.copy(id = d.id) }
                    ?.sortedBy { it.slotIndex } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Today's Solo matches · Entry $ENTRY_FEE coins", style = MaterialTheme.typography.bodyMedium)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(matches, key = { it.id }) { m ->
            MatchCard(m) { message = it }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MatchCard(m: MatchDoc, onMessage: (String?) -> Unit) {
    var entry by remember(m.id) { mutableStateOf<EntryDoc?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(m.id) {
        val reg = Repo.db.collection("matches").document(m.id)
            .collection("entries").document(Repo.uid!!)
            .addSnapshotListener { s, _ -> entry = s?.toObject(EntryDoc::class.java) }
        onDispose { reg.remove() }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(m.slot, style = MaterialTheme.typography.titleMedium)
                Text("${m.joined}/$MAX_PLAYERS players")
            }

            if (m.joined < MIN_PLAYERS) {
                Text(
                    "Needs ${MIN_PLAYERS - m.joined} more players to start",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val e = entry
            if (e == null) {
                Button(
                    onClick = {
                        busy = true
                        onMessage(null)
                        scope.launch {
                            try { Repo.joinMatch(m.id) }
                            catch (ex: Exception) { onMessage(ex.message) }
                            finally { busy = false }
                        }
                    },
                    enabled = !busy && m.joined < MAX_PLAYERS,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (m.joined >= MAX_PLAYERS) "Full" else "Join ($ENTRY_FEE coins)") }
            } else {
                Text("✅ You joined", color = MaterialTheme.colorScheme.primary)
                if (m.roomId.isNotBlank()) {
                    Text("Room ID: ${m.roomId}")
                    Text("Password: ${m.roomPass}")
                } else {
                    Text("Room ID and password will appear here before the match.")
                }
                if (e.awarded) {
                    Text("Result: ${e.kills} kills" + (if (e.booyah) " + Booyah" else "") +
                        " · Prize ${e.prize} coins")
                }
            }
        }
    }
}
