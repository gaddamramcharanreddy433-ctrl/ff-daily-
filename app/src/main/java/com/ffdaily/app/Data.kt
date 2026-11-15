package com.ffdaily.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ENTRY_FEE = 10L
const val MAX_PLAYERS = 48L
const val MIN_WITHDRAW = 30L
const val FREE_COINS = 10L

val MATCH_SLOTS = listOf(
    "8:00 AM", "9:30 AM", "11:00 AM", "12:30 PM", "2:00 PM",
    "3:30 PM", "5:00 PM", "6:30 PM", "8:00 PM"
)

data class UserDoc(
    val name: String = "",
    val mobile: String = "",
    val gameName: String = "",
    val upi: String = "",
    val coins: Long = 0
)

data class AppConfig(
    val adminUpi: String = "",
    val rupeesPerCoin: Double = 1.0,
    val prizeBooyahTenKills: Long = 10,
    val prizeBooyah: Long = 5,
    val prizeTenKills: Long = 8
)

data class MatchDoc(
    val id: String = "",
    val slot: String = "",
    val slotIndex: Long = 0,
    val date: String = "",
    val roomId: String = "",
    val roomPass: String = "",
    val joined: Long = 0
)

data class EntryDoc(
    val uid: String = "",
    val name: String = "",
    val gameName: String = "",
    val upi: String = "",
    val kills: Long = 0,
    val booyah: Boolean = false,
    val prize: Long = 0,
    val awarded: Boolean = false
)

data class PurchaseDoc(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val coins: Long = 0,
    val amount: Double = 0.0,
    val utr: String = "",
    val status: String = "pending"
)

data class WithdrawDoc(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val upi: String = "",
    val coins: Long = 0,
    val amount: Double = 0.0,
    val status: String = "pending"
)

fun prizeFor(booyah: Boolean, kills: Long, c: AppConfig): Long = when {
    booyah && kills >= 10 -> c.prizeBooyahTenKills
    booyah -> c.prizeBooyah
    kills >= 10 -> c.prizeTenKills
    else -> 0L
}

fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    private fun email(mobile: String) = "$mobile@ffapp.local"
    private fun password(pin: String) = "${pin}#ff"

    private fun checkInputs(mobile: String, pin: String) {
        require(mobile.length == 10 && mobile.all { it.isDigit() }) { "Enter a 10-digit mobile number" }
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be 4 digits" }
    }

    suspend fun login(mobile: String, pin: String) {
        checkInputs(mobile, pin)
        auth.signInWithEmailAndPassword(email(mobile), password(pin)).await()
    }

    suspend fun register(name: String, mobile: String, pin: String) {
        checkInputs(mobile, pin)
        require(name.isNotBlank()) { "Enter your name" }
        val res = auth.createUserWithEmailAndPassword(email(mobile), password(pin)).await()
        val id = res.user!!.uid
        db.collection("users").document(id).set(
            mapOf(
                "name" to name.trim(), "mobile" to mobile,
                "gameName" to "", "upi" to "", "coins" to FREE_COINS
            )
        ).await()
    }

    fun logout() = auth.signOut()

    suspend fun isAdmin(): Boolean {
        val id = uid ?: return false
        return db.collection("admins").document(id).get().await().exists()
    }

    suspend fun getConfig(): AppConfig =
        db.collection("config").document("app").get().await()
            .toObject(AppConfig::class.java) ?: AppConfig()

    suspend fun saveProfile(name: String, gameName: String, upi: String) {
        db.collection("users").document(uid!!)
            .update(mapOf("name" to name.trim(), "gameName" to gameName.trim(), "upi" to upi.trim()))
            .await()
    }

    suspend fun ensureTodayMatches() {
        val day = todayKey()
        MATCH_SLOTS.forEachIndexed { i, slot ->
            val ref = db.collection("matches").document("${day}_$i")
            if (!ref.get().await().exists()) {
                ref.set(
                    mapOf(
                        "slot" to slot, "slotIndex" to i, "date" to day,
                        "roomId" to "", "roomPass" to "", "joined" to 0
                    )
                ).await()
            }
        }
    }

    suspend fun joinMatch(matchId: String) {
        val id = uid!!
        val userRef = db.collection("users").document(id)
        val matchRef = db.collection("matches").document(matchId)
        val entryRef = matchRef.collection("entries").document(id)
        db.runTransaction { t ->
            val u = t.get(userRef)
            val m = t.get(matchRef)
            if (t.get(entryRef).exists()) throw Exception("You already joined this match")
            if ((u.getLong("coins") ?: 0) < ENTRY_FEE) throw Exception("Not enough coins")
            if ((m.getLong("joined") ?: 0) >= MAX_PLAYERS) throw Exception("Match is full")
            t.update(userRef, "coins", FieldValue.increment(-ENTRY_FEE))
            t.update(matchRef, "joined", FieldValue.increment(1))
            t.set(
                entryRef,
                mapOf(
                    "uid" to id,
                    "name" to (u.getString("name") ?: ""),
                    "gameName" to (u.getString("gameName") ?: ""),
                    "upi" to (u.getString("upi") ?: ""),
                    "kills" to 0, "booyah" to false, "prize" to 0, "awarded" to false
                )
            )
            null
        }.await()
    }

    suspend fun requestPurchase(coins: Long, amount: Double, utr: String) {
        require(utr.length == 12 && utr.all { it.isDigit() }) { "UTR must be 12 digits" }
        val id = uid!!
        val name = db.collection("users").document(id).get().await().getString("name") ?: ""
        db.collection("purchases").add(
            mapOf(
                "uid" to id, "name" to name, "coins" to coins, "amount" to amount,
                "utr" to utr, "status" to "pending"
            )
        ).await()
    }

    suspend fun requestWithdraw(coins: Long, rupeesPerCoin: Double) {
        require(coins >= MIN_WITHDRAW) { "Minimum withdrawal is $MIN_WITHDRAW coins" }
        val id = uid!!
        val userRef = db.collection("users").document(id)
        val wRef = db.collection("withdrawals").document()
        db.runTransaction { t ->
            val u = t.get(userRef)
            val upi = u.getString("upi") ?: ""
            if (upi.isBlank()) throw Exception("Save your UPI ID in Profile first")
            if ((u.getLong("coins") ?: 0) < coins) throw Exception("Not enough coins")
            t.update(userRef, "coins", FieldValue.increment(-coins))
            t.set(
                wRef,
                mapOf(
                    "uid" to id, "name" to (u.getString("name") ?: ""), "upi" to upi,
                    "coins" to coins, "amount" to coins * rupeesPerCoin, "status" to "pending"
                )
            )
            null
        }.await()
    }
}
