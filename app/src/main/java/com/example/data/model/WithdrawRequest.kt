package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Represents a withdrawal redemption request stored in the 'withdraw_requests' Firestore collection.
 */
@IgnoreExtraProperties
data class WithdrawRequest(
    val requestId: String = "",
    val userId: String = "",
    val upiId: String = "",
    val coinsDeducted: Long = 0L,
    val inrAmount: Double = 0.0,
    val timestamp: Long = 0L,
    val status: String = "Pending"
)
