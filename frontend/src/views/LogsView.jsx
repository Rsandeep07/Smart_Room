import { useCallback } from 'react'
import { Card } from '../components/Card'
import { fetchLogs } from '../api/client'
import { usePolling } from '../hooks/usePolling'
import { formatDateTime } from '../utils/format'

/** The full event log as a table, so every entry is readable and copyable. */
export function LogsView({ roomId, pollIntervalMs }) {
  const loader = useCallback(() => fetchLogs(roomId, 200), [roomId])
  const { data: events, isLoading } = usePolling(loader, pollIntervalMs)

  return (
    <Card
      title="Event log"
      subtitle="AC transitions, recommendation changes, alerts and sensor state"
      flush
    >
      {isLoading && !events ? (
        <p className="empty-state">Loading…</p>
      ) : !events || events.length === 0 ? (
        <p className="empty-state">
          No events recorded yet. Rows appear when something changes — the AC switching on,
          a recommendation moving, an alert firing.
        </p>
      ) : (
        <div className="data-table-wrap" style={{ maxHeight: 'calc(100vh - 220px)' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Time</th>
                <th scope="col">Level</th>
                <th scope="col">Event</th>
                <th scope="col">Message</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>{formatDateTime(event.createdAt)}</td>
                  <td>
                    <span className={`badge ${event.level === 'WARNING' ? 'badge--warning' : ''}`}>
                      {event.level}
                    </span>
                  </td>
                  <td>{event.eventType}</td>
                  <td style={{ whiteSpace: 'normal' }}>{event.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
