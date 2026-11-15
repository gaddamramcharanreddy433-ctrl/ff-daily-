package com.ffdaily.app

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
import kotlinx.coroutines.launch

private val PACKS = listOf(10L, 20L, 50L)

@Composable
fun WalletScreen(user: UserDoc, config: AppConfig) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf(10L) }
    var utr by remember { mutableStateOf("") }
    var wCoins by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var purchases by remember { mutableStateOf(listOf<PurchaseDoc>()) }
    var withdrawals by remember { mutableStateOf(listOf<WithdrawDoc>()) }

    DisposableEffect(Unit) {
        val id = Repo.uid!!
        val r1 = Repo.db.collection("purchases").whereEqualTo("uid", id)
            .addSnapshotListener { s, _ ->
                purchases = s?.documents?.mapNotNull { d ->
                    d.toObject(PurchaseDoc::class.java)?.copy(id = d.id)
                } ?: emptyList()
            }
        val r2 = Repo.db.collection("withdrawals").whereEqualTo("uid", id)
            .addSnapshotListener { s, _ ->
                withdrawals = s?.documents?.mapNotNull { d ->
                    d.toObject(WithdrawDoc::class.java)?.copy(id = d.id)
                } ?: emptyList()
            }
        onDispose { r1.remove(); r2.remove() }
    }

    fun rupees(coins: Long) = "₹" + "%.0f".format(coins * config.rupeesPerCoin)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        // ---------- Shop ----------
        Text("Buy coins", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PACKS.forEach { p ->
                val label = "$p coins\n${rupees(p)}"
                if (pack == p) Button(onClick = { pack = p }) { Text(label) }
                else OutlinedButton(onClick = { pack = p }) { Text(label) }
            }
        }
        if (config.adminUpi.isBlank()) {
            Text("Payments are not set up yet. Please try later.")
        } else {
            Text("1. Pay ${rupees(pack)} to this UPI ID:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(config.adminUpi, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(config.adminUpi))
                    msg = "UPI ID copied"
                }) { Text("Copy") }
            }
            Text("2. Enter the 12-digit UTR / reference number:")
            OutlinedTextField(
                value = utr,
                onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) utr = it },
                label = { Text("UTR (12 digits)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    busy = true; msg = null
                    scope.launch {
                        try {
                            Repo.requestPurchase(pack, pack * config.rupeesPerCoin, utr)
                            utr = ""
                            msg = "Request sent. Coins are added after admin approval."
                        } catch (e: Exception) { msg = e.message }
                        finally { busy = false }
                    }
                },
                enabled = !busy && utr.length == 12,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Submit payment") }
        }

        // ---------- Withdraw ----------
        HorizontalDivider()
        Text("Withdraw", style = MaterialTheme.typography.titleLarge)
        Text("Minimum $MIN_WITHDRAW coins. Money goes to your UPI ID: " +
            (user.upi.ifBlank { "not saved (add it in Profile)" }))
        OutlinedTextField(
            value = wCoins,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) wCoins = it },
            label = { Text("Coins to withdraw") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                busy = true; msg = null
                scope.launch {
                    try {
                        Repo.requestWithdraw(wCoins.toLongOrNull() ?: 0L, config.rupeesPerCoin)
                        wCoins = ""
                        msg = "Withdrawal requested. You will be paid on your UPI ID."
                    } catch (e: Exception) { msg = e.message }
                    finally { busy = false }
                }
            },
            enabled = !busy && (wCoins.toLongOrNull() ?: 0L) >= MIN_WITHDRAW,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Request withdrawal") }

        // ---------- History ----------
        HorizontalDivider()
        Text("History", style = MaterialTheme.typography.titleLarge)
        if (purchases.isEmpty() && withdrawals.isEmpty()) Text("Nothing yet.")
        purchases.forEach {
            Text("Bought ${it.coins} coins · UTR ${it.utr} · ${it.status}")
        }
        withdrawals.forEach {
            Text("Withdraw ${it.coins} coins (₹${"%.0f".format(it.amount)}) · ${it.status}")
        }
        Spacer(Modifier.height(24.dp))
    }
}
