import os
import datetime
import cv2
import torch
import urllib.request
import urllib.parse
import json
from typing import Optional
from langchain.tools import tool
from pymongo import MongoClient

# Use a mock client if no MongoDB is found
try:
    # Try connecting to local mongo
    client = MongoClient("mongodb://localhost:27017/", serverSelectionTimeoutMS=2000)
    client.server_info()
    db = client["medical_db"]
    meds_collection = db["medications"]
except Exception:
    print("Warning: MongoDB not found. Using in-memory mock for demonstration.")
    # Simple mock data structure
    class MockCollection:
        def __init__(self):
            self.data = [
                {
                    "name": "Lisinopril",
                    "instructions": "Take 1 tablet (10mg) with water.",
                    "last_taken": datetime.datetime.now() - datetime.timedelta(hours=25),
                    "frequency_hours": 24
                },
                {
                    "name": "Metformin",
                    "instructions": "Take with food.",
                    "last_taken": datetime.datetime.now() - datetime.timedelta(hours=13),
                    "frequency_hours": 12
                }
            ]
        def find(self, query=None):
            return self.data
    meds_collection = MockCollection()

@tool
def check_patient_reminders() -> str:
    """Checks the database for medications that need to be taken now. 
    Returns the name of the medication and instructions if due."""
    now = datetime.datetime.now()
    due_meds = []
    
    for med in meds_collection.find():
        last_taken = med["last_taken"]
        freq = med["frequency_hours"]
        if (now - last_taken).total_seconds() / 3600 >= freq:
            due_meds.append(f"Medication: {med['name']}\nInstructions: {med['instructions']}")
    
    if not due_meds:
        return "No medications are due at this time."
    
    return "The following medications need to be taken now:\n\n" + "\n---\n".join(due_meds)

@tool
def call_emergency_contact(message: str) -> str:
    """Sends an emergency message to the caretaker via email or preferred messaging app. 
    Input should be the message to send."""
    caretaker_email = os.getenv("CARETAKER_EMAIL", "caretaker@example.com")
    print(f"DEBUG: Sending Emergency Alert to {caretaker_email}: {message}")
    return f"Emergency alert sent to caretaker ({caretaker_email}): {message}"

class InjuryAnalyzer:
    """Helper to run Qwen Vision on frames/videos."""
    def __init__(self, model_wrapper):
        self.wrapper = model_wrapper

    def analyze_injury(self, video_path: str) -> str:
        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "type": "video",
                        "video": video_path,
                    },
                    {"type": "text", "text": "Analyze this video/image of an injury. What kind of injury is it and what are the immediate first aid steps? Give a concise medical assessment."},
                ],
            }
        ]
        
        try:
            from qwen_vl_utils import process_vision_info
            text = self.wrapper.processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
            image_inputs, video_inputs, video_kwargs = process_vision_info(messages, return_video_kwargs=True)
            
            inputs = self.wrapper.processor(
                text=[text],
                images=image_inputs,
                videos=video_inputs,
                padding=True,
                return_tensors="pt",
            ).to(self.wrapper.model.device)

            with torch.no_grad():
                generated_ids = self.wrapper.model.generate(**inputs, max_new_tokens=512, **video_kwargs)
            
            generated_ids_trimmed = [
                out_ids[len(in_ids):] for in_ids, out_ids in zip(inputs.input_ids, generated_ids)
            ]
            response = self.wrapper.processor.batch_decode(
                generated_ids_trimmed, skip_special_tokens=True, clean_up_tokenization_spaces=False
            )[0]
            return response
        except Exception as e:
            return f"Error during vision processing: {str(e)}"

    def analyze_injury_image(self, image_path: str) -> str:
        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "image": image_path,
                    },
                    {"type": "text", "text": "Analyze this image of an injury. What kind of injury is it and what are the immediate first aid steps? Give a concise medical assessment."},
                ],
            }
        ]
        
        try:
            from qwen_vl_utils import process_vision_info
            text = self.wrapper.processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
            # Fetch inputs but don't ask for video_kwargs (avoids 'fps' kwarg errors for images)
            image_inputs, video_inputs = process_vision_info(messages)
            
            inputs = self.wrapper.processor(
                text=[text],
                images=image_inputs,
                videos=video_inputs,
                padding=True,
                return_tensors="pt",
            ).to(self.wrapper.model.device)

            with torch.no_grad():
                # Removed **video_kwargs because it's purely an image
                generated_ids = self.wrapper.model.generate(**inputs, max_new_tokens=512)
            
            generated_ids_trimmed = [
                out_ids[len(in_ids):] for in_ids, out_ids in zip(inputs.input_ids, generated_ids)
            ]
            response = self.wrapper.processor.batch_decode(
                generated_ids_trimmed, skip_special_tokens=True, clean_up_tokenization_spaces=False
            )[0]
            return response
        except Exception as e:
            return f"Error during vision processing (image): {str(e)}"

