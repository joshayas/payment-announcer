package com.paymentannouncer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class StatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)

        findViewById<android.widget.Button>(R.id.refreshButton).setOnClickListener { loadStatus() }
        findViewById<android.widget.Button>(R.id.enterAppButton).setOnClickListener {
            startActivity(Intent(this, PaymentActivity::class.java))
            finish()
        }
        findViewById<android.widget.Button>(R.id.resubmitButton).setOnClickListener {
            startActivity(Intent(this, DepositActivity::class.java))
            finish()
        }
        findViewById<android.widget.Button>(R.id.statusProfileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        loadStatus()
    }

    override fun onResume() {
        super.onResume()
        loadStatus()
    }

    private fun loadStatus() {
        val icon = findViewById<android.widget.TextView>(R.id.statusIcon)
        val headline = findViewById<android.widget.TextView>(R.id.statusHeadline)
        val sub = findViewById<android.widget.TextView>(R.id.statusSub)
        val enterAppButton = findViewById<android.widget.Button>(R.id.enterAppButton)
        val resubmitButton = findViewById<android.widget.Button>(R.id.resubmitButton)

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.me(SessionManager.bearer(this@StatusActivity))

                if (response.code() == 401 || response.code() == 404) {
                    SessionManager.clear(this@StatusActivity)
                    startActivity(Intent(this@StatusActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }

                val driver = response.body()?.driver ?: return@launch

                when (driver.status) {
                    "pending_deposit" -> {
                        startActivity(Intent(this@StatusActivity, DepositActivity::class.java))
                        finish()
                    }
                    "pending_approval" -> {
                        icon.text = "⏳"
                        headline.text = "Waiting for admin approval"
                        sub.text = "Usually reviewed within 12 hours."
                        enterAppButton.visibility = android.view.View.GONE
                        resubmitButton.visibility = android.view.View.GONE
                    }
                    "approved" -> {
                        icon.text = "✓"
                        headline.text = "Approved — you're verified"
                        sub.text = "You can now use the payment screen."
                        enterAppButton.visibility = android.view.View.VISIBLE
                        resubmitButton.visibility = android.view.View.GONE
                    }
                    "rejected" -> {
                        icon.text = "✕"
                        headline.text = "Screenshot rejected"
                        sub.text = driver.rejectionNote ?: "Please resubmit a clearer screenshot."
                        enterAppButton.visibility = android.view.View.GONE
                        resubmitButton.visibility = android.view.View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                headline.text = "Couldn't check status"
                sub.text = e.message ?: "Check your connection and try again"
            }
        }
    }
}