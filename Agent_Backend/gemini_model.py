import os
from langchain_google_genai import ChatGoogleGenerativeAI

class GeminiModel:
    """A factory class that returns the official LangChain ChatGoogleGenerativeAI model.
    This natively supports structured tool-calling for LangGraph!"""
    
    def __new__(cls, *args, **kwargs):
        print(f"Loading Native Gemini API Model...")
        api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
        if not api_key:
            print("WARNING: GEMINI_API_KEY or GOOGLE_API_KEY environment variable is not set!")
            
        return ChatGoogleGenerativeAI(
            model="gemini-3-flash-preview",
            google_api_key=api_key,
            temperature=0        )
