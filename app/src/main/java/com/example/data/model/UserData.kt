package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Represents user data stored in the 'users' Firestore collection.
 * Default values provided for Firebase deserialization.
 */
@IgnoreExtraProperties
data class UserData(
    val userId: String = "",
    val totalCoins: Long = 0L,
    val lastAdTimestamp: Long = 0L,
    val adsWatchedCount: Long = 0L,
    val lastQuizDate: String = "",
    val quizQuestionsAnsweredToday: Long = 0L
)
