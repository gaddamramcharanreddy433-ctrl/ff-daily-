package com.ffdaily.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AdminScreen(config: AppConfig) {
    var sub by remember { mutableStateOf(0) }
    val names = listOf("Settings", "Payments", "Matches", "Players")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            names.forEachIndexed { i, n ->
                if (sub == i) Button(onClick = { sub = i }) { Text(n) }
                else TextButton(onClick = { sub = i }) { Text(n) }
                Spacer(Modifier.width(4.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (sub) {
                0 -> SettingsSection(config)
                1 -> PaymentsSection()
                2 -> MatchesSection(config)
                else -> PlayersSection()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- Settings
@Composable
private fun SettingsSection(config: AppConfig) {
    var upi by remember(config.adminUpi) { mutableStateOf(config.adminUpi) }
    var rate by remember(config.rupeesPerCoin) { mutableStateOf(config.rupeesPerCoin.toString()) }
    var p1 by remember(config.prizeBooyahTenKills) { mutableStateOf(config.prizeBooyahTenKills.toString()) }
    var p2 by remember(config.prizeBooyah) { mutableStateOf(config.prizeBooyah.toString()) }
    var p3 by remember(config.prizeTenKills) { mutableStateOf(config.prizeTenKills.toString()) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    Text("Settings", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(upi, { upi = it.trim() }, label = { Text("Your UPI ID (players pay here)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(rate, { rate = it }, label = { Text("₹ per coin") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth())
    Text("Prizes (coins)")
    OutlinedTextField(p1, { p1 = it.filter(Char::isDigit) }, label = { Text("Booyah + 10 or more kills") },
        singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(p2, { p2 = it.filter(Char::isDigit) }, label = { Text("Booyah + below 10 kills") },
        singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(p3, { p3 = it.filter(Char::isDigit) }, label = { Text("10 or more kills, no Booyah") },
        singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
    msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Button(
        onClick = {
            msg = null
            scope.launch {
                try {
                    Repo.db.collection("config").document("app").set(
                        mapOf(
                            "adminUpi" to upi,
                            "rupeesPerCoin" to (rate.toDoubleOrNull() ?: 1.0),
                            "prizeBooyahTenKills" to (p1.toLongOrNull() ?: 0L),
                            "prizeBooyah" to (p2.toLongOrNull() ?: 0L),
                            "prizeTenKills" to (p3.toLongOrNull() ?: 0L)
                        )
                    ).await()
                    msg = "Saved"
                } catch (e: Exception) { msg = e.message }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save settings") }
}

// ---------------------------------------------------------------- Payments
@Composable
private fun PaymentsSection() {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var purchases by remember { mutableStateOf(listOf<PurchaseDoc>()) }
    var withdrawals by remember { mutableStateOf(listOf<WithdrawDoc>()) }
    var msg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val r1 = Repo.db.collection("purchases").whereEqualTo("status", "pending")
            .addSnapshotListener { s, _ ->
                purchases = s?.documents?.mapNotNull { d ->
                    d.toObject(PurchaseDoc::class.java)?.copy(id = d.id)
                } ?: emptyList()
            }
        val r2 = Repo.db.collection("withdrawals").whereEqualTo("status", "pending")
            .addSnapshotListener { s, _ ->
                withdrawals = s?.documents?.mapNotNull { d ->
                    d.toObject(WithdrawDoc::class.java)?.copy(id = d.id)
                } ?: emptyList()
            }
        onDispose { r1.remove(); r2.remove() }
    }

    fun run(block: suspend () -> Unit) {
        msg = null
        scope.launch { try { block() } catch (e: Exception) { msg = e.message } }
    }

    msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Text("Coin purchases to approve", style = MaterialTheme.typography.titleLarge)
    if (purchases.isEmpty()) Text("None pending.")
    purchases.forEach { p ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${p.name}: ${p.coins} coins (₹${"%.0f".format(p.amount)})")
                Text("UTR: ${p.utr}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { run { approvePurchase(p) } }) { Text("Approve") }
                    OutlinedButton(onClick = { run { rejectPurchase(p) } }) { Text("Reject") }
                }
            }
        }
    }

    HorizontalDivider()
    Text("Withdrawals to pay", style = MaterialTheme.typography.titleLarge)
    if (withdrawals.isEmpty()) Text("None pending.")
    withdrawals.forEach { w ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${w.name}: ${w.coins} coins = ₹${"%.0f".format(w.amount)}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("UPI: ${w.upi}")
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(w.upi)); msg = "UPI copied"
                    }) { Text("Copy") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { run { markPaid(w) } }) { Text("Mark paid") }
                    OutlinedButton(onClick = { run { rejectWithdraw(w) } }) { Text("Reject + refund") }
                }
            }
        }
    }
}

private suspend fun approvePurchase(p: PurchaseDoc) {
    val pRef = Repo.db.collection("purchases").document(p.id)
    val uRef = Repo.db.collection("users").document(p.uid)
    Repo.db.runTransaction { t ->
        if (t.get(pRef).getString("status") != "pending") throw Exception("Already handled")
        t.update(pRef, "status", "approved")
        t.update(uRef, "coins", FieldValue.increment(p.coins))
        null
    }.await()
}

private suspend fun rejectPurchase(p: PurchaseDoc) {
    Repo.db.collection("purchases").document(p.id).update("status", "rejected").await()
}

private suspend fun markPaid(w: WithdrawDoc) {
    Repo.db.collection("withdrawals").document(w.id).update("status", "paid").await()
}

private suspend fun rejectWithdraw(w: WithdrawDoc) {
    val wRef = Repo.db.collection("withdrawals").document(w.id)
    val uRef = Repo.db.collection("users").document(w.uid)
    Repo.db.runTransaction { t ->
        if (t.get(wRef).getString("status") != "pending") throw Exception("Already handled")
        t.update(wRef, "status", "rejected")
        t.update(uRef, "coins", FieldValue.increment(w.coins))
        null
    }.await()
}

// ---------------------------------------------------------------- Matches
@Composable
private fun MatchesSection(config: AppConfig) {
    var matches by remember { mutableStateOf(listOf<MatchDoc>()) }
    var selected by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val reg = Repo.db.collection("matches").whereEqualTo("date", todayKey())
            .addSnapshotListener { s, _ ->
                matches = s?.documents
                    ?.mapNotNull { d -> d.toObject(MatchDoc::class.java)?.copy(id = d.id) }
                    ?.sortedBy { it.slotIndex } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    Text("Room ID and password", style = MaterialTheme.typography.titleLarge)
    matches.forEach { m -> RoomRow(m) }

    HorizontalDivider()
    Text("Award prizes", style = MaterialTheme.typography.titleLarge)
    Text("Pick a match:")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        matches.forEach { m ->
            if (selected == m.id) Button(onClick = { selected = m.id }) { Text(m.slot) }
            else OutlinedButton(onClick = { selected = m.id }) { Text(m.slot) }
        }
    }
    selected?.let { EntriesList(it, config) }
}

@Composable
private fun RoomRow(m: MatchDoc) {
    var roomId by remember(m.id, m.roomId) { mutableStateOf(m.roomId) }
    var pass by remember(m.id, m.roomPass) { mutableStateOf(m.roomPass) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${m.slot} · ${m.joined}/$MAX_PLAYERS players", style = MaterialTheme.typography.titleMedium)
            if (m.joined < MIN_PLAYERS) {
                Text("Below minimum ($MIN_PLAYERS players)", color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(roomId, { roomId = it.trim() }, label = { Text("Room ID") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it.trim() }, label = { Text("Password") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    msg = null
                    scope.launch {
                        try {
                            Repo.db.collection("matches").document(m.id)
                                .update(mapOf("roomId" to roomId, "roomPass" to pass)).await()
                            msg = "Published"
                        } catch (e: Exception) { msg = e.message }
                    }
                }) { Text("Publish") }
                OutlinedButton(onClick = { confirm = true }) { Text("Cancel + refund") }
            }
            OutlinedButton(onClick = {
                msg = null
                scope.launch {
                    try {
                        Repo.db.collection("matches").document(m.id)
                            .update(mapOf("roomId" to "", "roomPass" to "")).await()
                        roomId = ""
                        pass = ""
                        msg = "Cleared"
                    } catch (e: Exception) { msg = e.message }
                }
            }) { Text("Clear room") }
            if (confirm) {
                Text("Refund everyone in this match?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        confirm = false
                        msg = null
                        scope.launch {
                            try {
                                val n = cancelAndRefund(m.id)
                                msg = "Cancelled. Refunded $n players"
                            } catch (e: Exception) { msg = e.message }
                        }
                    }) { Text("Yes") }
                    TextButton(onClick = { confirm = false }) { Text("No") }
                }
            }
        }
    }
}

@Composable
private fun EntriesList(matchId: String, config: AppConfig) {
    var entries by remember(matchId) { mutableStateOf(listOf<EntryDoc>()) }

    DisposableEffect(matchId) {
        val reg = Repo.db.collection("matches").document(matchId).collection("entries")
            .addSnapshotListener { s, _ ->
                entries = s?.documents?.mapNotNull { it.toObject(EntryDoc::class.java) }
                    ?.sortedBy { it.name } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    if (entries.isEmpty()) Text("No players in this match yet.")
    entries.forEach { e -> EntryRow(matchId, e, config) }
}

@Composable
private fun EntryRow(matchId: String, e: EntryDoc, config: AppConfig) {
    var kills by remember(e.uid, e.kills) { mutableStateOf(e.kills.toString()) }
    var booyah by remember(e.uid, e.booyah) { mutableStateOf(e.booyah) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val k = kills.toLongOrNull() ?: 0L
    val prize = prizeFor(booyah, k, config)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${e.name} (${e.gameName.ifBlank { "no game name" }})", style = MaterialTheme.typography.titleMedium)
            if (e.awarded) {
                Text("✅ Awarded: ${e.kills} kills" + (if (e.booyah) " + Booyah" else "") + " → ${e.prize} coins")
            } else {
                OutlinedTextField(
                    kills, { kills = it.filter(Char::isDigit) }, label = { Text("Kills") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = booyah, onCheckedChange = { booyah = it })
                    Text("Booyah")
                }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    msg = null
                    scope.launch {
                        try { awardPrize(matchId, e.uid, k, booyah, prize) }
                        catch (ex: Exception) { msg = ex.message }
                    }
                }) { Text("Award $prize coins") }
            }
        }
    }
}

private suspend fun awardPrize(matchId: String, uid: String, kills: Long, booyah: Boolean, prize: Long) {
    val eRef = Repo.db.collection("matches").document(matchId).collection("entries").document(uid)
    val uRef = Repo.db.collection("users").document(uid)
    Repo.db.runTransaction { t ->
        if (t.get(eRef).getBoolean("awarded") == true) throw Exception("Already awarded")
        t.update(eRef, mapOf("kills" to kills, "booyah" to booyah, "prize" to prize, "awarded" to true))
        if (prize > 0) t.update(uRef, "coins", FieldValue.increment(prize))
        null
    }.await()
}

private suspend fun cancelAndRefund(matchId: String): Int {
    val mRef = Repo.db.collection("matches").document(matchId)
    val snap = mRef.collection("entries").get().await()
    val uids = snap.documents.map { it.id }
    Repo.db.runTransaction { t ->
        if (t.get(mRef).getBoolean("cancelled") == true) throw Exception("Already cancelled")
        val eRefs = uids.map { mRef.collection("entries").document(it) }
        val docs = eRefs.map { t.get(it) }
        docs.forEachIndexed { i, d ->
            if (d.exists() && d.getBoolean("refunded") != true) {
                t.update(
                    Repo.db.collection("users").document(uids[i]),
                    "coins", FieldValue.increment(ENTRY_FEE.toLong())
                )
                t.update(eRefs[i], "refunded", true)
            }
        }
        t.update(mRef, "cancelled", true)
        null
    }.await()
    return uids.size
}

// ---------------------------------------------------------------- Players
@Composable
private fun PlayersSection() {
    val clipboard = LocalClipboardManager.current
    var users by remember { mutableStateOf(listOf<UserDoc>()) }
    var msg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val reg = Repo.db.collection("users").addSnapshotListener { s, _ ->
            users = s?.documents?.mapNotNull { it.toObject(UserDoc::class.java) }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        }
        onDispose { reg.remove() }
    }

    Text("All players (${users.size})", style = MaterialTheme.typography.titleLarge)
    msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    users.forEach { u ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${u.name} · ${u.mobile} · ${u.coins} coins")
                if (u.upi.isBlank()) Text("UPI: not saved")
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("UPI: ${u.upi}")
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(u.upi)); msg = "UPI copied"
                    }) { Text("Copy") }
                }
            }
        }
    }
}
