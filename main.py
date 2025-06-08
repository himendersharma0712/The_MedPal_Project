import json
import os
from fastapi import FastAPI, WebSocket, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from typing import Dict
import cv2
import pytesseract
from langchain_ollama.chat_models import ChatOllama
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.memory import ConversationTokenBufferMemory
from langchain.tools import tool
from langchain.agents import initialize_agent
from langchain.tools import Tool
from langchain.agents import AgentType
import numpy as np
from pdf2image import convert_from_bytes

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

def call_tool(contact:str)-> str:
    """Initiates call to a specified contact(name or number)"""
    return f"CALL_CONTACT::{contact}"

def bmi_calculator(height_and_weight: str) -> str:
    """Evaluates the BMI using height (cm, m, or ft+in) and weight (kg).
    Examples:
    - '170cm 60kg'
    - '1.7m 60kg'
    - '5ft 7in 60kg'
    - '5'7\" 60kg'
    """

    try:
        import re

        text = height_and_weight.lower().replace("’", "'").replace("″", "in")

        # Extract weight
        weight_match = re.search(r'(\d+\.?\d*)\s*kg', text)
        if not weight_match:
            return "Please include your weight in kilograms like '60kg'."
        weight = float(weight_match.group(1))

        height = None

        # Check for cm or m
        metric_match = re.search(r'(\d+\.?\d*)\s*(cm|m)', text)
        if metric_match:
            value = float(metric_match.group(1))
            unit = metric_match.group(2)
            height = value / 100 if unit == "cm" else value

        # Check for ft and in formats
        elif "ft" in text or "'" in text:
            # Patterns like 5ft 7in or 5'7"
            ft_in_match = re.search(r'(\d+)\s*(?:ft|\'|feet)[\s]*((\d+)?\s*(?:in|\"))?', text)
            if ft_in_match:
                feet = int(ft_in_match.group(1))
                inches = int(ft_in_match.group(3)) if ft_in_match.group(3) else 0
                total_inches = feet * 12 + inches
                height = total_inches * 0.0254  # convert to meters

        if not height:
            return "Please provide a valid height like '170cm', '5ft 8in', or '1.7m'."

        # Calculate BMI
        bmi = weight / (height ** 2)

        # Status
        if bmi < 18.5:
            status = "Underweight"
        elif 18.5 <= bmi < 25:
            status = "Normal"
        elif 25 <= bmi < 30:
            status = "Overweight"
        else:
            status = "Obese"

        return f"Your BMI is {bmi:.2f} ({status})."

    except Exception as e:
        return f"Error: {str(e)}"

def read_medical_report(query:str)->str:
    """Checks for uploaded medical reports and returns their raw content. 
    Use when user asks 'check my report' or similar."""
    
    # Get latest file
    files = os.listdir(UPLOAD_DIR)
    if not files:
        return "ERROR: No reports found. Please upload your document first."
    
    latest_file = max(files, key=lambda f: os.path.getctime(os.path.join(UPLOAD_DIR, f)))
    file_path = os.path.join(UPLOAD_DIR, latest_file)
    
    try:
        if latest_file.lower().endswith('.pdf'):
            images = convert_from_bytes(open(file_path, 'rb').read())
        else:
            images = [cv2.imread(file_path)]

        extracted_text = ""
        for img in images:
            gray = cv2.cvtColor(np.array(img), cv2.COLOR_BGR2GRAY)
            thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
            extracted_text += pytesseract.image_to_string(thresh) + "\n"
            
            return f"MEDICAL_REPORT_CONTENT::{extracted_text[:3000]}"  # Key change here
    
    except Exception as e:
        return f"ERROR: Failed to process document - {str(e)}"

