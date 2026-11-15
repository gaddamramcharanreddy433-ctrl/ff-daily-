package com.ffdaily.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var loggedIn by remember { mutableStateOf(Repo.uid != null) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener {
                            loggedIn = it.currentUser != null
                        }
                        FirebaseAuth.getInstance().addAuthStateListener(listener)
                        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
                    }

                    if (loggedIn) MainScreen() else LoginScreen()
                }
            }
        }
    }
}
