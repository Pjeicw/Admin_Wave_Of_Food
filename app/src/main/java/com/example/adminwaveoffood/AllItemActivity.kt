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
import com.example.adminwaveoffood.adapter.MenuItemAdapter
import com.example.adminwaveoffood.databinding.ActivityAllItemBinding
import com.example.adminwaveoffood.model.AllMenu
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AllItemActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAllItemBinding
    private lateinit var database: DatabaseReference
    private val menuItems: MutableList<AllMenu> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference

        binding.backButton.setOnClickListener {
            finish()
        }

        fetchMenuItems()
    }

    fun fetchMenuItems() {
        database.child("menu").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                menuItems.clear()
                for (itemSnapshot in snapshot.children) {
                    val menuItem = itemSnapshot.getValue(AllMenu::class.java)
                    menuItem?.let { menuItems.add(it) }
                }
                setAdapter()

                if (menuItems.isEmpty()) {
                    binding.noItemsTextView.visibility = View.VISIBLE  // Display a "No items" message
                    binding.MenuRecyclerView.visibility = View.GONE
                } else {
                    binding.noItemsTextView.visibility = View.GONE
                    binding.MenuRecyclerView.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AllItemActivity", "Error fetching menu items", error.toException())
                showErrorDialog("Error", "Failed to retrieve menu items.")
            }
        })
    }


    private fun setAdapter() {
        val adapter = MenuItemAdapter(
            this,
            menuItems,
            database,
            refreshItemsCallback = {
                // This will be called when a delete action finishes
                fetchMenuItems() // Refresh the items list
            },
            onDeleteClickListener = { position ->
                // Handle delete action here, e.g., show confirmation dialog
            }
        )
        binding.MenuRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.MenuRecyclerView.adapter = adapter
    }

    private fun showErrorDialog(title: String, message: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        iconView.setImageResource(R.drawable.ic_error)
        titleView.text = title
        messageView.text = message

        val builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
        builder.setView(dialogView)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)
        dialog.show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
}