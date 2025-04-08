package com.example.adminwaveoffood

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.adminwaveoffood.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var binding: ActivityLoginBinding
    private lateinit var loadingDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Setup loading dialog
        setupLoadingDialog()

        // Email/Password Login
        binding.loginButton.setOnClickListener {
            val email = binding.email.text.toString().trim()
            val password = binding.password.text.toString().trim()
            if (email.isBlank() || password.isBlank()) {
                showPopupMessage(this, "Info", "Please fill in all details.")
            } else {
                showLoadingDialog() // Show loading dialog before login
                signInWithEmailAndPassword(email, password)
            }
        }

        // Facebook Sign In (Placeholder)
        binding.facebookButton.setOnClickListener {
            showPopupMessage(this, "Info", "Coming Soon.")
        }

        // Google Sign In
        binding.googleButton.setOnClickListener {
            signInWithGoogle()
        }

        // Forgot Password
        binding.forgotPasswordTextView.setOnClickListener {
            val email = binding.email.text.toString().trim()
            if (email.isBlank()) {
                showPopupMessage(this, "Info", "Please enter your email.")
            } else {
                sendPasswordResetEmail(email)
            }
        }

        // Navigate to Sign Up
        binding.dontHaveAccountButton.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun signInWithEmailAndPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                hideLoadingDialog() // Hide loading dialog when login completes
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    showPopupMessage(
                        this,
                        "Error",
                        "Login failed.",
                        true,
                        task.exception?.message
                    ) // Include log message
                }
            }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        resultLauncher.launch(signInIntent)
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task: Task<GoogleSignInAccount> =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            }
        }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(Exception::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: Exception) {
            showPopupMessage(this, "Error", "Google sign in failed.", true)
            Log.w("LoginActivity", "Google sign in failed", e)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    showPopupMessage(this, "Error", "Authentication failed.", true)
                    Log.w("LoginActivity", "signInWithCredential:failure", task.exception)
                }
            }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showPopupMessage(this, "Info", "Password reset email sent.")
                } else {
                    showPopupMessage(this, "Error", "Failed to send reset email.", true)
                    Log.w("LoginActivity", "sendPasswordResetEmail:failure", task.exception)
                }
            }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // Loading dialog methods
    private fun setupLoadingDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.loading_dialog, null)
        val gifImageView = dialogView.findViewById<ImageView>(R.id.loadingGif)

        // Load GIF using Glide
        Glide.with(this)
            .asGif()
            .load(R.drawable.loading)
            .into(gifImageView)

        val builder = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
        loadingDialog = builder.create()
        loadingDialog.setCancelable(false)

        // Set transparent background for the dialog
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showLoadingDialog() {
        loadingDialog.show()
    }

    private fun hideLoadingDialog() {
        if (loadingDialog.isShowing) {
            loadingDialog.dismiss()
        }
    }


    private fun showPopupMessage(
        context: Context,
        title: String,
        message: String,
        isError: Boolean = false,
        logMessage: String? = null
    ) { // Added logMessage parameter
        val dialogView = LayoutInflater.from(context).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        val icon = when {
            isError -> R.drawable.ic_error
            else -> R.drawable.ic_info
        }
        iconView.setImageResource(icon)
        titleView.text = title
        messageView.text =
            if (logMessage != null) "$message\n\nLog: $logMessage" else message // Include log message if available

        val builder = AlertDialog.Builder(context, R.style.RoundedAlertDialog)
        builder.setView(dialogView)

        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_container)
        dialog.show()

        // Customize button text size and dialog width
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.textSize = 20f // Set text size to 20sp

        val layoutParams = dialog.window?.attributes
        layoutParams?.width =
            resources.displayMetrics.widthPixels - (32 * 2).dp() // Set width with margins
        dialog.window?.attributes = layoutParams
    }

    // Extension function to convert dp to pixels
    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
}