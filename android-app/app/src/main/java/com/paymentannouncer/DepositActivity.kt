package com.paymentannouncer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val errorText = findViewById<android.widget.TextView>(R.id.depositError)
        val submitButton = findViewById<android.widget.Button>(R.id.submitDepositButton)

        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me(SessionManager.bearer(this@DepositActivity))
                val instructions = me.body()?.depositInstructions
                if (instructions != null) {
                    amountText.text = "ETB ${instructions.amountEtb}"
                    cbeRow.text = "CBE: ${instructions.accounts["cbe"] ?: "-"}"
                    telebirrRow.text = "Telebirr: ${instructions.accounts["telebirr"] ?: "-"}"
                }
            } catch (_: Exception) {
            }
        }

        pickButton.setOnClickListener { pickImageLauncher.launch("image/*") }

        fun resetSubmit() {
            submitButton.isEnabled = true
            submitButton.text = "Submit for approval"
        }

        submitButton.setOnClickListener {
            val file = pickedFile
            if (file == null) {
                errorText.setTextColor(resources.getColor(R.color.danger, theme))
                errorText.text = "Choose a screenshot first"
                return@setOnClickListener
            }
            errorText.setTextColor(resources.getColor(R.color.text_dim, theme))
            errorText.text = "Uploading... if the server was asleep this can take up to a minute."
            submitButton.isEnabled = false
            submitButton.text = "Uploading..."

            lifecycleScope.launch {
                try {
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("screenshot", file.name, requestFile)
                    val response = ApiClient.api.uploadDeposit(SessionManager.bearer(this@DepositActivity), part)
                    if (response.isSuccessful) {
                        startActivity(Intent(this@DepositActivity, StatusActivity::class.java))
                        finish()
                    } else {
                        resetSubmit()
                        errorText.setTextColor(resources.getColor(R.color.danger, theme))
                        errorText.text = "Upload failed, please try again"
                    }
                } catch (e: Exception) {
                    resetSubmit()
                    errorText.setTextColor(resources.getColor(R.color.danger, theme))
                    errorText.text = "Couldn't reach the server: ${e.message}"
                }
            }
        }
    }

    private fun handlePickedImage(uri: Uri) {
        val preview = findViewById<android.widget.ImageView>(R.id.screenshotPreview)
        val errorText = findViewById<android.widget.TextView>(R.id.depositError)
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { compressToJpeg(uri) }
            if (file == null) {
                errorText.setTextColor(resources.getColor(R.color.danger, theme))
                errorText.text = "Couldn't read that image, please choose another"
                return@launch
            }
            pickedFile = file
            errorText.text = ""
            preview.setImageURI(Uri.fromFile(file))
            preview.visibility = View.VISIBLE
        }
    }

    // Shrinks the picture to at most 1600px and saves it as a JPEG, so the upload is small and fast.
    private fun compressToJpeg(uri: Uri): File? {
        return try {
            val maxSide = 1600
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null

            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
            val finalBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap

            val out = File(cacheDir, "deposit_screenshot.jpg")
            FileOutputStream(out).use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            out
        } catch (e: Exception) {
            null
        }
    }
}