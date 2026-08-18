import cv2
import time
import threading
import requests

from datetime import datetime
from flask import Flask, Response, jsonify
from ultralytics import YOLO


# ============================================================
# CONFIGURATION
# ============================================================

# YOLO model
MODEL_PATH = "yolov8n.pt"

# ============================================================
# EXTERNAL IP CAMERA
# ============================================================
# IMPORTANT:
# This is now the CAMERA INPUT.
# We are NOT using the laptop webcam.
#
# Your local webcam stream:
CAMERA_SOURCE = "http://localhost:8090/stream"


# YOLO confidence
CONFIDENCE_THRESHOLD = 0.5


# ============================================================
# SPRING BOOT BACKEND
# ============================================================

BACKEND_URL = "http://localhost:8081"

ROOM_ID = "ROOM101"

API_KEY = "smart-room-dev-key"

# Send person count every 3 seconds
SEND_INTERVAL = 3


# ============================================================
# OUTPUT STREAM
# ============================================================

STREAM_HOST = "0.0.0.0"

STREAM_PORT = 8091


# ============================================================
# YOLO MODEL
# ============================================================

print()
print("======================================")
print(" Smart Room YOLO Camera Service")
print("======================================")
print()

print("Loading YOLO model...")

model = YOLO(MODEL_PATH)

print("YOLO model loaded successfully.")


# ============================================================
# CAMERA
# ============================================================

print()
print("Connecting to external camera...")
print("Camera URL:", CAMERA_SOURCE)

cap = cv2.VideoCapture(CAMERA_SOURCE)

if not cap.isOpened():
    raise RuntimeError(
        f"Could not connect to external camera: {CAMERA_SOURCE}"
    )

print("External camera connected successfully.")


# ============================================================
# SHARED DATA
# ============================================================

latest_frame = None

frame_lock = threading.Lock()

last_person_count = 0

last_sent_time = 0


# ============================================================
# FLASK APPLICATION
# ============================================================

app = Flask(__name__)


# ============================================================
# SEND PERSON COUNT TO SPRING BOOT
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
# GENERATE MJPEG STREAM
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
# VIDEO STREAM ENDPOINT
# ============================================================

@app.route("/stream")
def stream():

    return Response(

        generate_stream(),

        mimetype="multipart/x-mixed-replace; boundary=frame"
    )


# ============================================================
# STATUS ENDPOINT
# ============================================================

@app.route("/status")
def status():

    return jsonify({

        "roomId": ROOM_ID,

        "personCount": last_person_count

    })


# ============================================================
# YOLO PROCESSING
# ============================================================

def run_yolo():

    global latest_frame

    global last_person_count

    global last_sent_time


    while True:

        # ----------------------------------------------------
        # Read frame from external IP camera
        # ----------------------------------------------------

        ret, frame = cap.read()


        if not ret:

            print(
                "Could not read frame from external camera."
            )

            time.sleep(1)

            continue


        # ----------------------------------------------------
        # YOLO DETECTION
        # ----------------------------------------------------

        results = model(

            frame,

            conf=CONFIDENCE_THRESHOLD,

            verbose=False

        )


        # ----------------------------------------------------
        # PERSON COUNT
        # ----------------------------------------------------

        person_count = 0


        for result in results:

            for box in result.boxes:

                class_id = int(
                    box.cls[0]
                )


                # COCO class 0 = person

                if class_id == 0:

                    person_count += 1


        # ----------------------------------------------------
        # DRAW YOLO RESULTS
        # ----------------------------------------------------

        annotated_frame = frame.copy()


        for result in results:

            for box in result.boxes:

                class_id = int(
                    box.cls[0]
                )


                if class_id != 0:

                    continue


                confidence = float(
                    box.conf[0]
                )


                x1, y1, x2, y2 = map(
                    int,
                    box.xyxy[0]
                )


                # Draw bounding box

                cv2.rectangle(

                    annotated_frame,

                    (x1, y1),

                    (x2, y2),

                    (0, 255, 0),

                    2

                )


                # Person label

                label = (
                    f"Person {confidence:.2f}"
                )


                cv2.putText(

                    annotated_frame,

                    label,

                    (x1, max(y1 - 10, 20)),

                    cv2.FONT_HERSHEY_SIMPLEX,

                    0.6,

                    (0, 255, 0),

                    2

                )


        # ----------------------------------------------------
        # DISPLAY PERSON COUNT
        # ----------------------------------------------------

        cv2.putText(

            annotated_frame,

            f"People Count: {person_count}",

            (20, 40),

            cv2.FONT_HERSHEY_SIMPLEX,

            1,

            (0, 255, 0),

            2

        )


        # ----------------------------------------------------
        # UPDATE SHARED DATA
        # ----------------------------------------------------

        last_person_count = person_count


        with frame_lock:

            latest_frame = annotated_frame.copy()


        # ----------------------------------------------------
        # SEND COUNT TO BACKEND
        # ----------------------------------------------------

        current_time = time.time()


        if (
            current_time - last_sent_time
            >= SEND_INTERVAL
        ):

            send_person_count(
                person_count
            )

            last_sent_time = current_time


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":

    print()

    print(
        "Camera Input :",
        CAMERA_SOURCE
    )

    print(
        "YOLO Model   :",
        MODEL_PATH
    )

    print(
        "Backend      :",
        BACKEND_URL
    )

    print(
        "Room         :",
        ROOM_ID
    )

    print(
        "Output Stream:",
        f"http://localhost:{STREAM_PORT}/stream"
    )

    print()

    print("Starting YOLO processing...")


    # --------------------------------------------------------
    # START YOLO THREAD
    # --------------------------------------------------------

    yolo_thread = threading.Thread(

        target=run_yolo,

        daemon=True

    )

    yolo_thread.start()


    print(
        "YOLO processing started."
    )


    # --------------------------------------------------------
    # START FLASK SERVER
    # --------------------------------------------------------

    print()

    print(
        f"Starting MJPEG server on "
        f"http://localhost:{STREAM_PORT}"
    )

    print()

    app.run(

        host=STREAM_HOST,

        port=STREAM_PORT,

        threaded=True,

        debug=False,

        use_reloader=False

    )