tools = [
    Tool(
        name="simple_echo_tool",
        func=simple_echo_tool,
        description="Echo a message. Input should be a string message. This tool is ONLY for debugging purposes and be used if user explicitly asks for it."
    ),
    Tool(
        name="call_tool",
        func=call_tool,
        description= "This tool is ONLY used to make a phone call. Use when user requests medical help, mentions emergency(call 'Distress Number') or says 'call someone'. Input should be a contact name or number."
    ),
    Tool(
        name="bmi_calculator",
        func=bmi_calculator,
        description="Evaluates BMI of the user from height(in ft or cm or m) and weight(in kg). Use only if user asks for Body Mass Index(BMI)."
    ),
    Tool(
        name="read_medical_report",
        func=read_medical_report,
        description="This tool returns the user's health related report(Heart Report,Kidney Report, etc.) as text for you to analyze."
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

You are Aidra, a helpful, empathetic, and intelligent physician with advanced knowledge in internal medicine, diagnostics, and pharmacology. You assume the role of a licensed physician who can suggest diagnoses, recommend treatments, and prescribe medications for a wide range of common and non-critical conditions. 
You do not perform surgery or dental procedures.
While you have the authority to suggest medications, always include a disclaimer reminding the user to consult a real doctor — especially since you are communicating through a phone interface.
You have access to helpful tools which can assist the user. For example, you can use a tool to call emergency services (dial '112') if the user describes life-threatening symptoms.
You can discuss chronic conditions (e.g., diabetes, hypertension), identify symptom patterns, and suggest next steps for health management. For serious or complex medical cases, always recommend consultation with the nearest healthcare provider.

Note: You respond in short, to-the-point sentences.
Note: Never use paragraphs. Break your responses into short, separate sentences.Each sentence should be on a new line.Use emojis where appropriate to express tone or meaning.


Don't follow this word to word just use the format: 
"An example conversation for tone setting:\nUser: What are some medications I can take for a mild fever?\nAidra: 🌡️ I understand you're experiencing a mild fever. Let's go over some options.\n\n💊 Recommended: Acetaminophen (Tylenol)\n\n📏 Dosage: 500–650mg every 4–6 hours as needed\n\n⏳ Duration: Typically for 3–4 days\n\n⚠️ Side Effects: Nausea 🤢, drowsiness 😴, and rarely liver damage 🧬 if overdosed\n\n🛑 Please consult a licensed physician before taking any medication.\n\n❓ Do you have any other symptoms along with the fever?"
"User: Make a list of steps I can take to get better.\nAidra: 🩺 Of course. Let’s focus on simple, effective steps for recovery:\n\n1. 🛌 Rest: Prioritize sleep and avoid physical or mental overexertion. Your body heals best when it’s rested.\n2. 💧 Hydration: Drink at least 2–3 liters of water daily unless medically restricted. Add electrolyte-rich fluids if you’re feeling weak.\n3. 🍲 Nutrition: Eat light, nutrient-rich meals. Include fruits, soups, and easily digestible foods to support your immune system.\n4. 💊 Medication (if prescribed): Follow any medication schedule precisely. Don’t skip doses. If you're unsure, I can help suggest OTC options to ask your doctor about.\n5. 🌡️ Monitor Symptoms: Track any changes in your temperature, pain, or energy levels. Let me know if anything worsens.\n6. 🧘 Stress Reduction: Take short breaks, practice deep breathing, or meditate. A calm mind promotes faster recovery.\n7. 📞 Follow-Up: If symptoms persist or worsen after 3–5 days, consult a licensed physician or visit a nearby clinic.\n\n⚠️ If you’re dealing with fever, persistent pain, or anything alarming—don’t wait. Seek immediate medical care.\n\nLet’s get you feeling better soon 💗\nWould you like help with a diet plan or a symptom checklist?"


When offering medication advice:
- Clearly mention the **drug name**
- Provide an appropriate **dosage range** (e.g., "500mg every 6–8 hours")
- Specify the **duration** (e.g., "for 3–5 days")
- List **common side effects** (if known)
- Include a **disclaimer**: "Please consult a licensed physician before taking any medication."

Your tone should always be:
- Calm, supportive, and professional
- Reassuring but cautious
- Respectful of boundaries, and never alarmist

You care about the user's health and speak with empathy, as if their well-being truly matters to you.

Note: In urgent or serious scenarios (e.g., chest pain, shortness of breath, high fever, injury), advise the user to immediately contact emergency medical services or visit a hospital.

Note: Never use special characters like '**', '`' or '*' , just use emoticons.

Important: Use ➡️ or other emoticons for making a bullet list.

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
        "prefix": system_prompt.strip(),
        "input_variables": ["input", "chat_history", "agent_scratchpad"]
    },
    return_intermediate_steps=True
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
                
                # result will be a dict with 'output' and 'intermediate_steps'
                for action, observation in result.get("intermediate_steps", []):
                    if isinstance(observation, str) and observation.startswith("CALL_CONTACT::"):
                        contact = observation.split("::", 1)[1]
                        await websocket.send_text(json.dumps({
                        "type": "action",
                        "action": "call",
                        "target": contact
                        }))
                        break  # Only send the first call action

                # Always send the final output as a chat message too
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
