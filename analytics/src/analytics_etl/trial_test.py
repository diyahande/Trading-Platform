import os
from dotenv import load_dotenv

load_dotenv()

api_key = os.getenv("FAUXNANCE_API_KEY")

print("Key found:", api_key is not None)
print("Key length:", len(api_key) if api_key else 0)