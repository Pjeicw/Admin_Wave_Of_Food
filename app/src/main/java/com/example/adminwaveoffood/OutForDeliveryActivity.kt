package com.example.adminwaveoffood

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adminwaveoffood.adapter.DeliveryAdapter
import com.example.adminwaveoffood.databinding.ActivityOutForDeliveryBinding
import com.example.adminwaveoffood.model.OrderDetails
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class OutForDeliveryActivity : AppCompatActivity() {
    private val binding: ActivityOutForDeliveryBinding by lazy {
        ActivityOutForDeliveryBinding.inflate(layoutInflater)}

    private lateinit var database: FirebaseDatabase
    private var listOfCompleteOrderList: ArrayList<OrderDetails> = arrayListOf()
    private lateinit var completeOrderQuery: Query // Changed to Query
    private lateinit var valueEventListener: ValueEventListener
    private lateinit var adapter: DeliveryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.backButton.setOnClickListener {
            finish()
        }

        database = FirebaseDatabase.getInstance()
        completeOrderQuery = database.reference.child("CompletedOrder") // Changed to Query
            .orderByChild("currentTime")

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listOfCompleteOrderList.clear()
                for (orderSnapshot in snapshot.children) {
                    val completeOrder = orderSnapshot.getValue(OrderDetails::class.java)
                    completeOrder?.let { listOfCompleteOrderList.add(it) }
                }
                listOfCompleteOrderList.reverse()

                if (listOfCompleteOrderList.isEmpty()) {
                    binding.noItemsTextView.visibility = View.VISIBLE
                    binding.deliveryRecyclerView.visibility = View.GONE
                } else {
                    binding.noItemsTextView.visibility = View.GONE
                    binding.deliveryRecyclerView.visibility = View.VISIBLE

                    if (!::adapter.isInitialized) {
                        // Initialize the adapter only once
                        val customerNames = listOfCompleteOrderList.mapNotNull { it.userName }.toMutableList()
                        val moneyStatuses = listOfCompleteOrderList.map { it.paymentReceived }.toMutableList()
                        adapter = DeliveryAdapter(customerNames, moneyStatuses)
                        binding.deliveryRecyclerView.adapter = adapter
                        binding.deliveryRecyclerView.layoutManager = LinearLayoutManager(this@OutForDeliveryActivity)
                    } else {
                        // Update the adapter with new data
                        val customerNames = listOfCompleteOrderList.mapNotNull { it.userName }
                        val moneyStatuses = listOfCompleteOrderList.map { it.paymentReceived }
                        adapter.updateList(customerNames, moneyStatuses)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("OutForDeliveryActivity", "Error getting completed orders", error.toException())
                showPopupMessage("Error", "Failed to retrieve completed orders.", true, error.message)
                binding.noItemsTextView.visibility = View.VISIBLE
                binding.deliveryRecyclerView.visibility = View.GONE
            }
        }

        retrieveCompleteOrderDetail()
    }

    private fun retrieveCompleteOrderDetail() {
        completeOrderQuery.addValueEventListener(valueEventListener) // Use the Query
    }

    override fun onStop() {
        super.onStop()
        completeOrderQuery.removeEventListener(valueEventListener) // Use the Query
    }

    private fun showPopupMessage(
        title: String,
        message: String,
        isError: Boolean = false,
        logMessage: String? = null
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        val icon = when {
            isError -> R.drawable.ic_error
            message.startsWith("Failed", true) || message.contains(
                "error",
                true
            ) -> R.drawable.ic_error
            else -> R.drawable.ic_info
        }
        iconView.setImageResource(icon)
        titleView.text = title
        messageView.text = if (logMessage != null) "$message\n\nLog:$logMessage" else message

        val builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
        builder.setView(dialogView)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)
        dialog.show()

        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.textSize = 20f

        val layoutParams = dialog.window?.attributes
        layoutParams?.width = resources.displayMetrics.widthPixels - (32 * 2).dp()
        dialog.window?.attributes = layoutParams
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

}