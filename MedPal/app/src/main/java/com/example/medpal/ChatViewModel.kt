package com.example.medpal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).chatMessageDao()
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    //region WebSocket Configuration
    private var webSocketService: WebSocketService? = null
    private val wsClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    //endregion

    //region HTTP Configuration (Existing)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(80, TimeUnit.SECONDS)
        .writeTimeout(80, TimeUnit.SECONDS)
        .build()

    private val apiService = Retrofit.Builder()
        .baseUrl("http://192.168.2.252:8000/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ChatApi::class.java)
    //endregion

    var isLoading by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            dao.getAllMessages().collectLatest { entities ->
                _messages.clear()
                _messages.addAll(entities.map { it.toChatMessage() })
            }
        }
    }

    //region WebSocket Methods
    fun initWebSocket(clientId: String) {
        if (webSocketService == null) {
            webSocketService = WebSocketService(
                clientId = clientId,
                client = wsClient,
                onMessageReceived = { message ->
                    viewModelScope.launch {
                        try {
                            val json = JSONObject(message)
                            if(json.optString("type")=="action"){
                                when(json.optString("action")){
                                    "call"-> {
                                        val target = json.optString("target")
                                        // Trigger phone call intent (see below)

                                        makePhoneCall(target)

                                        // add a system message to chat history
                                        val sysMsg = ChatMessage(text = "Calling $target...", isUser = false)
                                        dao.insertMessage(sysMsg.toEntity())
                                        isLoading = false

                                    }

                                    "meditate"->{
                                        val duration = json.optInt("duration", 5)
                                        val sound = json.optInt("sound", 1)
                                        startMeditation(duration, sound)
                                        val sysMsg = ChatMessage(
                                            text = "Starting $duration-minute meditation with sound #$sound...",
                                            isUser = false
                                        )
                                        dao.insertMessage(sysMsg.toEntity())
                                        isLoading = false
                                    }
                                }
                            }
                            else {
                                // Not an action, treat as normal chat message
                                val irisMsg = ChatMessage(text = message, isUser = false)
                                dao.insertMessage(irisMsg.toEntity())
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            // Not JSON, treat as normal chat message
                            val irisMsg = ChatMessage(text = message, isUser = false)
                            dao.insertMessage(irisMsg.toEntity())
                            isLoading = false
                        }
                    }
                }, onConnectionChanged = { isConnected ->
                    Log.d("WebSocket", "Connection state: $isConnected")
                }
            ).apply { connect() }
        }
    }

//    fun makePhoneCall(numberOrName: String) {
//        // If you expect a number, use directly. For contact names, resolve to number first.
//        val intent = Intent(Intent.ACTION_CALL).apply {
//            data = Uri.parse("tel:$numberOrName")
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        }
//        val context = getApplication<Application>().applicationContext
//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
//            context.startActivity(intent)
//        } else {
//            // Request permission from the user (handle this in your Activity/UI)
//        }
//    }


    // ----------------- Meditation Integration -----------------
    var isMeditating by mutableStateOf(false)
    var meditationTimeLeft by mutableStateOf(0) // seconds
    var meditationTotalTime by mutableStateOf(0) // seconds
    var meditationSoundIndex by mutableStateOf(1)
    private var meditationTimerJob: Job? = null
    private var meditationPlayer: MediaPlayer? = null

    fun startMeditation(durationMinutes: Int, soundIndex: Int) {
        stopMeditation()
        meditationSoundIndex = soundIndex.coerceIn(1, 10)
        meditationTotalTime = durationMinutes * 60
        meditationTimeLeft = meditationTotalTime
        isMeditating = true

        // Start timer
        meditationTimerJob = viewModelScope.launch {
            while (meditationTimeLeft > 0 && isMeditating) {
                delay(1000)
                meditationTimeLeft -= 1
            }
            stopMeditation()
        }

        // Play sound (looped)
        val context = getApplication<Application>().applicationContext
        val resId = context.resources.getIdentifier(
            "meditation$meditationSoundIndex", "raw", context.packageName
        )
        meditationPlayer = MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            start()
        }
    }

    fun stopMeditation() {
        isMeditating = false
        meditationTimerJob?.cancel()
        meditationTimerJob = null
        meditationPlayer?.stop()
        meditationPlayer?.release()
        meditationPlayer = null
        meditationTimeLeft = 0
    }


    @SuppressLint("Range")
    fun makePhoneCall(numberOrName: String) {
        val context = getApplication<Application>().applicationContext
        var phoneNumber = numberOrName

        // Check if input is not a valid phone number (simple regex, can be improved)
        if (!numberOrName.matches(Regex("^\\+?[0-9]{7,15}\$"))) {
            // Query Contacts database for the name
            val resolver = context.contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
                arrayOf(numberOrName),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    phoneNumber = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                } else {
                    // Handle contact not found
                    Log.e("PhoneCall", "Contact $numberOrName not found")
                    return
                }
            }
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            context.startActivity(intent)
        } else {
            // Request permission from the user (handle this in your Activity/UI)
        }
    }



    fun sendChatMessage(text: String) {
        if(webSocketService?.isConnected == true){
            Log.d("WebSocket","Sending message: $text")

            val jsonObject = JSONObject()
            jsonObject.put("message",text)

            webSocketService?.sendMessage(jsonObject.toString())

            val userMessage = ChatMessage(text=text, isUser = true)

            viewModelScope.launch {
                dao.insertMessage(userMessage.toEntity())
                isLoading = true
            }
        }

        else{
            Log.e("WebSocket", "Cannot send message - WebSocket not connected")
            initWebSocket("user123")
        }
    }

    override fun onCleared() {
        webSocketService?.disconnect()
        super.onCleared()
    }
    //endregion

    //region Existing HTTP Methods (UNCHANGED)
    fun addFileMessage(fileUri: String, fileType: String, fileName: String) {
        val message = ChatMessage(
            text = "",
            isUser = true,
            fileUrl = fileUri,
            fileType = fileType,
            fileName = fileName
        )
        if(fileUri.isNotBlank() && fileType.isNotBlank()) {
            viewModelScope.launch {
                dao.insertMessage(message.toEntity())
            }
        }
    }

    fun uploadFile(uri: Uri) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            try {
                val filePart = createMultipartFromUri(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val response = apiService.uploadFile(
                    filePart,
                    "user123".toRequestBody("text/plain".toMediaTypeOrNull()),
                    "chat456".toRequestBody("text/plain".toMediaTypeOrNull()),
                    mimeType.toRequestBody("text/plain".toMediaTypeOrNull())
                )
                if (response.isSuccessful) {
                    response.body()?.let {
                        // Handle file upload success if needed
                    }
                } else {
                    _messages.add(ChatMessage(
                        text = "Upload failed: ${response.code()}",
                        isUser = false
                    ))
                }
            } catch (e: Exception) {
                _messages.add(ChatMessage(
                    text = "Upload error: ${e.message}",
                    isUser = false
                ))
            }
        }
    }
    //endregion

    //region Helper Methods (UNCHANGED)
    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        return result ?: uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
    }

    private fun createMultipartFromUri(context: Context, uri: Uri): MultipartBody.Part {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open file stream")
        val fileBytes = inputStream.readBytes()
        val fileName = getFileName(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return MultipartBody.Part.createFormData(
            "file",
            fileName,
            fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )
    }
    //endregion


    fun deleteMessageById(messageId:String){
        viewModelScope.launch {
            dao.deleteMessageById(messageId)
        }
    }
}

