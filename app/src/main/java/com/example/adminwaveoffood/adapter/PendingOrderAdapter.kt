package com.example.adminwaveoffood.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.adminwaveoffood.databinding.PendingOrderItemBinding
import com.example.adminwaveoffood.model.OrderDetails
import com.google.firebase.database.*

class PendingOrderAdapter(
    private val context: Context,
    private val customerNames: MutableList<String>,
    private val quantity: MutableList<String>,
    private val foodImage: MutableList<String>,
    private val listOfOrderItem: ArrayList<OrderDetails>,
    private val itemClicked: OnItemClicked
) : RecyclerView.Adapter<PendingOrderAdapter.PendingOrderViewHolder>() {

    interface OnItemClicked {
        fun onItemClickListener(position: Int)
        fun onItemAcceptClickListener(position: Int, foodId: String, userUid: String)
        fun onItemDispatchClickListener(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingOrderViewHolder {
        val binding = PendingOrderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PendingOrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingOrderViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = customerNames.size

    inner class PendingOrderViewHolder(internal val binding: PendingOrderItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var isAccepted = false

        fun bind(position: Int) {
            val orderItem = listOfOrderItem[position]

            binding.apply {
                // Bind data to views
                customerName.text = customerNames[position]
                pendingOrderQuantity.text = quantity[position]
                val uriString = foodImage[position]
                val uri = Uri.parse(uriString)
                Glide.with(context).load(uri).into(orderFoodImage)

                // Initial state for the button
                if (isAccepted) {
                    orderedAcceptButton.text = "Dispatch"
                } else {
                    orderedAcceptButton.text = "Accept"
                }

                // Handle Accept/Dispatch button clicks
                orderedAcceptButton.setOnClickListener {
                    if (!isAccepted) {
                        // Accept order logic
                        val firstFoodName = orderItem.foodNames?.firstOrNull()
                        val userUid = orderItem.userUid ?: ""

                        if (!firstFoodName.isNullOrEmpty()) {
                            findFoodIdByName(firstFoodName) { foodId ->
                                if (foodId.isNotEmpty()) {
                                    // Call listener for accepting the order
                                    itemClicked.onItemAcceptClickListener(position, foodId, userUid)
                                    isAccepted = true
                                    orderedAcceptButton.text = "Dispatch"
                                    showToast("Order is accepted")
                                } else {
                                    showToast("Food ID not found for $firstFoodName")
                                }
                            }
                        } else {
                            showToast("No food items found in this order")
                        }
                    } else {
                        // Dispatch order logic
                        itemClicked.onItemDispatchClickListener(position)
                        removeItemFromList(position)
                        showToast("Order is dispatched")
                    }
                }

                // Handle item click
                itemView.setOnClickListener {
                    itemClicked.onItemClickListener(position)
                }
            }
        }

        // Helper function to remove item from all lists and notify adapter
        private fun removeItemFromList(position: Int) {
            customerNames.removeAt(position)
            quantity.removeAt(position)
            foodImage.removeAt(position)
            listOfOrderItem.removeAt(position)
            notifyItemRemoved(position)
        }

        // Helper function to display a toast message
        private fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        // Function to find food ID by name from Firebase
        private fun findFoodIdByName(foodName: String, callback: (String) -> Unit) {
            val menuRef = FirebaseDatabase.getInstance().reference.child("menu")

            menuRef.orderByChild("foodName").equalTo(foodName)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var foodId = ""
                        for (itemSnapshot in snapshot.children) {
                            foodId = itemSnapshot.key ?: "" // Retrieve the key as food ID
                            break // Exit loop after first match
                        }
                        callback(foodId)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("PendingOrderAdapter", "Error finding food ID", error.toException())
                        callback("")
                    }
                })
        }
    }

}
