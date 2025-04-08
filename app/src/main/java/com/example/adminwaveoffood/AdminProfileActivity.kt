package com.example.adminwaveoffood

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.adminwaveoffood.databinding.ActivityAdminProfileBinding
import com.example.adminwaveoffood.model.UserModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.database.*

class AdminProfileActivity : AppCompatActivity() {
    private val binding: ActivityAdminProfileBinding by lazy {
        ActivityAdminProfileBinding.inflate(layoutInflater)
    }
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var adminReference: DatabaseReference
    private lateinit var googleSignInClient: GoogleSignInClient

    private val REQUEST_CODE_GOOGLE_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        adminReference = database.reference.child("user")

        // Initialize Google Sign-In client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.backButton.setOnClickListener {
            finish()
        }

        // Initially disable input fields and save button
        setInputFieldsEnabled(false)
        binding.saveProfileButton.isEnabled = false

        binding.editButton.setOnClickListener {
            val isEditing = !binding.name.isEnabled // Toggle editing state
            setInputFieldsEnabled(isEditing)
            binding.saveProfileButton.isEnabled = isEditing
            if (isEditing) {
                binding.name.requestFocus()
            }
        }

        binding.saveProfileButton.setOnClickListener {
            updateUserData()
        }

        retrieveUserData()
    }

    private fun retrieveUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            getUserData(currentUser)
        } else {
            Log.e("AdminProfileActivity", "No user logged in.")
            showPopupMessage(this, "Error", "No user logged in.", true)
        }
    }

    private fun getUserData(user: FirebaseUser) {
        val userReference = adminReference.child(user.uid)

        userReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val userModel = snapshot.getValue(UserModel::class.java)
                    userModel?.let { setDataToTextView(it) }
                } else {
                    Log.e("AdminProfileActivity", "User data not found.")
                    showPopupMessage(
                        this@AdminProfileActivity,
                        "Error",
                        "User data not found.",
                        true
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AdminProfileActivity", "Error retrieving user data.", error.toException())
                showPopupMessage(
                    this@AdminProfileActivity,
                    "Error",
                    "Failed to retrieve user data.",
                    true,
                    error.message
                )
            }
        })
    }

    private fun setDataToTextView(userModel: UserModel) {
        binding.name.setText(userModel.name)
        binding.email.setText(userModel.email)
        binding.password.setText(userModel.password)
        binding.phone.setText(userModel.phone)
        binding.address.setText(userModel.address)
    }

    private fun setInputFieldsEnabled(enabled: Boolean) {
        binding.name.isEnabled = enabled
        binding.address.isEnabled = enabled
        binding.email.isEnabled = enabled
        binding.phone.isEnabled = enabled
        binding.password.isEnabled = enabled

        setButtonColor(enabled) // Set initial button color
    }

    private fun updateUserData() {
        val name = binding.name.text.toString().trim()
        val email = binding.email.text.toString().trim()
        val password = binding.password.text.toString().trim()
        val phone = binding.phone.text.toString().trim()
        val address = binding.address.text.toString().trim()

        if (name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank() || address.isBlank()) {
            showPopupMessage(this, "Error", "Please fill in all fields.", true)
            return
        }

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userReference = adminReference.child(currentUser.uid)
            val updatedData = hashMapOf(
                "name" to name,
                "email" to email,
                "password" to password,
                "phone" to phone,
                "address" to address
            )

            userReference.updateChildren(updatedData as Map<String, Any>).addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                setInputFieldsEnabled(false)
                binding.saveProfileButton.isEnabled = false
                setButtonColor(false)

                // Reauthenticate user before updating email and password
                if (currentUser.email != null) {
                    val credential = EmailAuthProvider.getCredential(currentUser.email!!, password)
                    reauthenticateWithEmail(currentUser, credential, email, password)
                } else {
                    reauthenticateWithGoogle(currentUser, email, password)
                }
            }.addOnFailureListener { e ->
                Log.e("AdminProfileActivity", "Error updating profile.", e)
                showPopupMessage(this, "Error", "Failed to update profile.", true, e.message)
            }
        } else {
            Log.e("AdminProfileActivity", "No user logged in.")
            showPopupMessage(this, "Error", "No user logged in.", true)
        }
    }

    private fun reauthenticateWithEmail(
        currentUser: FirebaseUser,
        credential: AuthCredential,
        email: String,
        password: String
    ) {
        currentUser.reauthenticate(credential).addOnSuccessListener {
            updateUserEmailAndPassword(currentUser, email, password)
        }.addOnFailureListener { e ->
            Log.e("AdminProfileActivity", "Error reauthenticating user.", e)
//            showPopupMessage(this, "Error", "Failed to reauthenticate user.", true, e.message)
        }
    }

    private fun reauthenticateWithGoogle(
        currentUser: FirebaseUser,
        email: String,
        password: String
    ) {
        googleSignInClient.silentSignIn().addOnCompleteListener(this) { signInTask ->
            if (signInTask.isSuccessful) {
                val account = signInTask.result
                val idToken = account?.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    currentUser.reauthenticate(credential).addOnSuccessListener {
                        updateUserEmailAndPassword(currentUser, email, password)
                    }.addOnFailureListener { e ->
                        Log.e("AdminProfileActivity", "Error reauthenticating user.", e)
                        showPopupMessage(
                            this,
                            "Error",
                            "Failed to reauthenticate user.",
                            true,
                            e.message
                        )
                    }
                }
            } else {
                googleSignInClient.signInIntent.also {
                    startActivityForResult(it, REQUEST_CODE_GOOGLE_SIGN_IN)
                }
            }
        }
    }

    private fun updateUserEmailAndPassword(
        currentUser: FirebaseUser,
        email: String,
        password: String
    ) {
        currentUser.updateEmail(email).addOnSuccessListener {
            currentUser.updatePassword(password).addOnSuccessListener {
                Log.d("AdminProfileActivity", "Email and password updated successfully.")
            }.addOnFailureListener { e ->
                Log.e("AdminProfileActivity", "Error updating password.", e)
                showPopupMessage(this, "Error", "Failed to update password.", true, e.message)
            }
        }.addOnFailureListener { e ->
            Log.e("AdminProfileActivity", "Error updating email.", e)
            showPopupMessage(this, "Error", "Failed to update email.", true, e.message)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    val idToken = account.idToken
                    if (idToken != null) {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        auth.currentUser?.reauthenticate(credential)?.addOnSuccessListener {
                            updateUserEmailAndPassword(
                                auth.currentUser!!,
                                binding.email.text.toString(),
                                binding.password.text.toString()
                            )
                        }?.addOnFailureListener { e ->
                            Log.e("AdminProfileActivity", "Error reauthenticating user.", e)
                            showPopupMessage(
                                this,
                                "Error",
                                "Failed to reauthenticate user.",
                                true,
                                e.message
                            )
                        }
                    }
                }
            } catch (e: ApiException) {
                Log.e("AdminProfileActivity", "Google sign-in failed.", e)
                showPopupMessage(this, "Error", "Google sign-in failed.", true, e.message)
            }
        }
    }

    private fun showPopupMessage(
        context: Context,
        title: String,
        message: String,
        isError: Boolean = false,
        logMessage: String? = null
    ) {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(context).inflate(R.layout.centered_dialog, null)

        // Get references to the views
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        // Set icon based on error or info
        val icon = if (isError) R.drawable.ic_error else R.drawable.ic_info
        iconView.setImageResource(icon)

        // Set title and message
        titleView.text = title
        messageView.text = if (logMessage != null) "$message\n\nLog: $logMessage" else message

        // Create and show the dialog
        val builder = AlertDialog.Builder(context, R.style.RoundedAlertDialog)
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)
        dialog.show()

        // Customize button text size and dialog width
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.textSize = 20f // Set text size to 20sp

        val layoutParams = dialog.window?.attributes
        layoutParams?.width = (resources.displayMetrics.widthPixels - (32 * 2).dp()) // Set width with margins
        dialog.window?.attributes = layoutParams
    }

    // Extension function to convert dp to pixels
    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()


    private fun setButtonColor(isActive: Boolean) {
        val background = if (isActive) {
            R.drawable.greenbuttongradient // Use gradient drawable for active state
        } else {
            R.drawable.graybuttonbackground // Use a different drawable for inactive state
        }
        binding.saveProfileButton.background = ContextCompat.getDrawable(this, background)
    }

}
