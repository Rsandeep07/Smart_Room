import cv2
import time
import threading
import requests

from datetime import datetime
from flask import Flask, Response, jsonify
from ultralytics import YOLO


# ============================================================
# Configuration
# ============================================================

MODEL_PATH = "yolov8n.pt"

CAMERA_INDEX = 0

CONFIDENCE_THRESHOLD = 0.5

BACKEND_URL = "http://localhost:8080"

ROOM_ID = "ROOM101"

API_KEY = "smart-room-dev-key"

SEND_INTERVAL = 3

STREAM_HOST = "0.0.0.0"

STREAM_PORT = 8090


# ============================================================
# YOLO
# ============================================================

print("Loading YOLO model...")

model = YOLO(MODEL_PATH)

print("YOLO model loaded successfully.")


# ============================================================
# Camera
# ============================================================

cap = cv2.VideoCapture(CAMERA_INDEX, cv2.CAP_DSHOW)

if not cap.isOpened():
    raise RuntimeError("Could not open webcam")


# ============================================================
# Shared data
# ============================================================

latest_frame = None

frame_lock = threading.Lock()

last_person_count = 0

last_sent_time = 0


# ============================================================
# Flask application
# ============================================================

app = Flask(__name__)


# ============================================================
# Send person count to Spring Boot
# ============================================================

def send_person_count(person_count):

    payload = {
        "roomId": ROOM_ID,
        "personCount": person_count,
        "source": "yolov8n",
        "recordedAt": datetime.now().isoformat(
            timespec="seconds"
        )
    }

    try:

        response = requests.post(

            f"{BACKEND_URL}/api/detection",

            json=payload,

            headers={
                "X-API-Key": API_KEY
            },

            timeout=5
        )

        print(
            f"Person Count: {person_count} "
            f"-> Backend: HTTP {response.status_code}"
        )

        if response.status_code != 200:

            print(
                "Backend response:",
                response.text
            )

    except requests.exceptions.RequestException as e:

        print(
            "Could not connect to backend:",
            e
        )


# ============================================================
# Generate MJPEG stream
# ============================================================

def generate_stream():

    while True:

        with frame_lock:

            frame = latest_frame

        if frame is None:

            time.sleep(0.05)

            continue

        success, buffer = cv2.imencode(
            ".jpg",
            frame
        )

        if not success:

            continue

        frame_bytes = buffer.tobytes()

        yield (
            b"--frame\r\n"
            b"Content-Type: image/jpeg\r\n\r\n"
            + frame_bytes
            + b"\r\n"
        )


# ============================================================
# Video stream endpoint
# ============================================================

@app.route("/stream")
def stream():

    return Response(
        generate_stream(),
        mimetype="multipart/x-mixed-replace; boundary=frame"
    )


# ============================================================
# Status endpoint
# ============================================================

@app.route("/status")
def status():

    return jsonify({
        "roomId": ROOM_ID,
        "personCount": last_person_count
    })


# ============================================================
# YOLO processing thread
# ============================================================

def run_yolo():

    global latest_frame
    global last_person_count
    global last_sent_time

    while True:

        ret, frame = cap.read()

        if not ret:

            print("Could not read frame")

            time.sleep(0.1)

            continue


        # ----------------------------------------------------
        # YOLO detection
        # ----------------------------------------------------

        results = model(
            frame,
            conf=CONFIDENCE_THRESHOLD,
            verbose=False
        )


        # ----------------------------------------------------
        # Person count
        # ----------------------------------------------------

        person_count = 0


        for result in results:

            for box in result.boxes:

                class_id = int(box.cls[0])

                # COCO class 0 = person

                if class_id == 0:
                    person_count += 1

        # ----------------------------------------------------
        # Display person count on video
        # ----------------------------------------------------

        cv2.putText(

            frame,

            f"People Count: {person_count}",

            (20, 40),

            cv2.FONT_HERSHEY_SIMPLEX,

            1,

            (0, 255, 0),

            2
        )


        # ----------------------------------------------------
        # Update shared data
        # ----------------------------------------------------

        last_person_count = person_count


        with frame_lock:

            latest_frame = frame.copy()


        # ----------------------------------------------------
        # Send count to backend every 10 seconds
        # ----------------------------------------------------

        current_time = time.time()


        if current_time - last_sent_time >= SEND_INTERVAL:

            send_person_count(person_count)

            last_sent_time = current_time


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":

    print()
    print("======================================")
    print(" Smart Room YOLO Camera Service")
    print("======================================")
    print()

    print(
        f"Camera       : {CAMERA_INDEX}"
    )

    print(
        f"Stream       : "
        f"http://localhost:{STREAM_PORT}/stream"
    )

    print(
        f"Backend      : {BACKEND_URL}"
    )

    print(
        f"Room         : {ROOM_ID}"
    )

    print()

    print("Starting YOLO...")

    # Start YOLO in background thread

    yolo_thread = threading.Thread(

        target=run_yolo,

        daemon=True
    )

    yolo_thread.start()


    print("YOLO processing started.")

    print(
        f"Starting MJPEG server on "
        f"http://localhost:{STREAM_PORT}"
    )

    print()


    # Start Flask

    app.run(

        host=STREAM_HOST,

        port=STREAM_PORT,

        threaded=True,

        debug=False,

        use_reloader=False
    )