cd /d "D:\AI health assistant"
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload --timeout-keep-alive 600
