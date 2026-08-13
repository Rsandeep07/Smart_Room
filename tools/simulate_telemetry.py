#!/usr/bin/env python3
"""
Telemetry simulator for the Smart Classroom Monitoring and AC Recommendation System.

Stands in for the two real producers so the backend and dashboard can be developed,
demonstrated and tested before the ESP32-CAM is flashed and the vision service is
running:

  * the ESP32-CAM        -> POST /api/room/data      (temperature, humidity, vent temperature)
  * the Python CV service -> POST /api/detection      (person count)

This is a development aid, not part of the delivered system. It is what makes the
build plan's Step 4 completion gate reachable - exercising the whole flow before any
hardware exists - and it drives AC transitions and the cold-room alert on demand,
which is otherwise a matter of waiting an hour in a real room.

Standard library only; no pip install needed.

Usage
-----
  # Fill 8 hours of history, then stream live every 10 s (what the dashboard wants)
  python tools/simulate_telemetry.py --backfill-hours 8

  # Live only
  python tools/simulate_telemetry.py

  # Seed history and exit, without streaming
  python tools/simulate_telemetry.py --backfill-hours 8 --no-stream

  # Drive the room cold with the AC stuck on, to make the alert fire
  python tools/simulate_telemetry.py --scenario overcooled --backfill-hours 2

  # One sample and exit, for a smoke test
  python tools/simulate_telemetry.py --once
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_API_KEY = "smart-room-dev-key"
DEFAULT_ROOM = "ROOM101"

# Interval each producer reports at, matching the build plan: the ESP32 reads its
# sensors on a 30 s timer (Step 2.3) and the vision service posts every 10 s (Step 5.6).
SENSOR_INTERVAL_SECONDS = 30
DETECTION_INTERVAL_SECONDS = 10


@dataclass
class RoomModel:
    """
    A crude thermal model of one classroom. Enough to make the charts and the AC
    transitions behave plausibly; not a simulation anyone should draw conclusions from.

    Room temperature moves toward outdoor temperature, is pushed up by the people in
    it, and is pulled down while the AC runs. The AC itself is a dumb thermostat
    cycling around a setpoint, because that is what an uncontrolled central unit
    looks like from the outside.
    """

    outdoor_temperature: float = 34.0
    room_temperature: float = 27.0
    humidity: float = 55.0
    ac_setpoint: float = 24.0
    ac_on: bool = False
    ac_minutes_on: float = 0.0

    # Per-minute coefficients. Cooling has to comfortably exceed the heat a full room
    # puts in, or the AC never wins and the simulated room just sits at 32 degrees:
    # a full classroom gains about 0.11 K/min from occupancy alone, plus envelope drift.
    leak_rate: float = 0.010          # pull toward outdoor temperature
    occupancy_gain: float = 0.0035    # heating per person
    cooling_rate: float = 0.30        # cooling while the AC runs

    def step(self, minutes: float, occupancy: int, scenario: str) -> None:
        drift = (self.outdoor_temperature - self.room_temperature) * self.leak_rate
        gain = occupancy * self.occupancy_gain
        cooling = self.cooling_rate if self.ac_on else 0.0

        self.room_temperature += (drift + gain - cooling) * minutes
        self.room_temperature += random.uniform(-0.02, 0.02) * minutes

        # Humidity approaches a target rather than drifting linearly: the coil condenses
        # moisture out while cooling, occupancy adds it back, and both effects level off.
        # A linear drift just pins itself to whichever clamp it reached first.
        target = (45.0 if self.ac_on else 62.0) + occupancy * 0.18
        self.humidity += (target - self.humidity) * min(1.0, 0.06 * minutes)
        self.humidity = max(30.0, min(80.0, self.humidity))

        self._update_ac(minutes, scenario)

    def _update_ac(self, minutes: float, scenario: str) -> None:
        if scenario == "overcooled":
            # Somebody set it to 18 and walked away. This is the condition Section 9's
            # alert exists to catch, so the simulator has to be able to produce it.
            self.ac_on = True
            self.ac_minutes_on += minutes
            return

        if self.ac_on:
            self.ac_minutes_on += minutes
            if self.room_temperature <= self.ac_setpoint - 1.0:
                self.ac_on = False
                self.ac_minutes_on = 0.0
        else:
            if self.room_temperature >= self.ac_setpoint + 1.0:
                self.ac_on = True
                self.ac_minutes_on = 0.0

    def vent_temperature(self) -> float:
        """
        Supply-vent temperature, which is what the DS18B20 at the diffuser reads.

        A running coil delivers air well below room temperature - that gap is exactly
        the signal Section 20.3 uses to detect AC state. With the AC off the vent sits
        a little under room temperature and the gap closes.
        """
        if self.ac_on:
            return self.room_temperature - random.uniform(6.5, 8.5)
        return self.room_temperature - random.uniform(0.0, 1.0)


def occupancy_for(moment: datetime, scenario: str) -> int:
    """
    Class-schedule occupancy, plus the count noise a single wide-angle camera produces.

    Section 20.4 is explicit that a single 2 MP camera undercounts occluded rear rows,
    so a simulator that emitted an exact figure would flatter the system. The jitter
    here stands in for that error.
    """
    if scenario == "empty":
        return random.choice([0, 0, 1, 2])

    hour = moment.hour + moment.minute / 60.0

    if hour < 8.5 or hour >= 17.5:
        base = 0
    elif 12.5 <= hour < 13.5:            # lunch
        base = 4
    elif 8.5 <= hour < 9.0 or 16.5 <= hour < 17.5:
        base = 8
    else:
        # Occupancy rises through the morning, dips after lunch.
        base = 26 + 12 * math.sin((hour - 8.5) / 9.0 * math.pi)
        if hour >= 13.5:
            base -= 5

    if scenario == "crowded":
        base *= 1.6

    jitter = random.gauss(0, 1.6)
    return max(0, int(round(base + jitter)))


def post(base_url: str, path: str, api_key: str, payload: dict, timeout: float = 5.0) -> dict | None:
    request = urllib.request.Request(
        f"{base_url}{path}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "X-API-Key": api_key},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"  ! {path} -> HTTP {e.code}: {body}", file=sys.stderr)
    except urllib.error.URLError as e:
        # The backend not being up yet is the normal case during development, so this
        # is a warning and the loop keeps going rather than dying.
        print(f"  ! {path} -> {e.reason}", file=sys.stderr)
    return None


def send_sensor_sample(args, model: RoomModel, when: datetime | None = None) -> dict | None:
    payload = {
        "roomId": args.room,
        "temperature": round(model.room_temperature, 2),
        "humidity": round(model.humidity, 2),
        "ventTemperature": round(model.vent_temperature(), 2),
    }
    if when is not None:
        payload["recordedAt"] = when.isoformat(timespec="seconds")
    return post(args.base_url, "/api/room/data", args.api_key, payload)


def send_detection(args, count: int, when: datetime | None = None) -> dict | None:
    payload = {
        "roomId": args.room,
        "personCount": count,
        "source": "simulator",
    }
    if when is not None:
        payload["recordedAt"] = when.isoformat(timespec="seconds")
    return post(args.base_url, "/api/detection", args.api_key, payload)


def backfill(args, model: RoomModel) -> None:
    """
    Posts back-dated samples so the history charts have something in them immediately.

    Uses a coarser step than live streaming: a full 8 hours at the real 30 s cadence
    would be ~960 sensor posts, and the charts bucket to 10-minute averages anyway
    (see DashboardService.bucketMinutesFor), so finer detail would be averaged straight
    back out.
    """
    step_minutes = 5
    now = datetime.now().replace(microsecond=0)
    start = now - timedelta(hours=args.backfill_hours)
    steps = int(args.backfill_hours * 60 / step_minutes)

    print(f"Backfilling {args.backfill_hours} h of history "
          f"({steps} samples at {step_minutes} min) for {args.room} ...")

    # Settle the model before recording, so the series does not open on the arbitrary
    # constructor values. Stepped at the occupancy of the start instant, not of "now",
    # or an 8-hour backfill starting at 07:00 warms up against an evening empty room.
    for _ in range(40):
        model.step(step_minutes, occupancy_for(start, args.scenario), args.scenario)

    sent = 0
    for i in range(steps):
        moment = start + timedelta(minutes=i * step_minutes)
        occupancy = occupancy_for(moment, args.scenario)
        model.step(step_minutes, occupancy, args.scenario)

        if send_sensor_sample(args, model, moment) is not None:
            sent += 1
        if send_detection(args, occupancy, moment) is not None:
            sent += 1

    print(f"Backfill complete: {sent} samples accepted.")


def stream(args, model: RoomModel) -> None:
    print(f"Streaming live telemetry for {args.room} -> {args.base_url}  (Ctrl+C to stop)")
    print(f"  sensor every {SENSOR_INTERVAL_SECONDS}s, detection every {DETECTION_INTERVAL_SECONDS}s, "
          f"scenario '{args.scenario}'")

    tick = 0
    while True:
        now = datetime.now()
        occupancy = occupancy_for(now, args.scenario)
        model.step(DETECTION_INTERVAL_SECONDS / 60.0, occupancy, args.scenario)

        result = send_detection(args, occupancy)

        if tick % (SENSOR_INTERVAL_SECONDS // DETECTION_INTERVAL_SECONDS) == 0:
            sensor_result = send_sensor_sample(args, model)
            result = sensor_result or result
            recommended = (result or {}).get("recommendedTemperature")
            alert = (result or {}).get("alertActive")
            print(f"  {now:%H:%M:%S}  {occupancy:3d} people  "
                  f"room {model.room_temperature:5.1f} °C  vent {model.vent_temperature():5.1f} °C  "
                  f"AC {'ON ' if model.ac_on else 'OFF'} {model.ac_minutes_on:5.1f} min  "
                  f"-> recommend {recommended if recommended is not None else '--'} °C"
                  f"{'  [ALERT]' if alert else ''}")

        tick += 1
        time.sleep(DETECTION_INTERVAL_SECONDS)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Simulate ESP32-CAM sensor samples and vision-service person counts.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="backend base URL")
    parser.add_argument("--api-key", default=DEFAULT_API_KEY,
                        help="value for the X-API-Key header (smartroom.security.api-key)")
    parser.add_argument("--room", default=DEFAULT_ROOM, help="room identifier")
    parser.add_argument("--scenario", default="classroom",
                        choices=["classroom", "crowded", "empty", "overcooled"],
                        help="'overcooled' pins the AC on to make the cold-room alert fire")
    parser.add_argument("--backfill-hours", type=float, default=0,
                        help="post this many hours of back-dated history before streaming")
    parser.add_argument("--no-stream", action="store_true",
                        help="exit after the backfill instead of streaming live (seed and quit)")
    parser.add_argument("--once", action="store_true",
                        help="send a single pair of samples and exit")
    args = parser.parse_args()

    random.seed()
    model = RoomModel()
    if args.scenario == "overcooled":
        model.ac_setpoint = 18.0
        model.room_temperature = 22.0

    if args.once:
        occupancy = occupancy_for(datetime.now(), args.scenario)
        model.step(1.0, occupancy, args.scenario)
        print("sensor  :", send_sensor_sample(args, model))
        print("detection:", send_detection(args, occupancy))
        return 0

    if args.backfill_hours > 0:
        backfill(args, model)
        if args.no_stream:
            # Back-dated samples deliberately do not drive the decision engine, so the
            # current recommendation stays empty until a live sample or the backend's
            # 60-second monitor tick evaluates against the present state.
            print("Seeded history only. Send a live sample, or wait for the monitor tick, "
                  "for the recommendation to appear.")
            return 0

    try:
        stream(args, model)
    except KeyboardInterrupt:
        print("\nStopped.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
