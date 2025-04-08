package com.example.adminwaveoffood.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.adminwaveoffood.R
import com.example.adminwaveoffood.databinding.ItemItemBinding
import com.example.adminwaveoffood.model.AllMenu
import com.google.firebase.database.DatabaseReference

class MenuItemAdapter(
    private val context: Context,
    private val menuList: MutableList<AllMenu>, // Use MutableList for adding/removing items
    private val database: DatabaseReference,
    private val refreshItemsCallback: () -> Unit,
    private val onDeleteClickListener: (position: Int) -> Unit
) : RecyclerView.Adapter<MenuItemAdapter.MenuItemViewHolder>() {

    private val itemQuantities = IntArray(menuList.size) { 1 }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemViewHolder {
        val binding = ItemItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MenuItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuItemViewHolder, position:Int) {
        val menuItem = menuList[position]
        holder.bind(menuItem, position) // Pass position to bind method

        holder.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog(position)
        }
    }

    override fun getItemCount(): Int = menuList.size

    inner class MenuItemViewHolder(private val binding: ItemItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val deleteButton: View = binding.deleteButton

        fun bind(menuItem: AllMenu, position: Int) { // Add position parameter
            binding.apply {
                foodNameTextView.text = menuItem.foodName
                priceTextView.text = "$" + menuItem.foodPrice // Add "$" before price

                // Use itemQuantities for quantity
//                quantityTextView.text = itemQuantities[position].toString()
                quantityTextView.text = menuItem.foodQuantity

                val uriString = menuItem.foodImage
                val uri = try {
                    Uri.parse(uriString)
                } catch (e: Exception) {
                    Log.e("MenuItemAdapter", "Invalid image URI", e)
                    null
                }

                Glide.with(context)
                    .load(uri)
                    .placeholder(R.drawable.placeholder_image) // Replace with your placeholder
                    .error(R.drawable.error_image) // Replace with your error image
                    .into(foodImageView)

                // Add click listeners for plus and minus buttons
                minusButton.setOnClickListener {
                    decreaseQuantity(position)
                }
                plusButton.setOnClickListener {
                    increaseQuantity(position)
                }
            }
        }

        private fun increaseQuantity(position: Int) {
            val menuItem = menuList[position]
            val itemKey = menuItem.key // Assuming you have a 'key' property in your AllMenu model

            itemKey?.let { key ->
                val currentQuantity = menuItem.foodQuantity?.toIntOrNull() ?: 0 // Get current quantity from menuItem
                val newQuantity = currentQuantity + 1

                if (newQuantity <= 500) { // Limit quantity to 500
                    database.child("menu").child(key).child("foodQuantity").setValue(newQuantity.toString())
                        .addOnSuccessListener {
                            // Update quantityTextView after successful update in Firebase
                            binding.quantityTextView.text = newQuantity.toString()
                        }
                        .addOnFailureListener { e ->
                            Log.e("MenuItemAdapter", "Error updating quantity", e)
                            // Handle error, e.g., show a message to the user
                        }
                }
            }
        }

        private fun decreaseQuantity(position: Int) {val menuItem = menuList[position]
            val itemKey = menuItem.key // Assuming you have a 'key' property in your AllMenu model

            itemKey?.let { key ->
                val currentQuantity = menuItem.foodQuantity?.toIntOrNull() ?: 0 // Get current quantity from menuItem
                val newQuantity = currentQuantity - 1

                if (newQuantity >= 1) { // Minimum quantity is 1
                    database.child("menu").child(key).child("foodQuantity").setValue(newQuantity.toString())
                        .addOnSuccessListener {
                            // Update quantityTextView after successful update in Firebase
                            binding.quantityTextView.text = newQuantity.toString()
                        }
                        .addOnFailureListener { e ->
                            Log.e("MenuItemAdapter", "Error updating quantity", e)
                            // Handle error, e.g., show a message to the user
                        }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(position: Int) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        iconView.setImageResource(R.drawable.ic_info) // Replace with your info icon
        titleView.text = "Confirm Delete"
        messageView.text = "Are you sure you want to delete this item?"

        val builder = AlertDialog.Builder(context, R.style.RoundedAlertDialog)
        builder.setView(dialogView)
        builder.setPositiveButton("Delete") { dialog, _ ->
            if (menuList.isNotEmpty() && position in 0 until menuList.size) {
                val itemToDelete = menuList[position]
                val itemKey = itemToDelete.key

                itemKey?.let { key ->
                    database.child("menu").child(key).removeValue()
                        .addOnSuccessListener {
                            if (position in 0 until menuList.size) {
                                menuList.removeAt(position)
                                notifyItemRemoved(position)
                                refreshItemsCallback.invoke()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("MenuItemAdapter", "Error deleting menu item", e)
                            // Show error message in a dialog or Toast
                        }
                }

                dialog.dismiss()
            } else {
                Log.e("MenuItemAdapter", "Invalid position or empty list")
                // You might want toshow a message to the user here
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)
        dialog.show()

        val layoutParams = dialog.window?.attributes
        layoutParams?.width = context.resources.displayMetrics.widthPixels - (32 * 2).dp() // Use context.resources
        dialog.window?.attributes = layoutParams
    }

    private fun Int.dp(): Int = (this * context.resources.displayMetrics.density + 0.5f).toInt()
}