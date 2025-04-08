package com.example.adminwaveoffood

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.adminwaveoffood.databinding.ActivityCreateUserBinding
import com.example.adminwaveoffood.model.UserModel
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class CreateUserActivity : AppCompatActivity() {
    private val binding: ActivityCreateUserBinding by lazy {
        ActivityCreateUserBinding.inflate(layoutInflater)
    }
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateButtonState()

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        userReference = database.reference.child("user")

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.createUserButton.setOnClickListener {
            if (areFieldsCompleted()) {
                createNewUser()
            } else {
                showPopupMessage(this, "Error", "Please fill in all fields.", true)
            }
        }

        addTextChangeListeners()
    }

    private fun addTextChangeListeners() {
        val fields = listOf(binding.name, binding.email, binding.address, binding.password)
        fields.forEach { field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = updateButtonState()
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    private fun areFieldsCompleted(): Boolean {
        val name = binding.name.text.toString().trim()
        val email = binding.email.text.toString().trim()
        val address = binding.address.text.toString().trim()
        val password = binding.password.text.toString().trim()
        return name.isNotBlank() && email.isNotBlank() && address.isNotBlank() && password.isNotBlank()
    }

    private fun updateButtonState() {
        val isActive = areFieldsCompleted()
        val background = if (isActive) {
            R.drawable.greenbuttongradient
        } else {
            R.drawable.graybuttonbackground
        }
        binding.createUserButton.background = ContextCompat.getDrawable(this, background)
    }

    private fun createNewUser() {
        val name = binding.name.text.toString().trim()
        val email = binding.email.text.toString().trim()
        val address = binding.address.text.toString().trim()
        val password = binding.password.text.toString().trim()

        if (name.isBlank() || email.isBlank() || address.isBlank() || password.isBlank()) {
            showPopupMessage(this, "Error", "Please fill in all fields.", true)
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("CreateUserActivity", "createUserWithEmail:success")
                    val newUser = auth.currentUser
                    if (newUser != null) {
                        // Save new user data
                        saveUserData(newUser.uid, name, email, address, password)
                    } else {
                        Log.e("CreateUserActivity", "Error: User is null after creation.")
                        showPopupMessage(this, "Error", "Failed to create user.", true)
                    }
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthWeakPasswordException -> "Password is too weak."
                        is FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                        is FirebaseAuthUserCollisionException -> "An account already exists with this email."
                        else -> "Account creation failed."
                    }
                    showPopupMessage(this, "Error", errorMessage, true, exception?.message)
                    Log.e("CreateUserActivity", "Account creation failed", exception)
                }
            }
    }

    private fun reauthenticateAdmin() {
        val currentAdmin = auth.currentUser
        if (currentAdmin == null) {
            Log.e("CreateUserActivity", "No current admin user to re-authenticate.")
            return
        }

        val credentials = getAdminCredentials()
        if (credentials == null) {
            Log.e("CreateUserActivity", "Admin credentials are missing.")
            return
        }

        val (email, password) = credentials
        val credential = EmailAuthProvider.getCredential(email, password)

        currentAdmin.reauthenticate(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("CreateUserActivity", "Admin re-authenticated successfully.")
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Invalid admin credentials."
                        else -> "Admin re-authentication failed."
                    }
                    showPopupMessage(this, "Error", errorMessage, true, exception?.message)
                    Log.e("CreateUserActivity", "Admin re-authentication failed", exception)
                }
            }
    }

    private fun saveAdminCredentials(email: String, password: String) {
        val prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("admin_email", email)
            putString("admin_password", password)
            apply()
        }
    }

    private fun getAdminCredentials(): Pair<String, String>? {
        val prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        val email = prefs.getString("admin_email", null)
        val password = prefs.getString("admin_password", null)
        return if (email != null && password != null) {
            Pair(email, password)
        } else {
            null
        }
    }

    private fun saveUserData(userId: String, name: String, email: String, address: String, password: String) {
        val user = UserModel(name = name, email = email, password = password, address = address)
        userReference.child(userId).setValue(user)
            .addOnSuccessListener {
                Log.d("CreateUserActivity", "User data saved successfully!")
                Toast.makeText(this, "User created successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("CreateUserActivity", "Error saving user data.", e)
                showPopupMessage(this, "Error", "Failed to save user data.", true, e.message)
            }
    }

    private fun showPopupMessage(
        context: Context,
        title: String,
        message: String,
        isError: Boolean,
        logMessage: String? = null
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        val icon = if (isError) R.drawable.ic_error else R.drawable.ic_info
        iconView.setImageResource(icon)
        titleView.text = title
        messageView.text = if (logMessage != null) "$message\n\nLog: $logMessage" else message

        val builder = AlertDialog.Builder(context, R.style.RoundedAlertDialog)
        builder.setView(dialogView)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)
        dialog.show()

        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.textSize = 20f

        val layoutParams = dialog.window?.attributes
        layoutParams?.width = context.resources.displayMetrics.widthPixels - (32 * 2).dp()
        dialog.window?.attributes = layoutParams
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
}
