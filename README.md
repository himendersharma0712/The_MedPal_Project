# 🏥 MedPal – AI-Powered Health Assistant App

**MedPal** is a next-gen Android application designed to support both physical and mental well-being using local AI models. It goes beyond basic health tracking by offering emotionally intelligent conversations, medical PDF interpretation, and tool-based reasoning—all wrapped in a sleek, WhatsApp-like chat UI.

## 💡 Features

- 🧠 **Conversational LLM Support**: Human-like chats using locally hosted LLMs via Ollama.
- 📄 **Medical Report Reader**: Upload PDFs or images—MedPal reads, interprets, and explains.
- 💊 **Symptom Checker & OTC Suggestions**: Diagnose common illnesses and suggest safe OTCs (non-replacement for a doctor).
- 🔋 **Offline AI**: No constant internet required! LLM runs locally using Ollama.
- 🧘‍♀️ **Mental Health Support**: Calm, conversational, and emotionally aware dialogue engine.
- 💬 **WhatsApp-style UI**: Familiar and smooth user interface built with Jetpack Compose.
- 🧰 **Tool-Based Reasoning**: From health calculators to file readers—tools amplify LLM capabilities.

## ⚙️ Tech Stack

| Layer        | Tech Details                                                                 |
|--------------|------------------------------------------------------------------------------|
| Frontend     | Android Studio, Kotlin, Jetpack Compose                                      |
| Backend      | FastAPI (Python)                                                             |
| AI Engine    | Ollama + LangChain agent with `gemma3-tools:4b`                              |
| Communication| WebSocket (chat) + HTTP (file transfer)                                      |

## 🚀 How It Works

1. The Android app sends messages and uploads files to a local FastAPI server.
2. The backend routes the request to a LangChain-powered agent.
3. The agent uses tools + LLM reasoning to generate an answer.
4. The app displays the response in a clean chat UI.

## 🙋‍♂️ Developed By

**Himender Sharma** – Lead Developer (Full-stack development, AI agent integration, backend/server setup, UI/UX design, debugging, complete documentation & final report writing)

Contributions: *Ginisha Miglani (HTML pages integration and presentation PPT)*

## 📎 Note

- This app is not intended to replace professional medical advice.
- Always consult a licensed healthcare provider for serious conditions.





