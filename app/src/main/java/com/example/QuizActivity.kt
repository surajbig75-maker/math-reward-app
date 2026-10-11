package com.example

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.GameRepository
import com.example.data.model.QuizQuestion
import com.example.databinding.ActivityQuizBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizBinding
    private lateinit var repository: GameRepository
    private var currentQuestion: QuizQuestion? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isAnswerPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = GameRepository.getInstance(this)

        setupToolbar()
        observeUserData()
        setupClickListeners()
        loadNextQuestion()
    }

    private fun setupToolbar() {
        binding.toolbarQuiz.setNavigationOnClickListener {
            finish()
        }
    }

    private fun observeUserData() {
        lifecycleScope.launch {
            repository.userState.collectLatest { user ->
                binding.tvQuizCoins.text = user.totalCoins.toString()
                val questionsToday = user.quizQuestionsAnsweredToday.toInt()

                if (questionsToday >= GameRepository.DAILY_QUESTION_LIMIT) {
                    showDailyLimitReached()
                } else {
                    binding.tvQuestionCount.text = getString(
                        R.string.question_number_format,
                        questionsToday + 1,
                        GameRepository.DAILY_QUESTION_LIMIT
                    )
                    binding.progressQuestion.progress = questionsToday + 1
                }
            }
        }
    }

    private fun setupClickListeners() {
        val buttons = listOf(
            binding.btnOption1,
            binding.btnOption2,
            binding.btnOption3,
            binding.btnOption4
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                if (!isAnswerPending && currentQuestion != null) {
                    handleAnswerSelection(button)
                }
            }
        }

        binding.btnDoneQuiz.setOnClickListener {
            finish()
        }
    }

    private fun loadNextQuestion() {
        val user = repository.userState.value
        if (user.quizQuestionsAnsweredToday >= GameRepository.DAILY_QUESTION_LIMIT) {
            showDailyLimitReached()
            return
        }

        isAnswerPending = false
        resetOptionButtons()

        val question = QuizQuestion.generateRandom()
        currentQuestion = question

        binding.tvQuestionExpression.text = question.expression
        binding.tvAnswerFeedback.visibility = View.INVISIBLE

        val buttons = listOf(
            binding.btnOption1,
            binding.btnOption2,
            binding.btnOption3,
            binding.btnOption4
        )

        question.options.forEachIndexed { index, optionValue ->
            if (index < buttons.size) {
                buttons[index].text = optionValue.toString()
                buttons[index].isEnabled = true
            }
        }
    }

    private fun handleAnswerSelection(selectedButton: MaterialButton) {
        val question = currentQuestion ?: return
        isAnswerPending = true

        val selectedAnswer = selectedButton.text.toString().toIntOrNull() ?: return
        val isCorrect = (selectedAnswer == question.correctAnswer)

        // Disable all buttons to prevent multiple clicks
        val buttons = listOf(
            binding.btnOption1,
            binding.btnOption2,
            binding.btnOption3,
            binding.btnOption4
        )
        buttons.forEach { it.isEnabled = false }

        val correctColor = ContextCompat.getColor(this, R.color.quiz_correct)
        val wrongColor = ContextCompat.getColor(this, R.color.quiz_wrong)
        val whiteColor = ContextCompat.getColor(this, R.color.white)

        if (isCorrect) {
            selectedButton.backgroundTintList = ColorStateList.valueOf(correctColor)
            selectedButton.setTextColor(whiteColor)
            binding.tvAnswerFeedback.text = getString(R.string.quiz_correct_feedback)
            binding.tvAnswerFeedback.setTextColor(correctColor)
        } else {
            selectedButton.backgroundTintList = ColorStateList.valueOf(wrongColor)
            selectedButton.setTextColor(whiteColor)

            // Highlight the actual correct option in green
            buttons.firstOrNull { it.text.toString().toIntOrNull() == question.correctAnswer }?.let { correctBtn ->
                correctBtn.backgroundTintList = ColorStateList.valueOf(correctColor)
                correctBtn.setTextColor(whiteColor)
            }

            binding.tvAnswerFeedback.text = getString(R.string.quiz_wrong_feedback)
            binding.tvAnswerFeedback.setTextColor(wrongColor)
        }

        binding.tvAnswerFeedback.visibility = View.VISIBLE

        // Award coins in repository
        repository.awardQuizAnswer(isCorrect)

        // Delay before transitioning to next question
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                loadNextQuestion()
            }
        }, 1200L)
    }

    private fun resetOptionButtons() {
        val surfaceColor = ContextCompat.getColor(this, R.color.surface_card)
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)
        val buttons = listOf(
            binding.btnOption1,
            binding.btnOption2,
            binding.btnOption3,
            binding.btnOption4
        )

        buttons.forEach { button ->
            button.backgroundTintList = ColorStateList.valueOf(surfaceColor)
            button.setTextColor(textPrimaryColor)
            button.isEnabled = true
        }
    }

    private fun showDailyLimitReached() {
        binding.cardQuestion.visibility = View.GONE
        binding.btnOption1.visibility = View.GONE
        binding.btnOption2.visibility = View.GONE
        binding.btnOption3.visibility = View.GONE
        binding.btnOption4.visibility = View.GONE
        binding.tvAnswerFeedback.visibility = View.GONE
        binding.cardLimitReached.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
