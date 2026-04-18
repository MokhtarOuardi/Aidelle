import os
import argparse
from langgraph.prebuilt import create_react_agent
from local_qwen import LocalQwen
from gemini_model import GeminiModel
from tools import check_patient_reminders, call_emergency_contact, analyze_injury_video_file, analyze_injury_image_file, InjuryAnalyzer, search_medical_database, get_and_analyze_sensor_data
import tools

def main():
    parser = argparse.ArgumentParser(description="Medical Assistant Agent")
    parser.add_argument("--model", type=str, choices=["qwen", "gemini"], default="qwen",
                        help="Choose the main reasoning model to run the agent (qwen or gemini).")
    args = parser.parse_args()

    print(f"--- Medical Assistant Agent (Main Brain: {args.model.upper()}) ---")
    
    # 1. Initialize the vision model (always LocalQwen for vision tasks)
    print("Initializing Vision Model (LocalQwen)...")
    vision_llm = LocalQwen()
    
    # 2. Setup the Vision Analyzer (shared with the tool)
    tools.vision_analyzer = InjuryAnalyzer(vision_llm)
    
    # 3. Set the Main Brain LLM
    if args.model == "gemini":
        print("Initializing Main Brain (Gemini)...")
        llm = GeminiModel()
    else:
        llm = vision_llm
    
    # 4. Define the Toolkit
    toolkit = [
        check_patient_reminders,
        call_emergency_contact,
        analyze_injury_video_file,
        analyze_injury_image_file,
        search_medical_database,
        get_and_analyze_sensor_data
    ]
    
    # 5. Create the Agent
    system_prompt = (
        "You are a kind and caring medical assistant helping an elderly person. "
        "ALWAYS respond in simple, clear, and easy-to-understand language. "
        "Avoid medical jargon — explain things as if you are talking to a grandparent. "
        "Use short sentences, be warm and reassuring, and use bullet points when listing things. "
        "IMPORTANT: You MUST ALWAYS use the 'search_medical_database' tool to check PubMed before replying to ANY medical query. "
        "Never answer a medical question from your own knowledge without checking the database first. "
        "After getting the results, summarize them in plain everyday language the user can understand."
    )
    agent_executor = create_react_agent(llm, tools=toolkit, prompt=system_prompt)
    
    # 6. Conversational Loop
    print("\nAgent is ready. Ask a medical question or request assistance.")
    print("Examples: 'What medications do I need?', 'I'm hurt, check my injury', 'Call my caretaker'.")
    
    while True:
        user_input = input("\nYou: ")
        if user_input.lower() in ["exit", "quit", "bye"]:
            break
            
        try:
            response = agent_executor.invoke({"messages": [{"role": "user", "content": user_input}]})
            print(f"\nAgent: {response['messages'][-1].content}")
        except Exception as e:
            print(f"\nError: {str(e)}")

if __name__ == "__main__":
    main()
