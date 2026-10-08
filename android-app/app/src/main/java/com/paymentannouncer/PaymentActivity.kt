package com.paymentannouncer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var lang: String = "en"
    private var soundMode: String = "voice"
    private var lastPayment: ParsedPayment? = null
    private val history = mutableListOf<ParsedPayment>()

    private var todayTotal: Double = 0.0
    private var todayCount: Int = 0

    private lateinit var statusChip: TextView
    private lateinit var senderNameText: TextView
    private lateinit var amountText: TextView
    private lateinit var metaText: TextView
    private lateinit var historyLabel: TextView
    private lateinit var historyScroll: View
    private lateinit var historyContainer: LinearLayout
    private lateinit var soundModeButton: Button
    private lateinit var dailyTotalText: TextView

    private val smsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                statusChip.text = if (lang == "am") "ክፍያ በመጠበቅ ላይ" else getString(R.string.waiting_for_payment)
            } else {
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        lang = SessionManager.getLanguage(this)
        soundMode = SessionManager.getSoundMode(this)
        tts = TextToSpeech(this, this)

        statusChip = findViewById(R.id.statusChip)
        senderNameText = findViewById(R.id.senderNameText)
        amountText = findViewById(R.id.amountText)
        metaText = findViewById(R.id.metaText)
        historyLabel = findViewById(R.id.historyLabel)
        historyScroll = findViewById(R.id.historyScroll)
        historyContainer = findViewById(R.id.historyContainer)
        soundModeButton = findViewById(R.id.soundModeButton)
        dailyTotalText = findViewById(R.id.dailyTotalText)

        updateSoundModeButtonLabel()

        findViewById<Button>(R.id.langEnButton).setOnClickListener { setLang("en") }
        findViewById<Button>(R.id.langAmButton).setOnClickListener { setLang("am") }
        soundModeButton.setOnClickListener { cycleSoundMode() }
        findViewById<Button>(R.id.profileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<Button>(R.id.repeatButton).setOnClickListener {
            lastPayment?.let { announce(it) }
        }

        findViewById<Button>(R.id.testSmsButton).setOnClickListener {
            val names = listOf("Sample Passenger", "Demo Rider", "Test User", "Sample Customer")
            val amounts = listOf(50.0, 75.0, 120.0, 200.0, 35.0)
            val sample = ParsedPayment(
                amount = amounts.random(),
                senderName = names.random(),
                maskedPhone = "2519****2144",
                txnDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date()),
                txnId = "TEST${(1000..9999).random()}",
                balanceAfter = null,
            )
            onPaymentReceived(sample)
        }

        loadTodayTotal()
        ensureSmsPermissions()
    }

    override fun onResume() {
        super.onResume()
        PaymentEventBus.setListener { payment -> runOnUiThread { onPaymentReceived(payment) } }
        loadTodayTotal()
    }

    private fun loadTodayTotal() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.todayTotal(SessionManager.bearer(this@PaymentActivity))
                val body = response.body() ?: return@launch
                todayTotal = body.totalAmount
                todayCount = body.count
                updateDailyTotalText()
            } catch (_: Exception) {
                // Non-fatal: keep whatever total is currently shown.
            }
        }
    }

    private fun updateDailyTotalText() {
        dailyTotalText.text = if (lang == "am") {
            "ዛሬ: ETB ${formatAmount(todayTotal)} · ${todayCount} ክፍያዎች"
        } else {
            "Today: ETB ${formatAmount(todayTotal)} · ${todayCount} payments"
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }

    private fun ensureSmsPermissions() {
        val missing = smsPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return

        val shouldExplainFirst = missing.any { shouldShowRequestPermissionRationale(it) }
        if (shouldExplainFirst) {
            AlertDialog.Builder(this)
                .setTitle("SMS access needed")
                .setMessage("This screen catches Telebirr payment texts automatically and announces them. Without SMS access, you'd have to check payments manually.")
                .setPositiveButton("Continue") { _, _ -> requestPermissionsLauncher.launch(missing.toTypedArray()) }
                .setNegativeButton("Not now", null)
                .show()
        } else {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("SMS permission is off")
            .setMessage("Payment Announcer can't detect payments automatically without SMS access. You can still use \"Simulate test SMS\", or turn on the permission anytime in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        PaymentEventBus.setListener(null)
    }

    override fun onInit(status: Int) {
        // TTS engine ready; nothing to do until a payment arrives.
    }

    private fun setLang(newLang: String) {
        lang = newLang
        SessionManager.saveLanguage(this, newLang)
        findViewById<Button>(R.id.langEnButton).backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                resources.getColor(if (newLang == "en") R.color.gold else R.color.panel, theme)
            )
        findViewById<Button>(R.id.langAmButton).backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                resources.getColor(if (newLang == "am") R.color.gold else R.color.panel, theme)
            )
        statusChip.text = if (lastPayment == null) {
            if (lang == "am") "ክፍያ በመጠበቅ ላይ" else getString(R.string.waiting_for_payment)
        } else {
            if (lang == "am") "ክፍያ ደርሷል" else getString(R.string.payment_received)
        }
        updateDailyTotalText()
    }

    private fun cycleSoundMode() {
        soundMode = when (soundMode) {
            "voice" -> "beep"
            "beep" -> "silent"
            else -> "voice"
        }
        SessionManager.saveSoundMode(this, soundMode)
        updateSoundModeButtonLabel()
    }

    private fun updateSoundModeButtonLabel() {
        soundModeButton.text = when (soundMode) {
            "voice" -> "🔊 Voice"
            "beep" -> "🔔 Beep"
            else -> "🔇 Silent"
        }
    }

    private fun onPaymentReceived(payment: ParsedPayment) {
        lastPayment?.let { previous ->
            history.add(0, previous)
            if (history.size > 20) history.removeAt(history.lastIndex)
        }

        lastPayment = payment
        statusChip.text = if (lang == "am") "ክፍያ ደርሷል" else getString(R.string.payment_received)

        senderNameText.text = payment.senderName
        senderNameText.setTextColor(resources.getColor(R.color.text, theme))

        amountText.visibility = View.VISIBLE
        amountText.text = "ETB ${payment.amount}"

        val metaParts = mutableListOf<String>()
        metaParts.add(if (payment.source == "cbe") "CBE" else "Telebirr")
        payment.txnDate?.let { metaParts.add(it) }
        payment.txnId?.let { metaParts.add("Txn $it") }
        metaText.text = metaParts.joinToString(" · ")

        todayTotal += payment.amount
        todayCount += 1
        updateDailyTotalText()

        renderHistory()
        announce(payment)
    }

    private fun renderHistory() {
        if (history.isEmpty()) {
            historyLabel.visibility = View.GONE
            historyScroll.visibility = View.GONE
            return
        }
        historyLabel.visibility = View.VISIBLE
        historyScroll.visibility = View.VISIBLE
        historyContainer.removeAllViews()

        for (item in history) {
            val row = TextView(this).apply {
                text = "${item.senderName}   ·   ETB ${item.amount}${item.txnDate?.let { "   ·   $it" } ?: ""}"
                setTextColor(resources.getColor(R.color.text_dim, theme))
                textSize = 15f
                setPadding(4, 14, 4, 14)
                gravity = Gravity.START
            }
            historyContainer.addView(row)
        }
    }

    private fun announce(payment: ParsedPayment) {
        when (soundMode) {
            "voice" -> if (payment.hasName) speak(payment) else speakNoName(payment)
            "beep" -> playBeep()
            "silent" -> { /* visual only */ }
        }
    }

    private fun playBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
        } catch (_: Exception) {
        }
    }

    private fun speak(payment: ParsedPayment) {
        val engine = tts ?: return
        val text = if (lang == "am") {
            "${payment.amount.toInt()} ብር ከ${payment.senderName} ደርሷል"
        } else {
            "Received ${payment.amount.toInt()} birr from ${payment.senderName}"
        }
        val locale = if (lang == "am") Locale("am", "ET") else Locale.US
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "payment_announcement")
    }

    private fun speakNoName(payment: ParsedPayment) {
        val engine = tts ?: return
        val text = if (lang == "am") {
            "${payment.amount.toInt()} ብር ደርሷል"
        } else {
            "Received ${payment.amount.toInt()} birr"
        }
        val locale = if (lang == "am") Locale("am", "ET") else Locale.US
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "payment_announcement")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}