import { useCallback, useState } from 'react'
import { Card } from '../components/Card'
import { acknowledgeAlert, fetchAlerts } from '../api/client'
import { usePolling } from '../hooks/usePolling'
import { formatDateTime, formatDurationWords, formatNumber } from '../utils/format'

/**
 * Alert history.
 *
 * An acknowledged alert is kept and shown, not deleted. The report needs to be able to
 * say how often the room was overcooled and for how long, and a table that only holds
 * currently-active alerts can never answer that.
 */
export function AlertsView({ roomId, pollIntervalMs, onChanged }) {
  const loader = useCallback(() => fetchAlerts(roomId, 100), [roomId])
  const { data: alerts, isLoading, refresh } = usePolling(loader, pollIntervalMs)
  const [busyId, setBusyId] = useState(null)

  const dismiss = async (alertId) => {
    setBusyId(alertId)
    try {
      await acknowledgeAlert(alertId)
      await refresh()
      onChanged?.()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Card title="Alerts" subtitle="Cold-room alerts raised for this room" flush>
      {isLoading && !alerts ? (
        <p className="empty-state">Loading…</p>
      ) : !alerts || alerts.length === 0 ? (
        <p className="empty-state">
          No alerts have been raised. One fires when the AC has run past its runtime limit
          and the room is below the cold threshold.
        </p>
      ) : (
        <div className="data-table-wrap" style={{ maxHeight: 'calc(100vh - 220px)' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Raised</th>
                <th scope="col">State</th>
                <th scope="col">Room temp</th>
                <th scope="col">AC runtime</th>
                <th scope="col">Message</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert) => {
                const active = !alert.acknowledgedAt
                return (
                  <tr key={alert.id}>
                    <td>{formatDateTime(alert.createdAt)}</td>
                    <td>
                      <span className={`badge ${active ? 'badge--warning' : 'badge--good'}`}>
                        {active ? 'Active' : 'Cleared'}
                      </span>
                    </td>
                    <td>{formatNumber(alert.temperature)} °C</td>
                    <td>{formatDurationWords(alert.acRuntimeSeconds)}</td>
                    <td style={{ whiteSpace: 'normal' }}>{alert.message}</td>
                    <td>
                      {active && (
                        <button
                          type="button"
                          className="button"
                          disabled={busyId === alert.id}
                          onClick={() => dismiss(alert.id)}
                        >
                          Dismiss
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
