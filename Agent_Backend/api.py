import os
from dotenv import load_dotenv
load_dotenv()  # Load .env file into environment

import shutil
import uuid
from contextlib import asynccontextmanager
from fastapi import FastAPI, UploadFile, File, HTTPException
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
        "IMPORTANT: You MUST ALWAYS use the 'search_medical_database' tool to check PubMed before replying to ANY medical query. "
        "Never answer a medical query from your own knowledge without checking the database first. "
        "After getting the results, summarize them in plain everyday language the user can understand."
    )
    agent_executor = create_react_agent(llm, tools=toolkit, prompt=system_prompt)
    
    print("Model and Agent ready.")
    yield
    print("Shutting down...")

app = FastAPI(lifespan=lifespan)

class ChatRequest(BaseModel):
    message: str

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
        
        return {"response": response["messages"][-1].content}
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
        if not hasattr(tools, 'vision_analyzer') or tools.vision_analyzer is None:
            raise HTTPException(status_code=503, detail="Vision analyzer is not initialized.")
            
        analysis_result = tools.vision_analyzer.analyze_injury(file_path)
        
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
