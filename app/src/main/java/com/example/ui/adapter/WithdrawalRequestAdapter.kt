package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.data.model.WithdrawRequest
import com.example.databinding.ItemWithdrawalRequestBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WithdrawalRequestAdapter(
    private var requests: List<WithdrawRequest> = emptyList()
) : RecyclerView.Adapter<WithdrawalRequestAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemWithdrawalRequestBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWithdrawalRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = requests[position]
        with(holder.binding) {
            tvItemUpiId.text = item.upiId

            val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            val dateStr = if (item.timestamp > 0) sdf.format(Date(item.timestamp)) else "Just now"
            tvItemDate.text = "$dateStr • ${item.coinsDeducted} Coins"

            tvItemAmount.text = "₹${item.inrAmount.toInt()}"
            tvItemStatus.text = item.status
        }
    }

    override fun getItemCount(): Int = requests.size

    fun updateData(newRequests: List<WithdrawRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
