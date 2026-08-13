import { formatTime } from '../utils/format'

/**
 * The Logs panel.
 *
 * The level dot is paired with the message text and the timestamp, so a warning is
 * distinguishable without relying on the amber. Rows only appear on state transitions -
 * AC on/off, a changed recommendation, an alert - which is what keeps the panel readable
 * instead of a scroll of telemetry.
 */
export function LogsPanel({ events, emptyMessage = 'No events recorded yet.' }) {
  if (!events || events.length === 0) {
    return <p className="empty-state">{emptyMessage}</p>
  }

  return (
    <div className="logs">
      {events.map((event) => (
        <div className="logs__row" key={event.id}>
          <span className="logs__time">{formatTime(event.createdAt)}</span>
          <span
            className={`logs__dot logs__dot--${event.level === 'WARNING' ? 'warning' : 'info'}`}
            title={event.level}
          />
          <span className="logs__message">{event.message}</span>
        </div>
      ))}
    </div>
  )
}
