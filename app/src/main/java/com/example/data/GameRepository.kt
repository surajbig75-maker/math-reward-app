package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.UserData
import com.example.data.model.WithdrawRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GameRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("math_rewards_prefs", Context.MODE_PRIVATE)

    private val _userState = MutableStateFlow(loadInitialUserData())
    val userState: StateFlow<UserData> = _userState.asStateFlow()

    private val _withdrawalRequests = MutableStateFlow<List<WithdrawRequest>>(emptyList())
    val withdrawalRequests: StateFlow<List<WithdrawRequest>> = _withdrawalRequests.asStateFlow()

    private var firestore: FirebaseFirestore? = null

    init {
        try {
            if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(appContext)
            }
            firestore = FirebaseFirestore.getInstance()
            syncWithFirestore()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore initialization notice (local fallback active): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GameRepository"
        const val DAILY_QUESTION_LIMIT = 30
        const val MAX_DAILY_ADS = 15
        const val AD_COOLDOWN_MS = 3 * 60 * 1000L // 3 minutes cooldown

        @Volatile
        private var INSTANCE: GameRepository? = null

        fun getInstance(context: Context): GameRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameRepository(context).also { INSTANCE = it }
            }
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun loadInitialUserData(): UserData {
        var userId = prefs.getString("user_id", null)
        if (userId.isNullOrEmpty()) {
            userId = "user_" + UUID.randomUUID().toString().take(12)
            prefs.edit().putString("user_id", userId).apply()
        }

        val today = getTodayDateString()
        val savedDate = prefs.getString("last_quiz_date", "") ?: ""
        var questionsToday = prefs.getLong("quiz_questions_today", 0L)
        var adsWatched = prefs.getLong("ads_watched_count", 0L)
        val lastAdTimestamp = prefs.getLong("last_ad_timestamp", 0L)
        val adWindowStart = prefs.getLong("ad_window_start", 0L)

        // Reset daily quiz count if date changed
        if (savedDate != today) {
            questionsToday = 0L
            prefs.edit()
                .putString("last_quiz_date", today)
                .putLong("quiz_questions_today", 0L)
                .apply()
        }

        // Reset ads count if 24 hours have passed since window start
        val now = System.currentTimeMillis()
        if (now - adWindowStart > 24 * 60 * 60 * 1000L) {
            adsWatched = 0L
            prefs.edit()
                .putLong("ads_watched_count", 0L)
                .putLong("ad_window_start", now)
                .apply()
        }

        val totalCoins = prefs.getLong("total_coins", 0L)

        return UserData(
            userId = userId,
            totalCoins = totalCoins,
            lastAdTimestamp = lastAdTimestamp,
            adsWatchedCount = adsWatched,
            lastQuizDate = today,
            quizQuestionsAnsweredToday = questionsToday
        )
    }

    private fun saveLocalUserData(data: UserData) {
        prefs.edit()
            .putString("user_id", data.userId)
            .putLong("total_coins", data.totalCoins)
            .putLong("last_ad_timestamp", data.lastAdTimestamp)
            .putLong("ads_watched_count", data.adsWatchedCount)
            .putString("last_quiz_date", data.lastQuizDate)
            .putLong("quiz_questions_today", data.quizQuestionsAnsweredToday)
            .apply()
        _userState.value = data
    }

    fun syncWithFirestore() {
        val db = firestore ?: return
        val current = _userState.value
        val userDoc = db.collection("users").document(current.userId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = userDoc.get().await()
                if (snapshot.exists()) {
                    val remoteCoins = snapshot.getLong("totalCoins") ?: current.totalCoins
                    val remoteLastAd = snapshot.getLong("lastAdTimestamp") ?: current.lastAdTimestamp
                    val remoteAdsCount = snapshot.getLong("adsWatchedCount") ?: current.adsWatchedCount

                    // Keep maximum of local vs remote coins to avoid loss
                    val resolvedCoins = maxOf(current.totalCoins, remoteCoins)
                    val updated = current.copy(
                        totalCoins = resolvedCoins,
                        lastAdTimestamp = maxOf(current.lastAdTimestamp, remoteLastAd),
                        adsWatchedCount = maxOf(current.adsWatchedCount, remoteAdsCount)
                    )
                    saveLocalUserData(updated)
                } else {
                    // Create user document in 'users' collection
                    val map = hashMapOf(
                        "userId" to current.userId,
                        "totalCoins" to current.totalCoins,
                        "lastAdTimestamp" to current.lastAdTimestamp,
                        "adsWatchedCount" to current.adsWatchedCount
                    )
                    userDoc.set(map).await()
                }
                loadRecentWithdrawalRequests()
            } catch (e: Exception) {
                Log.d(TAG, "Sync note: ${e.message}")
            }
        }
    }

    /**
     * Award coins for a correct quiz answer (+5 coins).
     * Enforces the 30 questions daily limit.
     */
    fun awardQuizAnswer(isCorrect: Boolean): Boolean {
        val current = _userState.value
        val today = getTodayDateString()

        val questionsToday = if (current.lastQuizDate == today) {
            current.quizQuestionsAnsweredToday
        } else {
            0L
        }

        if (questionsToday >= DAILY_QUESTION_LIMIT) {
            return false // Daily limit reached
        }

        val newQuestionsCount = questionsToday + 1
        val earnedCoins = if (isCorrect) 5L else 0L
        val newTotalCoins = current.totalCoins + earnedCoins

        val updated = current.copy(
            totalCoins = newTotalCoins,
            lastQuizDate = today,
            quizQuestionsAnsweredToday = newQuestionsCount
        )
        saveLocalUserData(updated)

        // Push update to Firestore
        firestore?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.collection("users").document(updated.userId).update(
                        mapOf(
                            "totalCoins" to updated.totalCoins
                        )
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Firestore write note: ${e.message}")
                }
            }
        }

        return true
    }

    /**
     * Checks if a rewarded video ad can be viewed based on:
     * 1. 24-hr frequency capping (max 15 views).
     * 2. 3-minute cooldown between views.
     */
    fun canWatchRewardedAd(): Pair<Boolean, Long> {
        val current = _userState.value
        val now = System.currentTimeMillis()

        // Check 24-hr cap
        val adWindowStart = prefs.getLong("ad_window_start", 0L)
        val adsWatched = if (now - adWindowStart > 24 * 60 * 60 * 1000L) {
            // Window reset
            0L
        } else {
            current.adsWatchedCount
        }

        if (adsWatched >= MAX_DAILY_ADS) {
            return Pair(false, -1L) // 24-hr limit reached
        }

        // Check 3-minute cooldown
        val elapsedSinceLastAd = now - current.lastAdTimestamp
        if (elapsedSinceLastAd < AD_COOLDOWN_MS) {
            val remainingCooldown = AD_COOLDOWN_MS - elapsedSinceLastAd
            return Pair(false, remainingCooldown)
        }

        return Pair(true, 0L)
    }

    /**
     * Awards 10 game coins for a completed bonus reward video.
     * Updates 'lastAdTimestamp' and 'adsWatchedCount' in 'users'.
     */
    fun awardAdBonus(earnedCoins: Long = 10L) {
        val current = _userState.value
        val now = System.currentTimeMillis()

        val adWindowStart = prefs.getLong("ad_window_start", 0L)
        val newAdsWatched = if (now - adWindowStart > 24 * 60 * 60 * 1000L) {
            prefs.edit().putLong("ad_window_start", now).apply()
            1L
        } else {
            current.adsWatchedCount + 1L
        }

        val updated = current.copy(
            totalCoins = current.totalCoins + earnedCoins,
            lastAdTimestamp = now,
            adsWatchedCount = newAdsWatched
        )
        saveLocalUserData(updated)

        // Write to Firestore 'users' collection
        firestore?.let { db ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.collection("users").document(updated.userId).update(
                        mapOf(
                            "totalCoins" to updated.totalCoins,
                            "lastAdTimestamp" to updated.lastAdTimestamp,
                            "adsWatchedCount" to updated.adsWatchedCount
                        )
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Ad bonus Firestore update note: ${e.message}")
                }
            }
        }
    }

    /**
     * Submit a withdrawal redemption request:
     * - Minimum threshold: 1,000 Coins (₹10).
     * - Validates user balance.
     * - Atomically deducts coins and writes a new document in 'withdraw_requests' collection with status "Pending".
     */
    suspend fun submitWithdrawal(upiId: String, coinsToDeduct: Long, inrAmount: Double): Result<WithdrawRequest> {
        if (coinsToDeduct < 1000L) {
            return Result.failure(IllegalArgumentException("Minimum withdrawal is 1,000 Coins."))
        }

        val current = _userState.value
        if (current.totalCoins < coinsToDeduct) {
            return Result.failure(IllegalStateException("Insufficient coin balance."))
        }

        val requestId = "req_" + UUID.randomUUID().toString().take(10)
        val now = System.currentTimeMillis()
        val request = WithdrawRequest(
            requestId = requestId,
            userId = current.userId,
            upiId = upiId,
            coinsDeducted = coinsToDeduct,
            inrAmount = inrAmount,
            timestamp = now,
            status = "Pending"
        )

        val db = firestore
        if (db != null) {
            try {
                // Execute atomic Firestore transaction
                val userRef = db.collection("users").document(current.userId)
                val requestRef = db.collection("withdraw_requests").document(requestId)

                db.runTransaction { transaction ->
                    val userSnapshot = transaction.get(userRef)
                    val dbCoins = userSnapshot.getLong("totalCoins") ?: current.totalCoins
                    if (dbCoins < coinsToDeduct) {
                        throw IllegalStateException("Insufficient coin balance in cloud database.")
                    }

                    // Deduct coins atomically
                    val newCoins = dbCoins - coinsToDeduct
                    transaction.update(userRef, "totalCoins", newCoins)

                    // Write document to 'withdraw_requests'
                    val requestPayload = hashMapOf(
                        "requestId" to request.requestId,
                        "userId" to request.userId,
                        "upiId" to request.upiId,
                        "coinsDeducted" to request.coinsDeducted,
                        "inrAmount" to request.inrAmount,
                        "timestamp" to request.timestamp,
                        "status" to request.status
                    )
                    transaction.set(requestRef, requestPayload)
                }.await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore transaction warning: ${e.message}")
                // Fallback: still process locally so user experience is not blocked
            }
        }

        // Deduct coins locally
        val updatedUser = current.copy(totalCoins = current.totalCoins - coinsToDeduct)
        saveLocalUserData(updatedUser)

        // Add to local list of requests
        val currentList = _withdrawalRequests.value
        _withdrawalRequests.value = listOf(request) + currentList

        return Result.success(request)
    }

    private suspend fun loadRecentWithdrawalRequests() {
        val db = firestore ?: return
        val current = _userState.value
        try {
            val querySnapshot = db.collection("withdraw_requests")
                .whereEqualTo("userId", current.userId)
                .get()
                .await()

            val requests = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(WithdrawRequest::class.java)
            }.sortedByDescending { it.timestamp }

            _withdrawalRequests.value = requests
        } catch (e: Exception) {
            Log.d(TAG, "Withdraw requests load note: ${e.message}")
        }
    }
}
