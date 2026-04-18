import torch
from transformers import AutoModelForCausalLM, AutoTokenizer, AutoProcessor, AutoModelForImageTextToText
from langchain_core.language_models.chat_models import SimpleChatModel
from langchain_core.messages import BaseMessage
from typing import Any, List, Mapping, Optional
from langchain_core.callbacks.manager import CallbackManagerForLLMRun

class LocalQwen(SimpleChatModel):
    """A custom LangChain LLM for local Qwen vision models."""
    
    model_id: str = "Qwen/Qwen3.5-0.8B"
    device: str = "cuda" if torch.cuda.is_available() else "cpu"
    tokenizer: Any = None
    model: Any = None
    processor: Any = None

    def __init__(self, **kwargs: Any):
        super().__init__(**kwargs)
        print(f"Loading model {self.model_id} on {self.device}...")
        
        # Load multimodal model since it's used for vision too
        self.model = AutoModelForImageTextToText.from_pretrained(
            self.model_id,
            torch_dtype="auto",
            device_map="auto"
        )
        self.processor = AutoProcessor.from_pretrained(self.model_id)
        # Tokenizer is usually part of processor, but for LLM interface we use the text side
        self.tokenizer = AutoTokenizer.from_pretrained(self.model_id)

    @property
    def _llm_type(self) -> str:
        return "local_qwen"
        
    def bind_tools(self, tools: Any, **kwargs: Any) -> Any:
        return self

    def _call(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        # Standard chat template application
        prompt_messages = []
        for m in messages:
            role = m.type if m.type in ["user", "assistant", "system"] else "user"
            content = getattr(m, "content", "")
            if not isinstance(content, str):
                content = str(content)
            prompt_messages.append({"role": role, "content": content})
            
        text = self.processor.apply_chat_template(prompt_messages, tokenize=False, add_generation_prompt=True)
        
        inputs = self.processor(text=[text], padding=True, return_tensors="pt").to(self.model.device)
        
        with torch.no_grad():
            generated_ids = self.model.generate(**inputs, max_new_tokens=512)
            
        generated_ids_trimmed = [
            out_ids[len(in_ids):] for in_ids, out_ids in zip(inputs.input_ids, generated_ids)
        ]
        response = self.processor.batch_decode(
            generated_ids_trimmed, skip_special_tokens=True, clean_up_tokenization_spaces=False
        )[0]
        
        if stop:
            for s in stop:
                if s in response:
                    response = response[:response.index(s)]
                    
        return response

    @property
    def _identifying_params(self) -> Mapping[str, Any]:
        return {"model_id": self.model_id}
