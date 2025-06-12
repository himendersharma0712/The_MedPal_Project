package com.example.medpal

import android.app.VoiceInteractor.Prompt
import coil.request.ImageRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ChatApi {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatRequest) : ChatResponse

    @Multipart
    @POST("upload-file")  // Adjust your endpoint URL accordingly
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: RequestBody,
        @Part("chat_id") chatId: RequestBody,
        @Part("mime_type") mimeType: RequestBody
    ): retrofit2.Response<FileUploadResponse>

}

data class ChatRequest(val message: String)
data class ChatResponse(val response: String)

data class FileUploadResponse(
    val url: String,
    val mimeType: String,
    val original_name: String
)

