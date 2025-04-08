package com.example.adminwaveoffood

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adminwaveoffood.adapter.PendingOrderAdapter
import com.example.adminwaveoffood.databinding.ActivityPendingOrderBinding
import com.example.adminwaveoffood.model.OrderDetails
import com.google.firebase.database.*
import androidx.activity.viewModels

class PendingOrderActivity : AppCompatActivity(), PendingOrderAdapter.OnItemClicked {
    private lateinit var binding: ActivityPendingOrderBinding
    private var listOfName: MutableList<String> = mutableListOf()
    private var listOfTotalPrice: MutableList<String> = mutableListOf()
    private var listOfImageFirstFoodOrder: MutableList<String> = mutableListOf()
    private var listOfOrderItem: ArrayList<OrderDetails> = arrayListOf()
    private lateinit var database: FirebaseDatabase
    private lateinit var databaseOrderDetails: DatabaseReference
    private lateinit var adapter: PendingOrderAdapter
    private var newOrderCount = 0 // Counter for new orders
    private val NOTIFICATION_PERMISSION_REQUEST_CODE =123

    private val notifiedOrderKeys = mutableSetOf<String>()

    private val notificationViewModel: NotificationViewModel by viewModels()

    // Notification channel ID
    private val CHANNEL_ID = "new_order_channel"

    // SharedPreferences for notification count
    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase database and reference
        database = FirebaseDatabase.getInstance()
        databaseOrderDetails = database.reference.child("OrderDetails")

        adapter = PendingOrderAdapter(this, listOfName, listOfTotalPrice, listOfImageFirstFoodOrder, listOfOrderItem, this)
        binding.pendingOrderRecyclerView.adapter = adapter
        binding.pendingOrderRecyclerView.layoutManager = LinearLayoutManager(this)

        // Fetch order details from the database
        getOrdersDetails()


        binding.backButton.setOnClickListener {
            finish() // Close the activity when back button is pressed
        }

        // Create notification channel
        createNotificationChannel()

