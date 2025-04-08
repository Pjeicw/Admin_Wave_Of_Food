package com.example.adminwaveoffood

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.bumptech.glide.Glide
import com.example.adminwaveoffood.databinding.ActivityAddItemBinding
import com.example.adminwaveoffood.model.AllMenu
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class AddItemActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddItemBinding
    private var foodImageUri: Uri? = null
    private lateinit var loadingDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup loading dialog
        setupLoadingDialog()

        // Initially disable the add item button and remove shadow
        binding.addItemButton.isEnabled = false
        setButtonShadow(false)

        binding.addItemButton.setOnClickListener {
            showLoadingDialog()
            addItem()
        }

        binding.selectImage.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.selectedImage.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Monitor text fields to enable the button when all fields are filled
        binding.foodName.addTextChangedListener { validateInput() }
        binding.foodPrice.addTextChangedListener { validateInput() }
        binding.foodQuantity.addTextChangedListener { validateInput() }
        binding.description.addTextChangedListener { validateInput() }
        binding.ingredint.addTextChangedListener { validateInput() }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun validateInput() {
        val isAllFieldsFilled = binding.foodName.text.isNotBlank() &&
                binding.foodPrice.text.isNotBlank() &&
                binding.foodQuantity.text.isNotBlank() &&
                binding.description.text.isNotBlank() &&
                binding.ingredint.text.isNotBlank() &&
                foodImageUri != null

        binding.addItemButton.isEnabled = isAllFieldsFilled
        setButtonShadow(isAllFieldsFilled)
    }

    private fun addItem() {
        val foodName = binding.foodName.text.toString().trim()
        val foodPrice = binding.foodPrice.text.toString().trim()
        val foodQuantity = binding.foodQuantity.text.toString().trim()
        val foodDescription = binding.description.text.toString().trim()
        val foodIngredient = binding.ingredint.text.toString().trim()

        val menuRef = FirebaseDatabase.getInstance().reference.child("menu")
        val newItemKey = menuRef.push().key

        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("menu_images/${newItemKey}.jpg")
        val uploadTask = imageRef.putFile(foodImageUri!!)

        uploadTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val newItem = AllMenu(
                        newItemKey,
                        foodName,
                        foodPrice,
                        foodDescription,
                        downloadUrl.toString(),
                        foodIngredient,
                        foodQuantity
                    )
                    newItemKey?.let { key ->
                        menuRef.child(key).setValue(newItem)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Item added successfully.", Toast.LENGTH_SHORT).show()
                                hideLoadingDialog()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                handleError("Failed to add item.", e)
                            }
                    }}.addOnFailureListener { e ->
                    handleError("Failed to retrieve image URL.", e)
                }
            } else {
                handleError("Failed to upload image.", task.exception)
            }
        }
    }

    private fun handleError(message: String, e: Exception?) {
        Log.e("AddItemActivity", message, e)
        showPopupMessage("Error", message, true, e?.message)
        hideLoadingDialog()
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                binding.selectedImage.setImageURI(uri)
                foodImageUri = uri
                validateInput()
            } else {
                Log.d("AddItemActivity", "No media selected")
            }
        }

    private fun showLoadingDialog() {
        loadingDialog.show()
    }

    private fun hideLoadingDialog() {
        if (loadingDialog.isShowing) {
            loadingDialog.dismiss()
        }
    }

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

    private fun showPopupMessage(
        title: String,
        message: String,
        isError: Boolean,
        logMessage: String?
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.centered_dialog, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.icon)
        val titleView = dialogView.findViewById<TextView>(R.id.title)
        val messageView = dialogView.findViewById<TextView>(R.id.message)

        val icon = if (isError) R.drawable.ic_error else R.drawable.ic_info
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

    private fun setButtonShadow(enabled: Boolean) {
        if (enabled) {
            binding.addItemButton.elevation = dp(8)
            binding.addItemButton.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.endColor)
            )
        } else {
            binding.addItemButton.elevation = 0f
            binding.addItemButton.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.gray)
            )
        }
    }

    private fun dp(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }
}