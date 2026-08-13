import { useState } from 'react'
import { Card } from '../components/Card'
import { clearAcOverride, setAcStatus } from '../api/client'
import { formatDateTime, formatNumber } from '../utils/format'

/**
 * Read-only view of the engine's configuration, plus the one control the dashboard owns.
 *
 * The thresholds are shown but not editable here. They are backend properties that must
 * be recalibrated from measurements taken in the real room, and a text box on a web page
 * invites someone to change the cold threshold on a hunch during a demonstration. The
 * values are displayed so that the number on screen and the number in the engine can be
 * confirmed to agree.
 */
export function SettingsView({ status, config, roomId, onChanged }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(null)

  const act = async (action, description) => {
    setBusy(true)
    setMessage(null)
    try {
      await action()
      setMessage(description)
      onChanged?.()
    } catch (e) {
      setMessage(`Failed: ${e.message}`)
    } finally {
      setBusy(false)
    }
  }

  const overrideActive = Boolean(status.manualOverrideExpiresAt)

  return (
    <div className="stack">
      <Card
        title="AC status source"
        subtitle="Supply-vent probe is primary; the manual override is a fallback"
      >
        <dl className="definition-list">
          <dt>Current state</dt>
          <dd>
            {status.acStatus} · {status.acMode}
          </dd>
          <dt>Determined by</dt>
          <dd>{status.acStatusSource === 'MANUAL' ? 'Manual override' : 'Vent temperature probe'}</dd>
          <dt>Vent delta</dt>
          <dd>
            {formatNumber(status.ventDelta)} K of {formatNumber(status.thresholds.ventDeltaThreshold, 1)} K
            threshold
          </dd>
          {overrideActive && (
            <>
              <dt>Override expires</dt>
              <dd>{formatDateTime(status.manualOverrideExpiresAt)}</dd>
            </>
          )}
        </dl>

        <p className="note" style={{ marginTop: 10 }}>
          An override outranks the probe temporarily and then expires on its own, so a
          setting made during a demonstration cannot be left behind by mistake.
        </p>

        <div className="button-row" style={{ marginTop: 12 }}>
          <button
            type="button"
            className="button"
            disabled={busy}
            onClick={() => act(() => setAcStatus(roomId, 'ON', 'set from Settings'), 'AC marked ON manually.')}
          >
            Mark AC ON
          </button>
          <button
            type="button"
            className="button"
            disabled={busy}
            onClick={() => act(() => setAcStatus(roomId, 'OFF', 'set from Settings'), 'AC marked OFF manually.')}
          >
            Mark AC OFF
          </button>
          <button
            type="button"
            className="button button--primary"
            disabled={busy || !overrideActive}
            onClick={() => act(() => clearAcOverride(roomId), 'Override cleared — vent probe resumed.')}
          >
            Return to vent probe
          </button>
        </div>

        {message && (
          <p className="note" style={{ marginTop: 10, color: 'var(--text-secondary)' }}>
            {message}
          </p>
        )}
      </Card>

      <div className="grid-2">
        <Card title="Decision engine" subtitle="Configured in the backend, recalibrate per room">
          <dl className="definition-list">
            <dt>Comfort setpoint</dt>
            <dd>{formatNumber(status.thresholds.targetTemperature, 1)} °C</dd>
            <dt>Cold threshold</dt>
            <dd>{formatNumber(status.thresholds.coldThreshold, 1)} °C</dd>
            <dt>AC runtime limit</dt>
            <dd>{status.thresholds.acRuntimeLimitMinutes} min</dd>
            <dt>Recommendation band</dt>
            <dd>
              {status.thresholds.recommendationMin} – {status.thresholds.recommendationMax} °C
            </dd>
            <dt>Hysteresis</dt>
            <dd>{status.thresholds.hysteresisMinutes} min between changes</dd>
            <dt>Vent delta threshold</dt>
            <dd>{formatNumber(status.thresholds.ventDeltaThreshold, 1)} K</dd>
          </dl>
          <p className="note" style={{ marginTop: 10 }}>
            The vent delta threshold above is a starting assumption. Measure the real
            vent-to-room difference with the AC genuinely on and off, then set it from that.
          </p>
        </Card>

        <Card title="Connection" subtitle="What this dashboard is talking to">
          <dl className="definition-list">
            <dt>Room</dt>
            <dd>{roomId}</dd>
            <dt>Known rooms</dt>
            <dd>{config?.rooms?.join(', ') ?? roomId}</dd>
            <dt>Camera stream</dt>
            <dd style={{ overflowWrap: 'anywhere' }}>{config?.cameraStreamUrl || 'not configured'}</dd>
            <dt>Poll interval</dt>
            <dd>{config?.pollIntervalSeconds ?? 5} s</dd>
            <dt>Server time</dt>
            <dd>{formatDateTime(status.serverTime)}</dd>
            <dt>Sensor telemetry</dt>
            <dd>{status.sensorOnline ? 'reporting' : 'no recent samples'}</dd>
            <dt>Vision service</dt>
            <dd>{status.visionOnline ? 'reporting' : 'no recent samples'}</dd>
          </dl>
        </Card>
      </div>
    </div>
  )
}
