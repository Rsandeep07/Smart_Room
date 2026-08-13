-- ---------------------------------------------------------------------------
-- Smart Classroom Monitoring and AC Recommendation System
-- MySQL schema  (build plan Step 3, refining Section 13)
--
--   mysql -u root -p -e "CREATE DATABASE smartroom CHARACTER SET utf8mb4;"
--   mysql -u root -p smartroom < mysql-schema.sql
--
-- The first three tables are exactly the Step 3 definitions: indexes added, an
-- explicit AC status source, and ac_status.duration deliberately absent because
-- it is derived on read (Section 20.6).
--
-- The last two tables (alerts, event_log) are additions. Section 13 has nowhere
-- to record a dismissable alert with an acknowledgement timestamp, and nowhere to
-- record the operational events the dashboard's Logs panel lists. Both are
-- required by the user interface in Section 14.
--
-- One deviation from Step 3: index names are prefixed with their table. MySQL
-- scopes index names per table, so Step 3's repeated 'idx_room_created' is legal
-- there - but H2, which the development profile runs on, scopes them per schema and
-- silently drops the duplicates. Unique names work on both.
-- ---------------------------------------------------------------------------

-- Telemetry. One row per ingest. A row from the ESP32 carries temperature,
-- humidity and vent_temperature with person_count NULL; a row from the Python
-- vision service carries person_count with the temperatures NULL. Readings are
-- therefore combined by taking the most recent non-NULL value of each, not by
-- expecting any single row to be complete.
CREATE TABLE IF NOT EXISTS room_data (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id VARCHAR(32) NOT NULL,
  temperature DECIMAL(5,2), humidity DECIMAL(5,2),
  vent_temperature DECIMAL(5,2), person_count INT,
  recorded_at DATETIME NOT NULL,
  INDEX idx_room_data_room_time (room_id, recorded_at)
);

-- AC ON/OFF intervals. The open interval is the row with end_time IS NULL;
-- there is at most one per room. Duration is end_time - start_time, computed on
-- read, so it can never drift out of step with the timestamps.
CREATE TABLE IF NOT EXISTS ac_status (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id VARCHAR(32) NOT NULL,
  status ENUM('ON','OFF') NOT NULL,
  source ENUM('MANUAL','VENT_PROBE') NOT NULL,
  start_time DATETIME NOT NULL, end_time DATETIME NULL,
  INDEX idx_ac_status_room_start (room_id, start_time)
);

-- Recommendation history. A row is written only when the recommendation
-- actually changes (Section 21 Step 4 hysteresis), so this table is an audit
-- trail of decisions rather than a poll log.
CREATE TABLE IF NOT EXISTS recommendations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id VARCHAR(32) NOT NULL, person_count INT,
  temperature DECIMAL(5,2), recommended_temperature INT,
  message VARCHAR(255), alert BOOLEAN DEFAULT FALSE,
  created_at DATETIME NOT NULL,
  INDEX idx_recommendations_room_created (room_id, created_at)
);

-- ADDITION: the cold-room alert of Section 9, as a dismissable record.
-- An alert is raised once per qualifying AC cycle (ac_status_id) so a room that
-- stays cold for two hours produces one banner, not sixty.
CREATE TABLE IF NOT EXISTS alerts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id VARCHAR(32) NOT NULL,
  alert_type VARCHAR(40) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  message VARCHAR(255) NOT NULL,
  temperature DECIMAL(5,2),
  ac_runtime_seconds BIGINT,
  ac_status_id BIGINT NULL,
  created_at DATETIME NOT NULL,
  acknowledged_at DATETIME NULL,
  INDEX idx_alerts_room_created (room_id, created_at),
  INDEX idx_alerts_room_ack (room_id, acknowledged_at)
);

-- ADDITION: operational event log backing the dashboard's Logs panel
-- (Section 14) and the structured logging of build plan Step 8.3.
CREATE TABLE IF NOT EXISTS event_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id VARCHAR(32),
  level VARCHAR(16) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  message VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_event_log_room_created (room_id, created_at)
);
