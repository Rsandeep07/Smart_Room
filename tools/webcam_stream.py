#!/usr/bin/env python3
"""
Webcam stand-in for the ESP32-CAM.

Serves the PC's own webcam as an MJPEG multipart stream on the same contract the
CameraWebServer sketch publishes, so the dashboard's Live Feed panel can be verified
before the board is flashed and without waiting on hardware.

This is a development aid, not part of the delivered system - the companion to
simulate_telemetry.py, which stands in for the other half of the ESP32's job.

Requires opencv-python (`pip install opencv-python`); everything else is standard
library.

Usage
-----
  # Serve the default webcam on http://localhost:8090/stream
  python tools/webcam_stream.py

  # A second camera, at a gentler frame rate
  python tools/webcam_stream.py --camera 1 --fps 10

  # Bigger frames, if the dashboard panel looks soft
  python tools/webcam_stream.py --width 1280 --height 720
"""

from __future__ import annotations

import argparse
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import cv2

BOUNDARY = "frame"
BOUNDARY_BYTES = BOUNDARY.encode()


class Camera:
    """One VideoCapture shared by every client - Windows will not open the device twice."""

    def __init__(self, index: int, width: int, height: int, quality: int) -> None:
        # DSHOW opens in well under a second. The default MSMF backend on Windows can
        # take five or more, which is long enough for the dashboard's 8 s connect
        # timeout to declare the feed dead before the first frame ever arrives.
        backend = cv2.CAP_DSHOW if sys.platform == "win32" else cv2.CAP_ANY
        self.capture = cv2.VideoCapture(index, backend)
        if not self.capture.isOpened():
            raise SystemExit(
                f"could not open webcam index {index} - close any other app using the "
                f"camera (Teams, Zoom, the Windows Camera app), or try --camera 1"
            )
        self.capture.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        self.capture.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
        self.quality = quality
        self.lock = threading.Lock()

    def jpeg(self) -> bytes | None:
        with self.lock:
            ok, frame = self.capture.read()
        if not ok:
            return None
        ok, buffer = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), self.quality])
        return buffer.tobytes() if ok else None

    def close(self) -> None:
        self.capture.release()


class StreamHandler(BaseHTTPRequestHandler):
    """Answers GET /stream with an endless multipart/x-mixed-replace response."""

    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:
        if self.path.split("?")[0] != "/stream":
            self.send_error(404, "only /stream is served")
            return

        self.send_response(200)
        self.send_header("Cache-Control", "no-cache, private")
        self.send_header("Pragma", "no-cache")
        self.send_header("Content-Type", f"multipart/x-mixed-replace; boundary={BOUNDARY}")
        self.end_headers()

        try:
            while True:
                jpeg = self.camera.jpeg()
                if jpeg is not None:
                    self.wfile.write(
                        b"--%s\r\nContent-Type: image/jpeg\r\nContent-Length: %d\r\n\r\n"
                        % (BOUNDARY_BYTES, len(jpeg))
                    )
                    self.wfile.write(jpeg)
                    self.wfile.write(b"\r\n")
                time.sleep(self.frame_interval)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            # The dashboard navigated away, or remounted the img on its retry timer.
            # Both are normal; only an unhandled exception here would be worth a trace.
            pass

    @property
    def camera(self) -> Camera:
        return self.server.camera

    @property
    def frame_interval(self) -> float:
        return self.server.frame_interval

    def log_message(self, *args) -> None:
        pass  # one log line per frame drowns anything useful


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Serve a webcam as MJPEG, in place of the ESP32-CAM."
    )
    parser.add_argument("--camera", type=int, default=0, help="webcam index (default 0)")
    parser.add_argument("--port", type=int, default=8090, help="listen port (default 8090)")
    parser.add_argument("--fps", type=float, default=15.0, help="frames per second (default 15)")
    parser.add_argument("--width", type=int, default=640)
    parser.add_argument("--height", type=int, default=480)
    parser.add_argument("--quality", type=int, default=80, help="JPEG quality 1-100 (default 80)")
    args = parser.parse_args()

    server = ThreadingHTTPServer(("0.0.0.0", args.port), StreamHandler)
    server.daemon_threads = True
    server.camera = Camera(args.camera, args.width, args.height, args.quality)
    server.frame_interval = 1.0 / max(args.fps, 1.0)

    print(f"MJPEG stream ready on http://localhost:{args.port}/stream")
    print("Point smartroom.dashboard.camera-stream-url at it. Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nstopping")
    finally:
        server.camera.close()


if __name__ == "__main__":
    main()
