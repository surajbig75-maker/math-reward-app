package com.example

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ads.RewardedAdManager
import com.example.data.GameRepository
import com.example.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: GameRepository
    private lateinit var adManager: RewardedAdManager

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = GameRepository.getInstance(this)
        adManager = RewardedAdManager.getInstance(this)

        initAdMob()
        observeData()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateAdCooldownStatus()
    }

    private fun initAdMob() {
        binding.pbAdLoading.visibility = View.VISIBLE
        adManager.initialize {
            runOnUiThread {
                binding.pbAdLoading.visibility = View.GONE
                updateAdCooldownStatus()
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repository.userState.collectLatest { user ->
                binding.tvTotalCoins.text = getString(R.string.coins_display_format, user.totalCoins)

                val questionsAnswered = user.quizQuestionsAnsweredToday.toInt()
                binding.tvQuizProgress.text = getString(
                    R.string.quiz_progress_format,
                    questionsAnswered,
                    GameRepository.DAILY_QUESTION_LIMIT
                )
                binding.progressQuiz.progress = questionsAnswered

                val adsWatched = user.adsWatchedCount.toInt()
                binding.tvAdCount.text = getString(
                    R.string.bonus_daily_limit_format,
                    adsWatched,
                    GameRepository.MAX_DAILY_ADS
                )

                updateAdCooldownStatus()
            }
        }
    }

    private fun setupListeners() {
        binding.btnStartQuiz.setOnClickListener {
            val user = repository.userState.value
            if (user.quizQuestionsAnsweredToday >= GameRepository.DAILY_QUESTION_LIMIT) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.quiz_daily_limit_reached_msg),
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                startActivity(Intent(this, QuizActivity::class.java))
            }
        }

        binding.btnOpenWallet.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }

        binding.btnQuickWallet.setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }

        binding.btnClaimBonus.setOnClickListener {
            onClaimBonusClicked()
        }

        binding.btnRules.setOnClickListener {
            showRulesDialog()
        }
    }

    private fun updateAdCooldownStatus() {
        stopCountdown()

        val (canWatch, remainingCooldown) = repository.canWatchRewardedAd()

        when {
            remainingCooldown == -1L -> {
                // 24-hr limit (15 views) reached
                binding.tvCooldownStatus.text = getString(R.string.bonus_limit_reached)
                binding.btnClaimBonus.isEnabled = false
            }
            !canWatch && remainingCooldown > 0 -> {
                // Cooldown timer active (3-minute interval)
                startCountdown(remainingCooldown)
            }
            else -> {
                // Ad is ready to view
                binding.tvCooldownStatus.text = getString(R.string.bonus_ready)
                binding.btnClaimBonus.isEnabled = true
            }
        }
    }

    private fun startCountdown(initialMillis: Long) {
        var millisLeft = initialMillis
        binding.btnClaimBonus.isEnabled = false

        countdownRunnable = object : Runnable {
            override fun run() {
                if (millisLeft <= 0) {
                    binding.tvCooldownStatus.text = getString(R.string.bonus_ready)
                    binding.btnClaimBonus.isEnabled = true
                    countdownRunnable = null
                } else {
                    val minutes = (millisLeft / 1000) / 60
                    val seconds = (millisLeft / 1000) % 60
                    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    binding.tvCooldownStatus.text = getString(R.string.bonus_cooldown_format, timeStr)
                    millisLeft -= 1000L
                    countdownHandler.postDelayed(this, 1000L)
                }
            }
        }

        countdownHandler.post(countdownRunnable!!)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun onClaimBonusClicked() {
        val (canWatch, _) = repository.canWatchRewardedAd()
        if (!canWatch) {
            Snackbar.make(binding.root, getString(R.string.ad_cooldown_active), Snackbar.LENGTH_SHORT).show()
            updateAdCooldownStatus()
            return
        }

        binding.pbAdLoading.visibility = View.VISIBLE
        binding.btnClaimBonus.isEnabled = false

        adManager.showAd(
            activity = this,
            onUserEarnedReward = { coins ->
                repository.awardAdBonus(coins.toLong())
                Snackbar.make(binding.root, getString(R.string.ad_reward_earned), Snackbar.LENGTH_LONG).show()
            },
            onAdDismissed = {
                binding.pbAdLoading.visibility = View.GONE
                updateAdCooldownStatus()
            },
            onAdFailed = { errorMsg ->
                binding.pbAdLoading.visibility = View.GONE
                Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_SHORT).show()
                updateAdCooldownStatus()
            }
        )
    }

    private fun showRulesDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rules_title))
            .setMessage(getString(R.string.rules_content))
            .setPositiveButton(getString(R.string.btn_close), null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
    }
}
