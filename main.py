import json
import os
from fastapi import FastAPI, WebSocket, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from typing import Dict

from langchain_ollama.chat_models import ChatOllama
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.memory import ConversationTokenBufferMemory
from langchain.tools import tool
from langchain.agents import initialize_agent
from langchain.tools import Tool
from langchain.agents import AgentType

UPLOAD_DIR = "uploaded_files"

# ------------------------- Globals -------------------------
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/files", StaticFiles(directory=UPLOAD_DIR), name="files")

# ------------------------- Models -------------------------
class ChatInput(BaseModel):
    message: str

# -------------------------- Tool Definition -------------------
def simple_echo_tool(message: str) -> str:
    """Echo a message. Just pass a string message as input. Do NOT use Python syntax."""
    return f"Echo: {message}"

tools = [
    Tool(
        name="simple_echo_tool",
        func=simple_echo_tool,
        description="Echo a message. Input should be a string message."
    )
]

# ------------------------- LLM & Memory Setup -------------------------
llm = ChatOllama(
    model="PetrosStav/gemma3-tools:4b",
    temperature=0.3,
    num_ctx=32768,
    keep_alive=-1,
    rope_scaling={"type": "yarn", "factor": 4.0, "original_max_position_embeddings": 32768}
)

memory = ConversationTokenBufferMemory(
    llm=llm,
    memory_key="chat_history",
    return_messages=True,
    input_key="input",
    output_key="output"
)

# ------------------------- System Prompt -------------------------
system_prompt = """

You are a Clinical AI assistant. Licensed to provide medical information.
Use tools when appropriate. Always prioritize patient safety. 
Don't use special characters like '*'. 

"""

# ------------------------- Agent Setup -------------------------------------
agent_executor = initialize_agent(
    tools=tools,
    llm=llm,
    agent= AgentType.CONVERSATIONAL_REACT_DESCRIPTION,
    verbose=True,
    memory=memory,
    handle_parsing_errors=True,
    agent_kwargs={
        "system_message": system_prompt,
        "input_variables": ["input", "chat_history", "agent_scratchpad"]
    }
)

# ------------------------- WebSocket Chat Integration -------------------------
@app.websocket("/ws/chat/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await websocket.accept()
    try:
        while True:
            raw_message = await websocket.receive_text()
            try:
                message_data = json.loads(raw_message)
                user_input = message_data.get("message", "").strip()
                
                if not user_input:
                    await websocket.send_text("Please enter a valid message")
                    continue

                result = await agent_executor.ainvoke({"input": user_input})
                await websocket.send_text(result["output"])

            except json.JSONDecodeError:
                await websocket.send_text("Invalid JSON format received ☠️")
            except Exception as e:
                print(f"Processing error: {e}")
                await websocket.send_text("Error processing request")

    except Exception as e:
        print(f"WebSocket connection error: {e}")
    finally:
        await websocket.close()

# ------------------------- File Upload Endpoint -------------------------
@app.post("/upload-file")
async def upload_file(
    file: UploadFile = File(...),
    user_id: str = Form(...),
    chat_id: str = Form(...),
    mime_type: str = Form(...)
):
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    file_path = os.path.join(UPLOAD_DIR, file.filename)
    content = await file.read()
    if not content:
        return {"error": "Empty file uploaded"}
    with open(file_path, "wb") as f:
        f.write(content)
    return {
        "url": f"http://localhost:8000/files/{file.filename}",
        "mime_type": mime_type or "application/octet-stream",
        "original_name": file.filename
    }
