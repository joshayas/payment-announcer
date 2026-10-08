package com.paymentannouncer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private var role = "driver"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val nameInput = findViewById<android.widget.EditText>(R.id.nameInput)
        val shopNameInput = findViewById<android.widget.EditText>(R.id.shopNameInput)
        val phoneInput = findViewById<android.widget.EditText>(R.id.phoneInput)
        val plateInput = findViewById<android.widget.EditText>(R.id.plateInput)
        val passwordInput = findViewById<android.widget.EditText>(R.id.passwordInput)
        val errorText = findViewById<android.widget.TextView>(R.id.registerError)
        val registerButton = findViewById<android.widget.Button>(R.id.registerButton)
        val driverBtn = findViewById<android.widget.Button>(R.id.roleDriverButton)
        val merchantBtn = findViewById<android.widget.Button>(R.id.roleMerchantButton)

        fun applyRole() {
            if (role == "driver") {
                driverBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.gold, theme))
                driverBtn.setTextColor(resources.getColor(R.color.bg, theme))
                merchantBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.panel, theme))
                merchantBtn.setTextColor(resources.getColor(R.color.text, theme))
                nameInput.hint = "Full name"
                shopNameInput.visibility = View.GONE
                plateInput.visibility = View.VISIBLE
            } else {
                merchantBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.gold, theme))
                merchantBtn.setTextColor(resources.getColor(R.color.bg, theme))
                driverBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.panel, theme))
                driverBtn.setTextColor(resources.getColor(R.color.text, theme))
                nameInput.hint = "Owner's name"
                shopNameInput.visibility = View.VISIBLE
                plateInput.visibility = View.GONE
            }
        }

        driverBtn.setOnClickListener { role = "driver"; applyRole() }
        merchantBtn.setOnClickListener { role = "merchant"; applyRole() }
        applyRole()

        registerButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val shopName = shopNameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val plate = plateInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (name.isEmpty() || phone.isEmpty() || password.length < 6) {
                errorText.text = "Name, phone and a password of at least 6 characters are required"
                return@setOnClickListener
            }
            if (role == "merchant" && shopName.isEmpty()) {
                errorText.text = "Shop name is required for a merchant account"
                return@setOnClickListener
            }
            errorText.text = ""

            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.register(
                        RegisterRequest(
                            role = role,
                            name = name,
                            phone = phone,
                            plate = if (role == "driver") plate.ifEmpty { null } else null,
                            shopName = if (role == "merchant") shopName else null,
                            password = password,
                        )
                    )
                    if (response.isSuccessful && response.body() != null) {
                        SessionManager.saveToken(this@RegisterActivity, response.body()!!.token)
                        startActivity(Intent(this@RegisterActivity, DepositActivity::class.java))
                        finish()
                    } else {
                        errorText.text = "That phone number may already be registered"
                    }
                } catch (e: Exception) {
                    errorText.text = "Couldn't reach the server: ${e.message}"
                }
            }
        }
    }
}