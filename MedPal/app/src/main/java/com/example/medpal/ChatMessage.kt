package com.example.medpal

import java.util.UUID

data class ChatMessage(
    val id:String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val fileUrl:String? = null,
    val fileType:String? = null,
    val fileName:String? = null
)