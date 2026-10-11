package com.example.data.model

import kotlin.random.Random

data class QuizQuestion(
    val expression: String,
    val correctAnswer: Int,
    val options: List<Int>
) {
    companion object {
        /**
         * Generates super-easy, fast, and child-friendly arithmetic questions:
         * 1. Single or friendly two-digit addition (e.g., 5 + 7, 12 + 8, 25 + 10)
         * 2. Simple subtraction with positive results (e.g., 15 - 6, 20 - 5)
         * 3. Basic multiplication tables from 2 to 9 (e.g., 4 × 3, 6 × 5)
         */
        fun generateRandom(): QuizQuestion {
            val operation = Random.nextInt(3) // 0: add, 1: subtract, 2: multiply
            val num1: Int
            val num2: Int
            val answer: Int
            val expr: String

            when (operation) {
                0 -> {
                    // Super easy addition: single-digit or simple 2-digit numbers
                    val easyAdds = listOf(
                        Pair(5, 7), Pair(12, 8), Pair(25, 10), Pair(8, 6),
                        Pair(15, 5), Pair(9, 4), Pair(20, 10), Pair(14, 7),
                        Pair(7, 8), Pair(11, 9), Pair(16, 4), Pair(6, 6)
                    )
                    val pair = if (Random.nextBoolean()) {
                        easyAdds.random()
                    } else {
                        val n1 = Random.nextInt(2, 26)
                        val n2 = Random.nextInt(1, 15)
                        Pair(n1, n2)
                    }
                    num1 = pair.first
                    num2 = pair.second
                    answer = num1 + num2
                    expr = "$num1 + $num2 = ?"
                }
                1 -> {
                    // Simple subtraction with positive results
                    val easySubs = listOf(
                        Pair(15, 6), Pair(20, 5), Pair(12, 4), Pair(10, 3),
                        Pair(18, 9), Pair(14, 6), Pair(25, 5), Pair(16, 8),
                        Pair(9, 5), Pair(22, 2), Pair(13, 7), Pair(8, 3)
                    )
                    val pair = if (Random.nextBoolean()) {
                        easySubs.random()
                    } else {
                        val n1 = Random.nextInt(6, 31)
                        val n2 = Random.nextInt(1, n1)
                        Pair(n1, n2)
                    }
                    num1 = pair.first
                    num2 = pair.second
                    answer = num1 - num2
                    expr = "$num1 − $num2 = ?"
                }
                else -> {
                    // Basic multiplication tables from 2 to 9
                    num1 = Random.nextInt(2, 10) // 2 to 9
                    num2 = Random.nextInt(2, 10) // 2 to 9
                    answer = num1 * num2
                    expr = "$num1 × $num2 = ?"
                }
            }

            // Generate 3 plausible nearby options
            val wrongAnswers = mutableSetOf<Int>()
            val offsets = listOf(-2, -1, 1, 2, -3, 3, -4, 4, -5, 5, -10, 10).shuffled()
            for (offset in offsets) {
                val candidate = answer + offset
                if (candidate >= 0 && candidate != answer) {
                    wrongAnswers.add(candidate)
                }
                if (wrongAnswers.size == 3) break
            }

            var delta = 1
            while (wrongAnswers.size < 3) {
                val cand = (answer + delta).coerceAtLeast(0)
                if (cand != answer) {
                    wrongAnswers.add(cand)
                }
                delta = if (delta > 0) -delta - 1 else -delta + 1
            }

            val allOptions = (wrongAnswers.toList() + answer).shuffled()

            return QuizQuestion(
                expression = expr,
                correctAnswer = answer,
                options = allOptions
            )
        }
    }
}