        // ChildEventListener for real-time updates
        databaseOrderDetails.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val orderDetails = snapshot.getValue(OrderDetails::class.java)
                orderDetails?.let {
                    // Check if the order already exists in the list using itemPushKey
                    val existingOrder = listOfOrderItem.find { existing -> existing.itemPushKey == it.itemPushKey }
                    if (!notifiedOrderKeys.contains(it.itemPushKey)) {
                        listOfOrderItem.add(it)
                        listOfName.add(it.userName ?: "")
                        listOfTotalPrice.add(it.totalPrice ?: "")
                        it.foodImages?.firstOrNull()?.let { image -> listOfImageFirstFoodOrder.add(image) }

                        // Show notification for the new order
                        showNotificationForNewOrder(it)

                        // Add the order key to the notified set
                        notifiedOrderKeys.add(it.itemPushKey!!)

                        // Update UI
                        binding.noItemsTextView.visibility = if (listOfOrderItem.isEmpty()) View.VISIBLE else View.GONE
                        adapter.notifyItemInserted(listOfOrderItem.size - 1)

                        // Update badge count
                        updateBadgeCount()
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle data changes (if needed)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Handle data removal (if needed)
                val removedOrder = snapshot.getValue(OrderDetails::class.java)
                removedOrder?.let {
                    val position = listOfOrderItem.indexOfFirst { it.itemPushKey == removedOrder.itemPushKey }
                    if (position != -1) {
                        listOfOrderItem.removeAt(position)
                        listOfName.removeAt(position)
                        listOfTotalPrice.removeAt(position)
                        listOfImageFirstFoodOrder.removeAt(position)
                        adapter.notifyItemRemoved(position)
                    }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle data movement (if needed)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PendingOrderActivity", "Error getting order details", error.toException())
                showPopupMessage("Error", "Failed to load orders", true, error.message)
            }
        })
    }

    private fun getOrdersDetails() {
        // Clear existing lists and notify adapter
        listOfOrderItem.clear()
        listOfName.clear()
        listOfTotalPrice.clear()
        listOfImageFirstFoodOrder.clear()
        adapter.notifyDataSetChanged()
        // Check if the list is empty
        binding.noItemsTextView.visibility = if (listOfOrderItem.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNotificationForNewOrder(orderDetails: OrderDetails) {
        val intent = Intent(this, PendingOrderActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationId = System.currentTimeMillis().toInt()

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.bell)
            .setContentTitle("New Order Received")
            .setContentText("From: ${orderDetails.userName}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        // Check notification permission (for Android 13+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(this)) {
                // Check if notifications are enabled
                if (areNotificationsEnabled()) {
                    // Post notification
                    notify(notificationId, notificationBuilder.build())
                } else {
                    // Notification might be suppressed due to Do Not Disturb mode
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            // Post the notification
                            notify(notificationId, notificationBuilder.build())
                        } else {
                            // Inform the user that notifications are suppressed due to Do Not Disturb
                            Toast.makeText(this@PendingOrderActivity, "Notifications suppressed due to Do Not Disturb mode.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // For older versions, simply show a toast
                        Toast.makeText(this@PendingOrderActivity, "Notifications might be suppressed.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            // Request permission for notifications
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
        notificationViewModel.notifyNewOrder()
    }

    private fun updateBadgeCount() {
        val newCount = sharedPreferences.getInt("newOrderCount", 0) + 1
        sharedPreferences.edit().putInt("newOrderCount", newCount).apply()
        newOrderCount = newCount
        invalidateOptionsMenu()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = Color.RED
                        enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onItemClickListener(position: Int) {
        val intent = Intent(this, OrderDetailsActivity::class.java)
        val userOrderDetails = listOfOrderItem[position]
        intent.putExtra("UserOrderDetails", userOrderDetails)
        startActivity(intent)
    }

    override fun onItemAcceptClickListener(position: Int, foodId: String, userUid: String) {
        val childItemPushKey = listOfOrderItem[position].itemPushKey
        val clickItemOrderReference = childItemPushKey?.let {
            database.reference.child("OrderDetails").child(it)
        }

        clickItemOrderReference?.child("orderAccepted")?.setValue(true)
            ?.addOnSuccessListener {updateOrderAcceptStatus(position)
                showSuccessMessage("Order accepted")
            }
            ?.addOnFailureListener { error ->
                Log.e("PendingOrderActivity", "Error accepting order", error)
                showPopupMessage("Error", "Failed to accept order", true, error.message)
            }
    }

    @SuppressLint("ResourceType")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.drawable.menu_pending_orders, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val notificationItem = menu?.findItem(R.id.action_notifications)
        if (newOrderCount > 0) {
            notificationItem?.setIcon(R.drawable.bell)
        } else {
            notificationItem?.setIcon(R.drawable.bell)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onItemDispatchClickListener(position: Int) {
        val dispatchItemPushKey = listOfOrderItem[position].itemPushKey
        val orderDetailsToDispatch = listOfOrderItem[position]

        orderDetailsToDispatch.orderAccepted = true

        val dispatchItemOrderReference = database.reference.child("CompletedOrder").child(dispatchItemPushKey!!)
        dispatchItemOrderReference.setValue(orderDetailsToDispatch)
            .addOnSuccessListener {
                deleteThisItemFromOrderDetails(dispatchItemPushKey)
            }
            .addOnFailureListener { error ->
                Log.e("PendingOrderActivity", "Error dispatching order", error)
                showPopupMessage("Error", "Failed to dispatch order", true, error.message)
            }
    }

    private fun deleteThisItemFromOrderDetails(dispatchItemPushKey: String) {
        val orderDetailsItemsReference = database.reference.child("OrderDetails").child(dispatchItemPushKey)
        orderDetailsItemsReference.removeValue()
            .addOnSuccessListener {
                showSuccessMessage("Order is dispatched")
                recreate()
            }
            .addOnFailureListener { error ->
                Log.e("PendingOrderActivity", "Error deleting order", error)
                showPopupMessage("Error", "Failed to dispatch order", true, error.message)
            }
    }

    private fun updateOrderAcceptStatus(position: Int) {
        val userIdOfClickedItem = listOfOrderItem[position].userUid
        val pushKeyOfClickedItem = listOfOrderItem[position].itemPushKey
        val buyHistoryReference = database.reference.child("user").child(userIdOfClickedItem!!).child("BuyHistory").child(pushKeyOfClickedItem!!)
        buyHistoryReference.child("orderAccepted").setValue(true)
        databaseOrderDetails.child(pushKeyOfClickedItem).child("orderAccepted").setValue(true)
    }

    private fun showPopupMessage(title: String, message: String, isError: Boolean, logMessage: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        titleView.text = title
        messageView.text = message
        if (isError) {
            iconView.setImageResource(R.drawable.ic_error)
        } else {
            iconView.setImageResource(R.drawable.ic_info)
        }

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val alertDialog = dialogBuilder.create()
        alertDialog.show()

        logMessage?.let { Log.e("PopupMessage", it) }
    }

    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}