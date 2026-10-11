package com.example

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.data.GameRepository
import com.example.databinding.ActivityWalletBinding
import com.example.ui.adapter.WithdrawalRequestAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class WalletActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletBinding
    private lateinit var repository: GameRepository
    private lateinit var adapter: WithdrawalRequestAdapter

    // Standard UPI ID pattern: user@bank (e.g. name@oksbi, 9876543210@paytm)
    private val upiPattern = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = GameRepository.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        observeData()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbarWallet.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = WithdrawalRequestAdapter()
        binding.rvWithdrawalRequests.layoutManager = LinearLayoutManager(this)
        binding.rvWithdrawalRequests.adapter = adapter
    }

    private fun observeData() {
        // Observe coin balance
        lifecycleScope.launch {
            repository.userState.collectLatest { user ->
                binding.tvWalletCoins.text = getString(R.string.coins_display_format, user.totalCoins)
            }
        }

        // Observe recent withdrawal requests
        lifecycleScope.launch {
            repository.withdrawalRequests.collectLatest { requests ->
                if (requests.isEmpty()) {
                    binding.tvNoRequests.visibility = View.VISIBLE
                    binding.rvWithdrawalRequests.visibility = View.GONE
                } else {
                    binding.tvNoRequests.visibility = View.GONE
                    binding.rvWithdrawalRequests.visibility = View.VISIBLE
                    adapter.updateData(requests)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSubmitWithdrawal.setOnClickListener {
            validateAndSubmitWithdrawal()
        }
    }

    private fun validateAndSubmitWithdrawal() {
        val upiId = binding.etUpiId.text?.toString()?.trim().orEmpty()

        // 1. UPI ID format validation
        if (upiId.isEmpty()) {
            binding.tilUpiId.error = getString(R.string.err_invalid_upi)
            return
        }

        if (!upiPattern.matcher(upiId).matches()) {
            binding.tilUpiId.error = getString(R.string.err_invalid_upi)
            return
        }

        binding.tilUpiId.error = null

        // 2. Determine selected withdrawal coins & INR amount
        val selectedCoins: Long
        val selectedInr: Double

        when (binding.chipGroupAmount.checkedChipId) {
            R.id.chip50 -> {
                selectedCoins = 5000L
                selectedInr = 50.0
            }
            R.id.chip100 -> {
                selectedCoins = 10000L
                selectedInr = 100.0
            }
            else -> {
                // Default ₹10 (1,000 Coins)
                selectedCoins = 1000L
                selectedInr = 10.0
            }
        }

        // 3. Balance verification
        val currentCoins = repository.userState.value.totalCoins
        if (currentCoins < selectedCoins) {
            Snackbar.make(
                binding.root,
                getString(R.string.err_insufficient_coins),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        // 4. Submit withdrawal transaction
        binding.btnSubmitWithdrawal.isEnabled = false
        binding.pbSubmitting.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = repository.submitWithdrawal(upiId, selectedCoins, selectedInr)

            binding.pbSubmitting.visibility = View.GONE
            binding.btnSubmitWithdrawal.isEnabled = true

            result.onSuccess {
                binding.etUpiId.text?.clear()
                Snackbar.make(
                    binding.root,
                    getString(R.string.withdrawal_success),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Snackbar.make(
                    binding.root,
                    error.message ?: getString(R.string.err_insufficient_coins),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}
