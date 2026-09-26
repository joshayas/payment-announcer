package com.paymentannouncer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class DepositActivity : AppCompatActivity() {

    private var pickedFile: File? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) handlePickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deposit)

        val cbeRow = findViewById<android.widget.TextView>(R.id.cbeAccountRow)
        val telebirrRow = findViewById<android.widget.TextView>(R.id.telebirrAccountRow)
        val amountText = findViewById<android.widget.TextView>(R.id.depositAmount)
        val pickButton = findViewById<android.widget.Button>(R.id.pickScreenshotButton)
        val preview = findViewById<android.widget.ImageView>(R.id.screenshotPreview)
        val errorText = findViewById<android.widget.TextView>(R.id.depositError)
        val submitButton = findViewById<android.widget.Button>(R.id.submitDepositButton)

        // Pull the live deposit instructions from the backend rather than hardcoding them here,
        // so changing the account numbers server-side doesn't require an app update.
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me(SessionManager.bearer(this@DepositActivity))
                val instructions = me.body()?.depositInstructions
                if (instructions != null) {
                    amountText.text = "ETB ${instructions.amountEtb}"
                    cbeRow.text = "CBE: ${instructions.accounts["cbe"] ?: "—"}"
                    telebirrRow.text = "Telebirr: ${instructions.accounts["telebirr"] ?: "—"}"
                }
            } catch (_: Exception) {
                // Non-fatal: the driver can still proceed once they know the numbers another way.
            }
        }

        pickButton.setOnClickListener { pickImageLauncher.launch("image/*") }

        submitButton.setOnClickListener {
            val file = pickedFile
            if (file == null) {
                errorText.text = "Choose a screenshot first"
                return@setOnClickListener
            }
            errorText.text = ""
            lifecycleScope.launch {
                try {
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("screenshot", file.name, requestFile)
                    val response = ApiClient.api.uploadDeposit(SessionManager.bearer(this@DepositActivity), part)
                    if (response.isSuccessful) {
                        startActivity(Intent(this@DepositActivity, StatusActivity::class.java))
                        finish()
                    } else {
                        errorText.text = "Upload failed, please try again"
                    }
                } catch (e: Exception) {
                    errorText.text = "Couldn't reach the server: ${e.message}"
                }
            }
        }

        preview.setImageBitmap(null)
    }

    private fun handlePickedImage(uri: Uri) {
        val preview = findViewById<android.widget.ImageView>(R.id.screenshotPreview)
        contentResolver.openInputStream(uri)?.use { input ->
            val outFile = File(cacheDir, "deposit_screenshot.jpg")
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
            pickedFile = outFile
        }
        preview.setImageURI(uri)
        preview.visibility = android.view.View.VISIBLE
    }
}
