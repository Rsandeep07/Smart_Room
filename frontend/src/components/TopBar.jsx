import { BellIcon, CheckCircleIcon, PlugOffIcon, WarningIcon } from './Icons'

/**
 * The system-status pill has three states, not two.
 *
 * DEGRADED exists because the failure mode Section 20.1 warns about - the ESP32 browning
 * out mid-stream - leaves the last reading sitting on screen looking perfectly healthy.
 * A dashboard that says "System Normal" while its sensor has been silent for ten minutes
 * is worse than one that says nothing.
 */
const PILL = {
  NORMAL: { className: 'status-pill--good', label: 'System Normal', Icon: CheckCircleIcon },
  ALERT: { className: 'status-pill--warning', label: 'Alert Active', Icon: WarningIcon },
  DEGRADED: { className: 'status-pill--critical', label: 'Sensor Offline', Icon: PlugOffIcon },
}

export function TopBar({
  title,
  subtitle,
  systemStatus,
  alertCount,
  rooms,
  roomId,
  onRoomChange,
  onBellClick,
}) {
  const pill = PILL[systemStatus] ?? PILL.NORMAL
  const { Icon } = pill

  return (
    <header className="topbar">
      <div>
        <h1 className="topbar__title">{title}</h1>
        <p className="topbar__subtitle">{subtitle}</p>
      </div>

      <div className="topbar__actions">
        {rooms && rooms.length > 1 && (
          <>
            <label className="visually-hidden" htmlFor="room-select">
              Room
            </label>
            <select
              id="room-select"
              className="room-select"
              value={roomId}
              onChange={(event) => onRoomChange(event.target.value)}
            >
              {rooms.map((room) => (
                <option key={room} value={room}>
                  {room}
                </option>
              ))}
            </select>
          </>
        )}

        <span className={`status-pill ${pill.className}`}>
          <Icon size={13} />
          {pill.label}
        </span>

        <button
          type="button"
          className="icon-button"
          onClick={onBellClick}
          aria-label={alertCount > 0 ? `${alertCount} active alerts` : 'Alerts'}
          title="Alerts"
        >
          <BellIcon size={16} />
          {alertCount > 0 && <span className="icon-button__badge">{alertCount}</span>}
        </button>
      </div>
    </header>
  )
}
