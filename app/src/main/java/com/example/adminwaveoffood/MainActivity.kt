package com.example.adminwaveoffood

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.adminwaveoffood.databinding.ActivityMainBinding
import com.example.adminwaveoffood.model.OrderDetails
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var completedOrderReference: DatabaseReference
    private val CHANNEL_ID = "new_order_channel"
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 123

    private val notificationViewModel: NotificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)// Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        notificationViewModel.newNotification.observe(this) { isNewNotification ->
            if (isNewNotification) {
                showNotificationForNewOrder() // Show notification in MainActivity
                notificationViewModel.newNotification.value = false // Reset notification state
            }
        }

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Set up click listeners
        binding.addMenu.setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java))
        }

        binding.allItemMenu.setOnClickListener {
            startActivity(Intent(this, AllItemActivity::class.java))
        }

        binding.outForDeliveryButton.setOnClickListener {
            startActivity(Intent(this, OutForDeliveryActivity::class.java))
        }

        binding.profile.setOnClickListener {
            startActivity(Intent(this, AdminProfileActivity::class.java))
        }

        binding.createUser.setOnClickListener {
            startActivity(Intent(this, CreateUserActivity::class.java))
        }

        binding.pendingOrderTextView.setOnClickListener {
            startActivity(Intent(this, PendingOrderActivity::class.java))
        }

        binding.pendingOrderIcon.setOnClickListener {
            startActivity(Intent(this, PendingOrderActivity::class.java))
        }

        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // Fetch and display data
        pendingOrdersListener()
        completedOrdersListener()
        wholeTimeEarningsListener()
    }

    private fun showNotificationForNewOrder() {
        val intent = Intent(this, PendingOrderActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationId = System.currentTimeMillis().toInt()

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.bell) // Replace with your notification icon
            .setContentTitle("New Order Received")
            .setContentText("You have new pending orders!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Check notification permission before setting sound
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            // Handle permission denial (e.g., log a message or request permission)
            Log.w(
                "MainActivity",
                "Notification permission not granted. Sound will not be set."
            )
            // Request the permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notificationBuilder.build())
        }
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can now set the sound for the notification
                Log.d("MainActivity", "Notification permission granted.")
                // Show the notification again with sound
                showNotificationForNewOrder() // Or call a function to update the existing notification
            } else {
                // Permission denied, handle accordingly (e.g., show a message to the user)
                Log.w("MainActivity", "Notification permission denied.")
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        // Inflate custom layout for the dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.centered_dialog, null).apply {
            findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_info)
            findViewById<TextView>(R.id.title).text = "Confirm Logout"
            findViewById<TextView>(R.id.message).text = "Are you sure you want to log out?"
        }

        // Create and configure the dialog
        val dialog = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, SignUpActivity::class.java))
                finish()
            }
            .setNegativeButton("No", null)
            .create()

        // Set custom button styles and show the dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).textSize = 18f
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).textSize = 18f
        }
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)

        // Adjust dialog width
        dialog.show()
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            width = resources.displayMetrics.widthPixels - (32 * 2).dp()
        }
    }

    private fun pendingOrdersListener() {
        val database = FirebaseDatabase.getInstance()
        val pendingOrderReference = database.reference.child("OrderDetails")
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        pendingOrderReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pendingOrderItemCount = snapshot.childrenCount.toInt()
                binding.pendingOrders.text = pendingOrderItemCount.toString()

                val lastShownCount = sharedPrefs.getInt("last_shown_pending_count", 0)
                if (pendingOrderItemCount >= 1 && pendingOrderItemCount > lastShownCount) {showPopupMessage("New Order", "You have new pending orders!", false)
                    sharedPrefs.edit()
                        .putInt("last_shown_pending_count",pendingOrderItemCount).apply()
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error getting pending orders count", error.toException())
                showPopupMessage("Error", "Failed to retrieve pending orders count.", true, error.message)
            }
        })
    }

    private fun completedOrdersListener() {
        val database = FirebaseDatabase.getInstance()
        val completeOrderReference = database.reference.child("CompletedOrder")
        completeOrderReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val completeOrderItemCount = snapshot.childrenCount.toInt()
                binding.completeOrder.text = completeOrderItemCount.toString()
            }override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error getting completed orderscount", error.toException())
                showPopupMessage("Error", "Failed to retrieve completed orders count.", true, error.message)
            }
        })
    }

    private fun wholeTimeEarningsListener() {
        val listOfTotalPay = mutableListOf<Int>()
        val completedOrderReference = FirebaseDatabase.getInstance().reference.child("CompletedOrder")
        completedOrderReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listOfTotalPay.clear() // Clear the list before adding new data
                for (orderSnapshot in snapshot.children) {
                    val completeOrder = orderSnapshot.getValue(OrderDetails::class.java)
                    completeOrder?.totalPrice?.replace("$", "")?.toIntOrNull()?.let {
                        listOfTotalPay.add(it)
                    }
                }
                val totalEarnings = listOfTotalPay.sum()
                binding.wholeTimeEarning.text = "$totalEarnings$"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error getting total earnings", error.toException())
                showPopupMessage("Error", "Failed toretrieve earnings.", true, error.message)
            }
        })
    }

    private fun showPopupMessage(
        title: String,
        message: String,
        isError: Boolean,
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
        messageView.text = if (logMessage != null) "$message\n\nLog: $logMessage" else message

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

    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}