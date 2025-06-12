package com.example.medpal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id:String,
    val text:String,
    val isUser: Boolean,
    val timestamp:Long,
    val fileUrl: String? = null,
    val fileType: String? = null,
    val fileName: String? = null
)