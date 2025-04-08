package com.example.adminwaveoffood

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseReference
import com.example.adminwaveoffood.databinding.ActivitySignUpBinding
import com.example.adminwaveoffood.model.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth and Database
        auth = Firebase.auth
        database = Firebase.database.reference

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)// Set up click listeners
        binding.facebookButton.setOnClickListener {
            showPopupMessage(this, "Info", "Coming Soon.")
        }

        binding.googleButton.setOnClickListener {
            signInWithGoogle()
        }

        binding.createUserButton.setOnClickListener {
            createUserWithEmailAndPassword()
        }

        binding.alreadyHaveAccountButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Set up location list
        val locationList = arrayOf(
            "Vientiane Prefecture",
            "Vientiane province",
            "Savannakhet province",
            "Salavan province",
            "Xiangkhouang province",
            "Oudomxay province",
            "Luang Prabang province",
            "Others"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, locationList)
        binding.listOfLocation.setAdapter(adapter)
    }

    private fun createUserWithEmailAndPassword() {
        val userName = binding.name.text.toString().trim()
        val nameOfRestaurant = binding.restaurantName.text.toString().trim()
        val email = binding.emailOrPhone.text.toString().trim()
        val password = binding.password.text.toString().trim()

        if (userName.isBlank() || nameOfRestaurant.isBlank() || email.isBlank() || password.isBlank()) {
            showPopupMessage(this, "Info", "Please fill in all details.")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveUserData(userName, nameOfRestaurant, email, password)
                    Toast.makeText(this,"Account created successfully.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    val exception = task.exception
                    val errorMessage = when {
                        exception is FirebaseAuthWeakPasswordException -> "Password is too weak."
                        exception is FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                        exception is FirebaseAuthUserCollisionException -> "An account already exists with this email."
                        else -> "Account creation failed."
                    }
                    showPopupMessage(this, "Error", errorMessage, true, exception?.message)
                    Log.e("SignUpActivity", "Account creation failed", exception)
                }
            }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    showPopupMessage(this, "Error", "Google sign-in failed.", true, e.message)
                    Log.e("SignUpActivity", "Google sign-in failed", e)
                }
            }
        }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (task.result.additionalUserInfo?.isNewUser == true) {
                        // Get user data from Google account
                        val displayName = user?.displayName ?: ""
                        val email = user?.email ?: ""
                        saveUserData(
                            displayName,
                            "",
                            email,
                            ""
                        ) // Assuming no restaurant name and password for Google sign-in:     showPopupMessage(this, "Success", "Account created successfully.")
                        Toast.makeText(this,"Account created successfully.",Toast.LENGTH_SHORT).show()
                    } else {
                        showPopupMessage(this, "Info", "Welcome back!")
                    }
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    showPopupMessage(
                        this,
                        "Error",
                        "Authentication failed.",
                        true,
                        task.exception?.message
                    )
                    Log.e("SignUpActivity", "Authentication failed", task.exception)
                }
            }
    }

    private fun saveUserData(
        userName: String,
        nameOfRestaurant: String,
        email: String,
        password: String
    ) {
        val user = UserModel(userName, nameOfRestaurant, email, password)
        auth.currentUser?.uid?.let { userId ->
            database.child("user").child(userId).setValue(user)
                .addOnFailureListener { e ->
                    showPopupMessage(this, "Error", "Failed to save user data: ${e.message}.", true)
                    Log.e("SignUpActivity", "User data saving failed", e)
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
        val dialogView = LayoutInflater.from(context).inflate(R.layout.centered_dialog, null)
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

        val builder = AlertDialog.Builder(context, R.style.RoundedAlertDialog)
        builder.setView(dialogView)

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

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