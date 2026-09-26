package com.paymentannouncer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val nameInput = findViewById<android.widget.EditText>(R.id.nameInput)
        val phoneInput = findViewById<android.widget.EditText>(R.id.phoneInput)
        val plateInput = findViewById<android.widget.EditText>(R.id.plateInput)
        val passwordInput = findViewById<android.widget.EditText>(R.id.passwordInput)
        val errorText = findViewById<android.widget.TextView>(R.id.registerError)
        val registerButton = findViewById<android.widget.Button>(R.id.registerButton)

        registerButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val plate = plateInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (name.isEmpty() || phone.isEmpty() || password.length < 6) {
                errorText.text = "Name, phone and a password of at least 6 characters are required"
                return@setOnClickListener
            }
            errorText.text = ""

            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.register(
                        RegisterRequest(name, phone, plate.ifEmpty { null }, password)
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