// New WebSocketService class
private class WebSocketService(
    private val clientId: String,
    private val client: OkHttpClient,
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) : okhttp3.WebSocketListener() {

    private var webSocket: okhttp3.WebSocket? = null
    var isConnected: Boolean = false
        private set

    fun connect() {
        Log.d("WebSocket", "Attempting connection...")
        val request = okhttp3.Request.Builder()
//            .url("ws://192.168.29.28:8000/ws/chat/$clientId") // home wifi
            .url("ws://192.168.2.252:8000/ws/chat/$clientId")
            .build()
        webSocket = client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
        super.onOpen(webSocket, response)
        isConnected = true
        onConnectionChanged(true)
        Log.d("WebSocket", "Connected to server")
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        Log.d("WebSocket", "Received message: $text")
        onMessageReceived(text)
    }

    override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        isConnected = false
        onConnectionChanged(false)
        Log.d("WebSocket", "Closing: $code - $reason")
        webSocket.close(code, reason)
    }

    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
        isConnected = false
        onConnectionChanged(false)
        Log.e("WebSocket", "Connection failed: ${t.message}")
        // Auto-reconnect after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d("WebSocket", "Attempting reconnect...")
            connect()
        }, 5000)
    }

    fun sendMessage(text: String) {
        if (isConnected) {
            webSocket?.send(text)
            Log.d("WebSocket", "Message sent successfully")
        } else {
            Log.e("WebSocket", "Failed to send message - connection not ready")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User initiated disconnect")
        isConnected = false
    }
}
