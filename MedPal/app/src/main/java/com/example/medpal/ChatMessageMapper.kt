package com.example.medpal

// Casting chatMessage to ChatMessageEntity and vice-versa

fun ChatMessageEntity.toChatMessage() = ChatMessage(
    id = id,
    text = text,
    isUser = isUser,
    timestamp = timestamp,
    fileUrl = fileUrl,
    fileType = fileType,
    fileName = fileName
)

fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    text = text,
    isUser = isUser,
    timestamp = timestamp,
    fileUrl = fileUrl,
    fileType = fileType,
    fileName = fileName
)
