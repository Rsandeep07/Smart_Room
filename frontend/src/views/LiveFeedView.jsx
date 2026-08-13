import { Card } from '../components/Card'
import { LiveFeedCard } from '../components/LiveFeedCard'
import { formatAge, formatDuration, formatNumber, NO_VALUE } from '../utils/format'

/** The camera at full width, with the readings that describe what is on screen. */
export function LiveFeedView({ status, cameraStreamUrl, liveAcSeconds }) {
  return (
    <div className="stack">
      <LiveFeedCard
        streamUrl={cameraStreamUrl}
        personCount={status.personCount}
        sensorOnline={status.sensorOnline}
      />

      <div className="grid-2">
        <Card title="Detection" subtitle="From the Python vision service">
          <dl className="definition-list">
            <dt>Person count</dt>
            <dd>{status.personCount ?? NO_VALUE}</dd>
            <dt>Last reported</dt>
            <dd>{formatAge(status.personCountAt)}</dd>
            <dt>Service state</dt>
            <dd>
              <span className={`badge ${status.visionOnline ? 'badge--good' : 'badge--warning'}`}>
                {status.visionOnline ? 'Reporting' : 'No recent reports'}
              </span>
            </dd>
          </dl>
          <p className="note" style={{ marginTop: 10 }}>
            The count is the median over a 10-second window, not a single frame. A single
            wide-angle camera undercounts occluded rear rows, so this figure should be
            calibrated against a manual head count and its measured error stated.
          </p>
        </Card>

        <Card title="Environment" subtitle="From the ESP32-CAM sensor board">
          <dl className="definition-list">
            <dt>Room temperature</dt>
            <dd>{formatNumber(status.temperature)} °C</dd>
            <dt>Humidity</dt>
            <dd>{formatNumber(status.humidity, 0)} %</dd>
            <dt>Vent temperature</dt>
            <dd>{formatNumber(status.ventTemperature)} °C</dd>
            <dt>Vent delta</dt>
            <dd>
              {formatNumber(status.ventDelta)} K
              <span className="note"> (AC on above {formatNumber(status.thresholds.ventDeltaThreshold, 1)} K)</span>
            </dd>
            <dt>AC state</dt>
            <dd>
              {status.acStatus} · {formatDuration(liveAcSeconds)}
            </dd>
            <dt>Last sample</dt>
            <dd>{formatAge(status.temperatureAt)}</dd>
          </dl>
        </Card>
      </div>
    </div>
  )
}
