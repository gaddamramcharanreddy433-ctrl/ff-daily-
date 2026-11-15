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
    var purc
