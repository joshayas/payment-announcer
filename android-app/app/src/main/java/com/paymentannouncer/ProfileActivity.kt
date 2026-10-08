package com.paymentannouncer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private var role = "driver"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val roleText = findViewById<android.widget.TextView>(R.id.profileRoleText)
        val nameInput = findViewById<android.widget.EditText>(R.id.profileNameInput)
        val shopNameInput = findViewById<android.widget.EditText>(R.id.profileShopNameInput)
        val plateInput = findViewById<android.widget.EditText>(R.id.profilePlateInput)
        val phoneText = findViewById<android.widget.TextView>(R.id.profilePhoneText)
        val saveError = findViewById<android.widget.TextView>(R.id.profileSaveError)
        val saveButton = findViewById<android.widget.Button>(R.id.profileSaveButton)

        val currentPasswordInput = findViewById<android.widget.EditText>(R.id.currentPasswordInput)
        val newPasswordInput = findViewById<android.widget.EditText>(R.id.newPasswordInput)
        val passwordError = findViewById<android.widget.TextView>(R.id.passwordError)
        val changePasswordButton = findViewById<android.widget.Button>(R.id.changePasswordButton)

        findViewById<android.widget.Button>(R.id.profileBackButton).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.logoutButton).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("You will need internet to log in again.")
                .setPositiveButton("Log out") { _, _ ->
                    SessionManager.clear(this)
                    val intent = android.content.Intent(this, LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun loadProfile() {
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.me(SessionManager.bearer(this@ProfileActivity))
                    val driver = response.body()?.driver ?: return@launch
                    role = driver.role
                    roleText.text = if (role == "merchant") "Merchant account · ነጋዴ" else "Driver account · ሹፌር"
                    nameInput.setText(driver.name)
                    phoneText.text = "Phone: ${driver.phone} (can't be changed)"
                    if (role == "merchant") {
                        shopNameInput.visibility = View.VISIBLE
                        shopNameInput.setText(driver.shopName ?: "")
                        plateInput.visibility = View.GONE
                    } else {
                        shopNameInput.visibility = View.GONE
                        plateInput.visibility = View.VISIBLE
                        plateInput.setText(driver.plate ?: "")
                    }
                } catch (e: Exception) {
                    saveError.text = "Couldn't load profile: ${e.message}"
                }
            }
        }
        loadProfile()

        saveButton.setOnClickListener {
            saveError.text = ""
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.updateProfile(
                        SessionManager.bearer(this@ProfileActivity),
                        UpdateProfileRequest(
                            name = nameInput.text.toString().trim().ifEmpty { null },
                            plate = if (role == "driver") plateInput.text.toString().trim() else null,
                            shopName = if (role == "merchant") shopNameInput.text.toString().trim().ifEmpty { null } else null,
                        )
                    )
                    if (response.isSuccessful) {
                        saveError.setTextColor(resources.getColor(R.color.mint, theme))
                        saveError.text = "Saved."
                    } else {
                        saveError.setTextColor(resources.getColor(R.color.danger, theme))
                        saveError.text = "Couldn't save changes"
                    }
                } catch (e: Exception) {
                    saveError.text = "Couldn't reach the server: ${e.message}"
                }
            }
        }

        changePasswordButton.setOnClickListener {
            passwordError.text = ""
            val current = currentPasswordInput.text.toString()
            val newPass = newPasswordInput.text.toString()
            if (current.isEmpty() || newPass.length < 6) {
                passwordError.text = "Enter your current password and a new one (6+ characters)"
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.changePassword(
                        SessionManager.bearer(this@ProfileActivity),
                        ChangePasswordRequest(current, newPass)
                    )
                    if (response.isSuccessful) {
                        currentPasswordInput.setText("")
                        newPasswordInput.setText("")
                        passwordError.setTextColor(resources.getColor(R.color.mint, theme))
                        passwordError.text = "Password updated."
                    } else {
                        passwordError.setTextColor(resources.getColor(R.color.danger, theme))
                        passwordError.text = "Current password is incorrect"
                    }
                } catch (e: Exception) {
                    passwordError.text = "Couldn't reach the server: ${e.message}"
                }
            }
        }
    }
}