package com.mengoya.googlemessagescopy

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spannable
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Message(
    val text: String,
    val isSent: Boolean,
    val timestamp: Long
)

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val PREFS_NAME = "ChatMessages"
    private val MESSAGE_KEY = "messages"

    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messagesLayout: LinearLayout
    private lateinit var messagesScroller: ScrollView

    private var lastMessageTime: Long = 0
    private val dateFormatter = SimpleDateFormat("EE, d MMMM, HH:mm", Locale("ru"))
    private val timeOnly = SimpleDateFormat("HH:mm", Locale("ru"))

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://nwqsr0rz5earuiy2t8z8.tha.kz/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("accept-language", "ru-Ru")
                    .addHeader("x-short-token", "8a4aa42d-9aac-4f4b-bf59-964acea901d3")
                    .addHeader("x-ma-os", "1")
                    .addHeader("x-ma-version", "2.12.1")
                    .addHeader("x-ma-factory", "samsung")
                    .addHeader("x-ma-model", "SM-S908E")
                    .addHeader("x-ma-os-version", "30")
                    .addHeader("x-ma-d", "0be4d0c3-dfdc-4319-a649-167bde772a93")
                    .addHeader("content-type", "application/json; charset=UTF-8")
                    .addHeader("accept-encoding", "gzip")
                    .addHeader("user-agent", "okhttp/5.0.0-alpha.2")
                    .build()
                chain.proceed(request)
            }
            .build())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messagesLayout = findViewById(R.id.messagesLayout)
        messagesScroller = findViewById(R.id.messagesScroller)

        // Очищаем существующий layout перед загрузкой сообщений
        messagesLayout.removeAllViews()

        // Загружаем сохраненные сообщения
        val messages = loadMessages()
        if (messages.isEmpty()) {
            // Если сообщений нет, добавляем дефолтные
            val defaultTime1 = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .parse("30/10/2024 18:04")?.time ?: System.currentTimeMillis()

            addMessage("154887", true, defaultTime1)

            val defaultTime2 = defaultTime1 + 1000
            addMessage("ONAY! ALA\nAT 30/10 18:04\n57,113AJ02,120₸\nhttp://qr.tha.kz/7F597", false, defaultTime2)
        } else {
            // Отображаем сохраненные сообщения
            messages.forEach { message ->
                displayMessage(message)
            }
        }

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                addMessage(messageText, true)
                messageInput.text.clear()

                if (messageText.all { it.isDigit() }) {
                    makeApiRequest(messageText)
                }
            }
        }
    }

    private data class SavedMessage(
        val text: String,
        val isSent: Boolean,
        val timestamp: Long
    )

    private fun saveMessages(messages: List<SavedMessage>) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val messagesJson = Gson().toJson(messages)
        prefs.edit().putString(MESSAGE_KEY, messagesJson).apply()
    }

    private fun loadMessages(): List<SavedMessage> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val messagesJson = prefs.getString(MESSAGE_KEY, null) ?: return emptyList()
        return try {
            Gson().fromJson(messagesJson, object : TypeToken<List<SavedMessage>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages", e)
            emptyList()
        }
    }

    private fun displaySavedMessages() {
        val messages = loadMessages()
        messages.forEach { message ->
            addMessage(message.text, message.isSent, message.timestamp)
        }
    }

    private fun addTimeHeader(timestamp: Long) {
        val currentTime = timestamp
        val showFullHeader = lastMessageTime == 0L ||
                !isSameDay(Date(lastMessageTime), Date(currentTime)) ||
                (currentTime - lastMessageTime) > 3600000

        if (showFullHeader) {
            val headerView = TextView(this).apply {
                text = dateFormatter.format(Date(currentTime))
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
            }
            messagesLayout.addView(headerView)
        }

        lastMessageTime = currentTime
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun makeApiRequest(terminalId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val request = TransactionRequest(
                    pan = "9398101000016299155",
                    terminal = terminalId
                )
                val response = withContext(Dispatchers.IO) {
                    apiService.startTransaction(1, request)
                }

                if (response.success) {
                    response.result?.data?.terminal?.let { terminal ->
                        val formattedMessage = buildString {
                            append("ONAY! ALA\n")
                            append("AT ${getCurrentDateTime()}\n")
                            append("${terminal.route},${terminal.conductor},${terminal.cost?.div(100)}₸\n")
                            append("http://qr.tha.kz/${generateQrCode()}")
                        }
                        addMessage(formattedMessage, false)
                    }
                } else {
                    addMessage(response.message, false)
                }
            } catch (e: Exception) {
                addMessage("Ошибка при выполнении запроса: ${e.message}", false)
                Log.e(TAG, "API request failed", e)
            }
        }
    }

    private fun getCurrentDateTime(): String {
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun generateQrCode(): String {
        val chars = "0123456789ABCDEF".toCharArray()
        return buildString {
            repeat(5) {
                append(chars.random())
            }
        }
    }

    private fun addMessage(text: String, isSent: Boolean, timestamp: Long = System.currentTimeMillis()) {
        // Сохраняем новое сообщение
        val messages = loadMessages().toMutableList()
        messages.add(SavedMessage(text, isSent, timestamp))
        saveMessages(messages)

        // Отображаем сообщение
        displayMessage(SavedMessage(text, isSent, timestamp))
    }

    private fun deleteMessage(message: SavedMessage) {
        // Удаляем сообщение из сохраненного списка
        val messages = loadMessages().toMutableList()
        messages.removeAll { it.timestamp == message.timestamp && it.text == message.text }
        saveMessages(messages)

        // Перерисовываем все сообщения
        messagesLayout.removeAllViews()
        messages.forEach { displayMessage(it) }
    }

    private fun showPopupMenu(view: View, message: SavedMessage) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Удалить сообщение")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Удалить сообщение" -> {
                    deleteMessage(message)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun displayMessage(message: SavedMessage) {
        addTimeHeader(message.timestamp)

        val newMessage = TextView(this).apply {
            if (message.isSent && message.text.all { it.isDigit() }) {
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                text = message.text
            } else if (!message.isSent) {
                val spannableString = SpannableString(message.text)

                val timePattern = "\\d{2}:\\d{2}".toRegex()
                timePattern.find(message.text)?.let { matchResult ->
                    spannableString.setSpan(
                        UnderlineSpan(),
                        matchResult.range.first,
                        matchResult.range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                val urlPattern = "http://qr\\.tha\\.kz/[A-Z0-9]+".toRegex()
                urlPattern.find(message.text)?.let { matchResult ->
                    spannableString.setSpan(
                        UnderlineSpan(),
                        matchResult.range.first,
                        matchResult.range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                text = spannableString
            } else {
                text = message.text
            }

            textSize = 18f
            if (!message.isSent) {
                setLineSpacing(0f, 1.2f)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 0, 16, 10)
            }

            background = resources.getDrawable(
                if (message.isSent) R.drawable.message_sent_background
                else R.drawable.message_received_background,
                null
            )

            setOnLongClickListener { view ->
                showPopupMenu(view, message)
                true
            }
        }

        val containerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(5, 0, 5, 5)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (message.isSent) Gravity.END else Gravity.START
            layoutParams = containerParams
            addView(newMessage)
        }

        messagesLayout.addView(container)
        messagesScroller.post {
            messagesScroller.fullScroll(View.FOCUS_DOWN)
        }

    }
}