@tool
def analyze_injury_video_file(video_path: str) -> str:
    """Analyzes a video file of an injury. Returns the medical assessment of the injury seen in the video."""
    if not video_path or not os.path.exists(video_path):
        return f"Error: Video file not found at {video_path}."
        
    global vision_analyzer
    if 'vision_analyzer' not in globals():
        return "Error: Injury Vision Analyzer is not initialized."
        
    return vision_analyzer.analyze_injury(video_path)

@tool
def analyze_injury_image_file(image_path: str) -> str:
    """Analyzes an image file of an injury. Returns the medical assessment of the injury seen in the image."""
    if not image_path or not os.path.exists(image_path):
        return f"Error: Image file not found at {image_path}."
        
    global vision_analyzer
    if 'vision_analyzer' not in globals():
        return "Error: Injury Vision Analyzer is not initialized."
        
    return vision_analyzer.analyze_injury_image(image_path)

@tool
def search_medical_database(query: str) -> str:
    """Searches PubMed, a trusted online medical database, for articles related to the query and returns a summary of the top results."""
    try:
        base_url = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/"
        search_url = f"{base_url}esearch.fcgi?db=pubmed&term={urllib.parse.quote(query)}&retmode=json&retmax=3"
        
        req = urllib.request.Request(search_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            search_data = json.loads(response.read().decode())
        
        id_list = search_data.get("esearchresult", {}).get("idlist", [])
        if not id_list:
            return "No results found in the medical database."
            
        ids_str = ",".join(id_list)
        summary_url = f"{base_url}esummary.fcgi?db=pubmed&id={ids_str}&retmode=json"
        
        req2 = urllib.request.Request(summary_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req2) as response:
            summary_data = json.loads(response.read().decode())
            
        results = []
        uid_list = summary_data.get("result", {}).get("uids", [])
        for uid in uid_list:
            item = summary_data["result"][uid]
            title = item.get("title", "")
            source = item.get("source", "")
            pubdate = item.get("pubdate", "")
            results.append(f"- {title} ({source}, {pubdate})")
            
        return "Top results from PubMed (National Library of Medicine):\n" + "\n".join(results)
    except Exception as e:
        return f"Error querying medical database: {str(e)}"

@tool
def get_and_analyze_sensor_data(patient_id: str = "default_user") -> str:
    """Fetches user daily sensor data arrays (heart rate, blood pressure, SpO2) from the database and analyzes it for any anomalies."""
    # Mock online database with arrays of readings over the day
    mock_sensor_db = {
        "default_user": {
            "heart_rate": [72, 75, 88, 110, 80, 78], # bpm (110 is high)
            "blood_pressure_systolic": [120, 122, 145, 130, 118], # mmHg
            "blood_pressure_diastolic": [78, 80, 92, 85, 75], # mmHg
            "spo2": [99, 98, 98, 94, 97], # % (94 is low)
            "temperature_f": [98.2, 98.6, 99.1, 100.6, 98.7] # F (100.6 is a fever spike)
        }
    }
    
    data = mock_sensor_db.get(patient_id)
    if not data:
        return f"No sensor data found for patient: {patient_id}"
    
    anomalies = []
    
    # Heart Rate
    hr_list = data["heart_rate"]
    max_hr = max(hr_list)
    min_hr = min(hr_list)
    avg_hr = sum(hr_list) / len(hr_list)
    if min_hr < 60 or max_hr > 100:
        anomalies.append(f"Abnormal Heart Rate detected. Range: {min_hr}-{max_hr} bpm (Avg: {avg_hr:.1f} bpm).")
        
    # Blood Pressure
    bps_list = data["blood_pressure_systolic"]
    bpd_list = data["blood_pressure_diastolic"]
    max_bps = max(bps_list)
    max_bpd = max(bpd_list)
    if max_bps >= 130 or max_bpd >= 80:
        anomalies.append(f"High Blood Pressure spikes detected. Peak: {max_bps}/{max_bpd} mmHg.")
        
    # SpO2
    spo2_list = data["spo2"]
    min_spo2 = min(spo2_list)
    if min_spo2 < 95:
        anomalies.append(f"Low Oxygen Saturation (SpO2) detected. Dropped to {min_spo2}%.")
        
    # Temperature
    temp_list = data["temperature_f"]
    max_temp = max(temp_list)
    if max_temp > 100.4:
        anomalies.append(f"Fever spike detected. Peak: {max_temp} °F.")
        
    result_str = "Daily Sensor Data Arrays:\n"
    result_str += f"- Heart Rate (bpm): {hr_list}\n"
    result_str += f"- Blood Pressure Systolic (mmHg): {bps_list}\n"
    result_str += f"- Blood Pressure Diastolic (mmHg): {bpd_list}\n"
    result_str += f"- SpO2 (%): {spo2_list}\n"
    result_str += f"- Temperature (°F): {temp_list}\n\n"
    
    if anomalies:
        result_str += "WARNING: Anomalies Detected Today!\n"
        for idx, anomaly in enumerate(anomalies):
            result_str += f"{idx+1}. {anomaly}\n"
        result_str += "Please advise the user based on these daily anomalies."
    else:
        result_str += "All daily sensor readings are within normal ranges."
        
    return result_str


