# Smart Classroom Monitoring and AC Recommendation System

Backend and dashboard for the project specified in *AI-Based Smart Classroom / Room
Monitoring and AC Recommendation System* (Sections 1–26).

An ESP32-CAM streams video and reads two temperature probes. A PC runs YOLO to count
people. This backend combines occupancy, temperature and AC runtime, recommends a
setpoint, and raises an alert when the room has been overcooled. The dashboard shows it
all to a receptionist.

**The system does not control the AC.** It recommends and notifies. That boundary is
Section 18's decision and it is deliberate.

---

## What is in this repository

| Path | Contents | Status |
|---|---|---|
| `backend/` | Spring Boot 3.5 / Java 17 — REST API, decision engine, AC monitor, persistence | Complete, 39 tests passing |
| `frontend/` | React 19 + Vite — the six-view dashboard | Complete |
| `tools/simulate_telemetry.py` | Stands in for the ESP32 and the vision service | Complete |

**Not in this repository:** the ESP32-CAM firmware (build plan Steps 1–2) and the Python
YOLO vision service (Step 5). Both have a defined HTTP contract to post to — see
[Producer contracts](#producer-contracts) — and the simulator demonstrates that contract
working end to end.

---

## Quick start

Nothing needs installing beyond a JDK and Node. Maven comes via the wrapper and the
default database is in-memory, so there is no MySQL step until you want persistence.

Three terminals:

```bash
# 1 - backend  (http://localhost:8081)
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run

# 2 - dashboard  (http://localhost:5173)
cd frontend
npm install
npm run dev

# 3 - fake hardware: seed a day of history, then stream live
python tools/simulate_telemetry.py --backfill-hours 22
```

Open <http://localhost:5173>. Requirements: JDK 17+, Node 18+, Python 3.9+.

Without terminal 3 the dashboard is correct but empty — it will say so rather than
inventing readings.

---

## Prove it works

```bash
cd backend && ./mvnw test
```

39 tests. The ones worth knowing about:

- `DecisionEngineTest` — every row of the Section 8 occupancy table, both edges of the
  Section 21 dead band, all four conditions of the cold-room alert, the Section 16 worked
  example, and the Section 20.5 regression (45 people in a 19 °C room must **not** be told
  to cool to 22 °C).
- `DecisionFlowIntegrationTest` — the alert lifecycle (raised once per AC cycle, clears
  itself when the AC goes off) and Step 4 hysteresis (a change is held back, reported as
  pending, then published).

---

## Architecture

```
ESP32-CAM ──MJPEG stream──────────────────────────────────►  React dashboard (img tag)
    │
    ├──MJPEG stream──►  Python + YOLO  ──POST /api/detection──┐
    │                   (on a PC)                            │
    └──POST /api/room/data───────────────────────────────────►│
       temperature, humidity, vent temperature                │
                                                             ▼
                                                    Spring Boot backend
                                              decision engine · AC monitor
                                                             │
                                                    MySQL (or H2)
                                                             │
                                            GET /api/room/{id}/status ──► React
```

Section 5's conclusion — that YOLO inference belongs on a PC and not on the ESP32 — is
the load-bearing decision in the whole design, and the layout above follows it.

---

## The decision engine (Section 21)

The rules live in one class, `DecisionEngine`, as a **pure function** of its inputs and
configuration: no repository, no clock, no state. Hysteresis and persistence are stateful
and live in `RecommendationService`. That split is what makes the rules directly testable,
which is what makes the Step 7 calibration work tractable.

```
STEP 1  Base setpoint from occupancy       (Section 8 table, kept verbatim)
STEP 2  Correction from measured temperature
            T < T_target − 1   →  +1 K   (room too cold, back off cooling)
            T > T_target + 2   →  −1 K   (room too hot, cool harder)
            otherwise          →   0 K
        recommended = clamp(base + adjust, 22, 27)
STEP 3  Cold-room alert:  AC ON  AND  runtime > R_max  AND  T < T_cold
STEP 4  Hysteresis: the published setpoint changes at most once every 10 minutes
STEP 5  Persist to `recommendations`
```

Three points that are easy to miss:

**The dead band is asymmetric on purpose.** It runs from `T_target − 1` up to
`T_target + 2`, so a room one degree warm is left alone while a room one degree cold has
its setpoint raised. Section 20.5's failure case is an *overcooled* room, so the engine is
quicker to back off cooling than to add it.

**All three alert conditions are required.** Drop the temperature term and it alerts on
every long AC cycle in mid-summer, and is ignored by the second day. Drop the runtime term
and it alerts whenever someone opens a window.

**Hysteresis means the display legitimately lags.** So the dashboard says so — "Change to
22 °C is held until 16:02 to stop the value flickering" — rather than appearing frozen.
Alerts are deliberately *outside* the hysteresis gate; an overcooled room should not wait
ten minutes to say so.

The dashboard also shows the engine's working (`23 °C from occupancy, +1 K from measured
temperature → 24 °C`). Section 20.5's objection was that an occupancy-only setpoint is
indefensible under examination; showing how the number was reached answers that on screen.

---

## AC status detection (Section 20.3, Option C)

The primary source is the **supply-vent probe**: the AC is running when the vent is more
than `vent-delta-threshold` colder than the room. No mains contact, no electrician, no
building permission — and unlike manual entry it cannot be forgotten.

Manual entry (Option A) survives as a dashboard override **with a time-to-live**. Section
20.3's objection to Option A is that a receptionist forgets to change it back, so the
system forgets for them: after 30 minutes the probe resumes on its own. Mains current
sensing (Option B) is out of scope.

`AcMonitorService` runs every 60 seconds. If neither an override nor a *fresh* vent/room
sample pair is available it holds the last known state rather than inventing an OFF
transition — which would reset the runtime timer and lose the alert condition.

> **Calibrate the 4 K threshold.** It is a starting assumption, not a measurement. Take
> the real vent-to-room difference with the AC genuinely on and genuinely off, and set it
> from that (Step 7.3). The Settings view displays the live vent delta to help.

---

## Configuration

Everything tunable is in `backend/src/main/resources/application.properties`. Nothing is
hard-coded in the services, because a threshold you must recompile to change does not get
recalibrated.

| Property | Default | Meaning |
|---|---|---|
| `smartroom.engine.target-temperature` | `24.0` | `T_target`, the comfort setpoint |
| `smartroom.engine.cold-threshold` | `21.0` | `T_cold`, below which the room is too cold |
| `smartroom.engine.ac-runtime-limit` | `PT60M` | `R_max` before the alert can fire |
| `smartroom.engine.recommendation-min/max` | `22` / `27` | Clamp on the recommendation |
| `smartroom.engine.hysteresis-interval` | `PT10M` | Step 4 minimum between changes |
| `smartroom.ac.vent-delta-threshold` | `4.0` | Room − vent Δ above which the AC is ON |
| `smartroom.ac.stale-reading-timeout` | `PT5M` | Beyond this a producer counts as offline |
| `smartroom.ac.manual-override-ttl` | `PT30M` | How long an override outranks the probe |
| `smartroom.security.api-key` | `smart-room-dev-key` | **Change this.** Shared ingest secret |
| `smartroom.dashboard.camera-stream-url` | — | The MJPEG URL from the serial monitor |
| `smartroom.dashboard.allowed-origins` | `http://localhost:5173` | CORS for the dashboard |

To verify the alert without waiting an hour (Step 7.4):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--smartroom.engine.ac-runtime-limit=PT2M
```

---

## Switching to MySQL

The default profile is in-memory H2 so the system runs before MySQL exists. Nothing
survives a restart. For persistence:

```bash
mysql -u root -p -e "CREATE DATABASE smartroom CHARACTER SET utf8mb4;"
mysql -u root -p smartroom < backend/src/main/resources/db/mysql-schema.sql

cd backend
MYSQL_USER=root MYSQL_PASSWORD=yourpassword ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=mysql
```

Credentials come from `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`,
`MYSQL_PASSWORD`. The schema is owned by the SQL file, not by Hibernate
(`ddl-auto=none`).

### Schema

Five tables. The first three are Step 3's definitions; the last two are additions.

- **`room_data`** — one row per ingest. Deliberately sparse: the ESP32 posts temperatures
  with `person_count` NULL, the vision service posts a count with the temperatures NULL.
  No single row is complete, so current state is assembled from the latest **non-NULL**
  value of each. This is why the repository queries all carry `IsNotNull` clauses — a
  plain "latest row" query would blank the temperature tile whenever a detection happened
  to arrive last.
- **`ac_status`** — ON/OFF intervals. The current one is the row with `end_time IS NULL`.
  **`duration` is not a column**; it is computed on read, so it cannot drift out of step
  with its own timestamps (Section 20.6).
- **`recommendations`** — written only when a decision actually changes. An audit trail,
  not a poll log.
- **`alerts`** *(addition)* — Section 13 has nowhere to record a *dismissable* alert with
  an acknowledgement timestamp, which Section 14's banner requires. Scoped to the AC cycle
  that caused it, so a room left cold for two hours raises one alert rather than 120.
- **`event_log`** *(addition)* — backs the dashboard's Logs panel and gives Step 8.3's
  structured logging somewhere queryable to live.

One deviation from Step 3: index names are prefixed with their table. MySQL scopes index
names per table so Step 3's repeated `idx_room_created` is legal there — but H2 scopes
them per schema and **silently dropped the duplicates**.

---

## API

Ingest endpoints require `X-API-Key` (or `Authorization: Bearer <key>`). Read endpoints
are open: the dashboard is a browser page, and putting the ingest key in a React bundle
would publish it.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/room/data` | Sensor sample from the ESP32 — **key required** |
| `POST` | `/api/detection` | Person count from the vision service — **key required** |
| `GET` | `/api/room/{roomId}/status` | Everything the dashboard renders, one snapshot |
| `GET` | `/api/room/{roomId}/history?hours=24` | Series for the charts (max 720 h) |
| `GET` | `/api/room/{roomId}/logs?limit=20` | Event log |
| `GET` | `/api/room/{roomId}/alerts?limit=20` | Alert history |
| `POST` | `/api/room/{roomId}/ac` | Manual override — `{"status":"ON"\|"OFF"}` |
| `DELETE` | `/api/room/{roomId}/ac` | Clear the override, hand back to the probe |
| `POST` | `/api/alerts/{id}/acknowledge` | Dismiss an alert |
| `GET` | `/api/config` | Camera URL, room list, poll interval |

`GET /status` is one call rather than six on purpose. The dashboard polls every five
seconds; six polled endpoints would let the tiles disagree with each other mid-refresh — a
person count from one instant beside an AC runtime from another.

### Producer contracts

```bash
# ESP32-CAM, every 30 s
curl -X POST http://localhost:8080/api/room/data \
  -H 'Content-Type: application/json' -H 'X-API-Key: smart-room-dev-key' \
  -d '{"roomId":"ROOM101","temperature":21.5,"humidity":48.0,"ventTemperature":15.2}'

# Vision service, every 10 s. personCount MUST be the median over the window,
# never a single frame (Section 20.4).
curl -X POST http://localhost:8080/api/detection \
  -H 'Content-Type: application/json' -H 'X-API-Key: smart-room-dev-key' \
  -d '{"roomId":"ROOM101","personCount":24,"source":"yolov8n-960"}'
```

Both accept an optional `recordedAt`. **Back-dated samples are stored but do not drive the
decision engine or the event log.** This matters for real hardware: Section 20.6's offline
buffer replays readings after a Wi-Fi reconnect, and without this rule the first replayed
sample starts the hysteresis clock and pins the published setpoint to stale data for ten
minutes. Replayed readings belong in the charts; the next live sample or the 60-second
monitor tick evaluates against the present.

---

## Wiring (Section 23)

```
ESP32-CAM (AI-Thinker)

  5V     ──┬── regulated 5 V / 2 A adapter
           └── 1000 µF / 16 V ── GND
  GND    ───── common ground

  GPIO13 ───── DHT22 DATA   ── 10 kΩ  ── 3.3 V     (room)
  GPIO14 ───── DS18B20 DQ   ── 4.7 kΩ ── 3.3 V     (AC supply vent)

  Flashing only:
  GPIO0  ───── GND          (remove the jumper afterwards)
  U0R    ───── FTDI TX
  U0T    ───── FTDI RX
```

- **The 5 V / 2 A supply and the capacitor are mandatory, not optional.** A laptop USB
  port cannot sustain the OV2640's current spikes during Wi-Fi transmission, and a
  brownout reset is the single most common way this project fails (Section 20.1).
- **The microSD slot must stay unused** — GPIO 13 and 14 belong to the SD_MMC interface.
- Both sensors are 3.3 V logic. Do not put them on the 5 V rail.
- FTDI TX → U0R and FTDI RX → U0T. The lines cross over.
- Read the sensors from a `millis()` timer, never inside the stream handler: a DHT22 read
  blocks for ~25 ms and will stutter the MJPEG stream.

---

## Dashboard notes

Six views: Dashboard, Live Feed, History, Logs, Alerts, Settings. Dark theme as designed,
with a light theme for a bright reception area. Polls every five seconds.

**The header pill has three states, not two.** `NORMAL`, `ALERT`, and `DEGRADED` — the
last when either producer has gone quiet. Without it, a browned-out ESP32 leaves its last
reading on screen behind a green "System Normal", which is worse than showing nothing.

**A dropped poll dims the numbers and raises a banner; it does not blank the screen.** The
reading from four seconds ago is still the best information available. Likewise a refetch
holds the previous render at reduced opacity rather than flashing a skeleton every five
seconds.

**The feed badge distinguishes CONNECTING from LIVE.** A request to an ESP32 that has
moved IP hangs on the TCP connect instead of erroring, so `onError` may not fire for a
minute. A LIVE badge over a black rectangle is exactly the kind of confidently wrong
display the DEGRADED state exists to avoid.

### Charts

Two colours do the work: green `#199e70` for temperature, blue `#3987e5` for occupancy.
They are **not** free choices — they were validated against the dark card surface for
colourblind separation (worst pair ΔE 19.6 deuteranopic), chroma, lightness band and 3:1
contrast. If you change them, re-run that check; a green that looks fine to us can be
indistinguishable from the blue to a deuteranopic reader.

- 2 px lines, area fill at ~10 % opacity, solid hairline gridlines, no dashing.
- Single series per chart, so no legend — the card title already names it.
- **Gaps are real.** A Wi-Fi outage draws as a break in the line, not a straight
  interpolation across missing data.
- Every chart has a **Table** toggle. No value is reachable only via a tooltip.
- Humidity is its own chart, never a second line on the temperature axis. Two measures on
  two y-scales in one plot invent a correlation that is not in the data.
- The y-axis is not zero-based (a room sits between 19 and 30 °C, so a zero baseline hides
  exactly the variation the chart exists to show) but counts are floored at zero, because
  an axis offering "−2 people" reads as a bug in the counting.

### Two deliberate departures from the mock

1. **The mock shows 24 people at 21.5 °C recommending 23 °C.** This build says **25 °C**.
   The mock's number comes from the occupancy-only table, which is precisely what Section
   20.5 identifies as wrong. Section 21 is implemented instead.
2. **The two "Today" pills drive one shared range.** They are rendered in both chart
   headers as designed, but they are two views of one selection — charts side by side on
   different ranges invite a comparison that is not valid.

---

## The simulator

```bash
python tools/simulate_telemetry.py --backfill-hours 22   # seed history, then stream
python tools/simulate_telemetry.py --no-stream ...       # seed and exit
python tools/simulate_telemetry.py --scenario overcooled # drive the cold-room alert
python tools/simulate_telemetry.py --once                # one sample, for a smoke test
```

Scenarios: `classroom`, `crowded`, `empty`, `overcooled`. Standard library only.

A crude thermal model — room temperature drifts toward outdoor, is pushed up by occupancy,
pulled down while the AC runs, with a dumb thermostat cycling around a setpoint. Person
counts carry deliberate jitter, because Section 20.4 is explicit that a single wide-angle
camera undercounts occluded rear rows and a simulator emitting exact figures would flatter
the system.

It is a development aid, not part of the delivered system. Nothing anyone should draw
conclusions from.

---

## Still to do

**Step 5 — the vision service.** `POST /api/detection` per the contract above. YOLOv8n at
`imgsz=960`, `classes=[0]`, `conf=0.4`; median of a 10-second deque; reconnect logic
around `cv2.VideoCapture`, because the MJPEG stream will drop.

**Steps 1–2 — the firmware.** CameraWebServer plus a 30-second sensor timer posting to
`/api/room/data`. Wi-Fi reconnect, hardware watchdog, and an in-memory ring buffer that
replays on reconnect — the backend already handles back-dated replay correctly.

**Step 7 — calibration.** The part that distinguishes a demonstration from an engineering
result, and the part that gets skipped:

1. Count heads manually at 10 intervals; log the YOLO count beside each; compute mean
   absolute error. Tune `conf`, `imgsz` and camera angle; repeat.
2. Measure the vent-to-room difference with the AC genuinely ON and OFF. Set
   `vent-delta-threshold` from that, not from the assumed 4 K.
3. Verify the alert with `ac-runtime-limit=PT2M`, then restore it.
4. **Publish the measured error in the report.** ±10–15 % is a respectable and defensible
   result *provided it is measured and stated.*

Camera placement matters more than any code here: high in a front corner, angled down
about 30°, to maximise head separation. Occlusion is the dominant accuracy limit of the
whole system and it cannot be removed, only mitigated.
