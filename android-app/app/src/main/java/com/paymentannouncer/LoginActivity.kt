package com.paymentannouncer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (SessionManager.isLoggedIn(this)) {
            goToStatus()
            return
        }

        val phoneInput = findViewById<android.widget.EditText>(R.id.phoneInput)
        val passwordInput = findViewById<android.widget.EditText>(R.id.passwordInput)
        val errorText = findViewById<android.widget.TextView>(R.id.loginError)
        val loginButton = findViewById<android.widget.Button>(R.id.loginButton)
        val registerLink = findViewById<android.widget.TextView>(R.id.goToRegister)

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (phone.isEmpty() || password.isEmpty()) {
                errorText.text = "Enter your phone and password"
                return@setOnClickListener
            }
            errorText.text = ""
            lifecycleScope.launch {
                try {
                    val response = ApiClient.api.login(LoginRequest(phone, password))
                    if (response.isSuccessful && response.body() != null) {
                        SessionManager.saveToken(this@LoginActivity, response.body()!!.token)
                        goToStatus()
                    } else {
                        errorText.text = "Incorrect phone or password"
                    }
                } catch (e: Exception) {
                    errorText.text = "Couldn't reach the server: ${e.message}"
                }
            }
        }
    }

    private fun goToStatus() {
        startActivity(Intent(this, StatusActivity::class.java))
        finish()
    }
}
