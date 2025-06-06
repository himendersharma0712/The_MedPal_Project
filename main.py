import json
from fastapi import FastAPI, WebSocket, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from langchain_ollama.chat_models import ChatOllama
from langchain.prompts import ChatPromptTemplate,MessagesPlaceholder
from langchain.memory import ConversationTokenBufferMemory
import os
from fastapi.staticfiles import StaticFiles
from typing import Dict


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

# ------------------------- LLM Setup -------------------------
llm = ChatOllama(
    model="PetrosStav/gemma3-tools:4b",
    temperature=0.3,
    num_ctx=32768,
    keep_alive=-1,
    rope_scaling={"type": "yarn", "factor": 4.0, "original_max_position_embeddings": 32768}
)

memory = ConversationTokenBufferMemory(
    llm=llm,
    max_token_limit=30000,
    return_messages=True,
    memory_key="history",
    input_key="input",
    output_key="output"
)

system_prompt = """

You are a Clinical AI assistant who can diagnose symptoms and prescribe medicines to the user.

"""

prompt = ChatPromptTemplate.from_messages([
    ("system", system_prompt),
    MessagesPlaceholder(variable_name="history"),
    ("human", "{input}"),
])


# ------------------------- WebSocket Chat Integration -------------------------
@app.websocket("/ws/chat/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await websocket.accept()

    try:

        while True:
            raw_message = await websocket.receive_text()

            try:
                message_data: Dict[str, str] = json.loads(raw_message)
                user_input = message_data.get("message", "").strip()

                if not user_input:
                    continue
                
                formatted_messages = await prompt.aformat_messages(
                    input=user_input,
                    history=memory.load_memory_variables({})["history"]
                )

                full_response = ""
                async for chunk in llm.astream(formatted_messages):
                    full_response += str(chunk.content)

                memory.save_context({"input": user_input}, {"output": full_response})
                await websocket.send_text(full_response)

            except json.JSONDecodeError:
                await websocket.send_text("Invalid JSON format received ☠️")
                continue

    except Exception as e:
        print(f"WebSocket error: {e}")
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

