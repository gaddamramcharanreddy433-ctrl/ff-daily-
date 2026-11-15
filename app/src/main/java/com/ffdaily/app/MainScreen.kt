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
            val r1 = Repo.db.collection("users"
