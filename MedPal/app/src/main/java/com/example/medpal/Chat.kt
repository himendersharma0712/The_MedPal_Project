package com.example.medpal

import java.util.UUID

// Add this data class anywhere in your codebase (typically in a model package)
data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val profileImage: String? = null
)
