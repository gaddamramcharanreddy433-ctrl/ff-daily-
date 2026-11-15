package com.ffdaily.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
        item { Spacer(Mod
