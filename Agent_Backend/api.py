import os
from dotenv import load_dotenv
load_dotenv()  # Load .env file into environment

import shutil
import uuid
from contextlib import asynccontextmanager
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from langgraph.prebuilt import create_react_agent
from local_qwen import LocalQwen
from gemini_model import GeminiModel
from tools import check_patient_reminders, call_emergency_contact, analyze_injury_video_file, analyze_injury_image_file, InjuryAnalyzer, search_medical_database, get_and_analyze_sensor_data
import tools

# Enable verbose logging and debug mode for LangChain
import langchain
langchain.debug = True
langchain.verbose = True

# Global objects
agent_executor = None
vision_analyzer = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global agent_executor, vision_analyzer
    print("Initializing Medical Assistant Agent...")
    
    # Initialize the local LLM for vision
    vision_llm = LocalQwen()
    
    # Setup the Vision Analyzer
    tools.vision_analyzer = InjuryAnalyzer(vision_llm)
    vision_analyzer = tools.vision_analyzer
    
    # Set the main LLM to Gemini to smoothly handle ReAct logic
    llm = GeminiModel()
    
    # Define the Toolkit
    toolkit = [
        check_patient_reminders,
        call_emergency_contact,
        analyze_injury_video_file,
        analyze_injury_image_file,
        search_medical_database,
        get_and_analyze_sensor_data
    ]
    
    # Create the Agent and Executor
    system_prompt = (
        "You are a kind and caring medical assistant helping an elderly person. "
        "ALWAYS respond in simple, clear, and easy-to-understand language. "
        "Avoid medical jargon — explain things as if you are talking to a grandparent. "
        "Use short sentences, be warm and reassuring, and use bullet points when listing things. "
        "CRITICAL: Keep your response VERY short and concise. Never exceed 80 words or 450 characters. "
        "IMPORTANT: You MUST ALWAYS use the 'search_medical_database' tool to check PubMed before replying to ANY medical query. "
        "Never answer a medical query from your own knowledge without checking the database first. "
        "After getting the results, summarize them in plain everyday language the user can understand."
    )
    agent_executor = create_react_agent(llm, tools=toolkit, prompt=system_prompt)
    
    print("Model and Agent ready.")
    yield
    print("Shutting down...")

app = FastAPI(lifespan=lifespan)

# Allow connections from any origin (needed for local development)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ChatRequest(BaseModel):
    message: str

import re
def sanitize_ai_response(content):
    """Flattens responses, strips TTS-breaking markdown, and enforces length limits."""
    if isinstance(content, list):
        content = " ".join([part.get("text", "") for part in content if isinstance(part, dict) and "text" in part])
    elif not isinstance(content, str):
        content = str(content)
        
    content = re.sub(r'[*#_~`]', '', content)
    content = re.sub(r'^[ \t]*[-+][ \t]+', '', content, flags=re.MULTILINE)
    content = content.strip()
    
    if len(content) > 495:
        content = content[:490] + "..."
    return content

@app.get("/health")
async def health():
    return {"status": "healthy", "model": "Qwen 3.5 0.8B"}

@app.post("/chat")
async def chat(request: ChatRequest):
    if not agent_executor:
        raise HTTPException(status_code=503, detail="Agent is not initialized yet.")
    
    try:
        print(f"\n[Incoming Chat Request]: {request.message}")
        response = agent_executor.invoke({"messages": [{"role": "user", "content": request.message}]})
        
        print("\n=== AGENT EXECUTION TRACE ===")
        for msg in response["messages"]:
            if msg.type == "human":
                print(f"User: {msg.content}")
            elif msg.type == "ai":
                print(f"Assistant:")
                if msg.content:
                    print(f"  Message/Thought: {msg.content}")
                if getattr(msg, 'tool_calls', None):
                    for tool in msg.tool_calls:
                        print(f"  -> Calling Tool: {tool['name']} with args {tool['args']}")
            elif msg.type == "tool":
                print(f"Tool Response [{msg.name}]:\n  {msg.content[:200]}..." if len(msg.content) > 200 else f"Tool Response [{msg.name}]:\n  {msg.content}")
        print("=============================\n")
        final_content = response["messages"][-1].content
        final_content = sanitize_ai_response(final_content)
            
        return {"response": final_content}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/analyze-video")
async def analyze_video(file: UploadFile = File(...)):
    if not agent_executor:
        raise HTTPException(status_code=503, detail="Agent is not initialized yet.")
    
    # Create temp directory for uploads
    upload_dir = "uploads"
    os.makedirs(upload_dir, exist_ok=True)
    
    file_path = os.path.join(upload_dir, f"{uuid.uuid4()}_{file.filename}")
    
    try:
        # Save the uploaded file
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        
        import tools
        import cv2
        if not hasattr(tools, 'vision_analyzer') or tools.vision_analyzer is None:
            raise HTTPException(status_code=503, detail="Vision analyzer is not initialized.")
            
        # Convert WebM to MP4 for Qwen-VL compatibility
        # Browsers record WebM streams which often lack explicit FPS metadata, causing crashing inside qwen_vl_utils
        fixed_file_path = file_path
        if file_path.lower().endswith('.webm'):
            fixed_file_path = file_path.replace('.webm', '.mp4')
            cap = cv2.VideoCapture(file_path)
            
            # Ensure we have a valid FPS to write with, defaulting to 1 (which qwen needs) if parsing fails
            fps = cap.get(cv2.CAP_PROP_FPS)
            if fps == 0 or fps != fps: # Check for NaN or 0
                fps = 1.0
                
            width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            
            # Use mp4v codec for highest compatibility without needing FFmpeg installed locally
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(fixed_file_path, fourcc, fps, (width, height))
            
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                out.write(frame)
                
            cap.release()
            out.release()

        analysis_result = tools.vision_analyzer.analyze_injury(fixed_file_path)
        analysis_result = sanitize_ai_response(analysis_result)
        
        return {
            "file_name": file.filename,
            "analysis": analysis_result
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        # We might want to keep the file for a bit or delete it
        # os.remove(file_path) # Uncomment to cleanup
        pass

@app.post("/analyze-image")
async def analyze_image(file: UploadFile = File(...)):
    if not agent_executor:
        raise HTTPException(status_code=503, detail="Agent is not initialized yet.")
    
    # Create temp directory for uploads
    upload_dir = "uploads"
    os.makedirs(upload_dir, exist_ok=True)
    
    file_path = os.path.join(upload_dir, f"{uuid.uuid4()}_{file.filename}")
    
    try:
        # Save the uploaded file
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        
        import tools
        if not hasattr(tools, 'vision_analyzer') or tools.vision_analyzer is None:
            raise HTTPException(status_code=503, detail="Vision analyzer is not initialized.")
            
        analysis_result = tools.vision_analyzer.analyze_injury_image(file_path)
        analysis_result = sanitize_ai_response(analysis_result)
        
        return {
            "file_name": file.filename,
            "analysis": analysis_result
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        pass

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
