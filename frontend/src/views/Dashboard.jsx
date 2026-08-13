import { AlertBanner } from '../components/AlertBanner'
import { Card } from '../components/Card'
import { HistoryChart } from '../components/HistoryChart'
import { LiveFeedCard } from '../components/LiveFeedCard'
import { LogsPanel } from '../components/LogsPanel'
import { RangeSelector } from '../components/RangeSelector'
import { RecommendationCard } from '../components/RecommendationCard'
import { StatTile } from '../components/StatTile'
import {
  DropletIcon,
  PeopleIcon,
  SnowflakeIcon,
  TargetIcon,
  ThermometerIcon,
  TimerIcon,
} from '../components/Icons'
import { formatDuration, formatInteger, formatNumber, NO_VALUE } from '../utils/format'

/**
 * The dashboard of Section 14.
 *
 * @param liveAcSeconds AC duration recomputed locally each second. The status endpoint is
 *                      polled every five seconds, so rendering acDurationSeconds directly
 *                      would leave the timer still and then jump - it has to tick.
 */
export function Dashboard({
  status,
  history,
  logs,
  rangeHours,
  onRangeChange,
  onDismissAlert,
  isDismissingAlert,
  liveAcSeconds,
  cameraStreamUrl,
  onNavigate,
}) {
  const acOn = status.acStatus === 'ON'

  return (
    <div className="dashboard">
      <div className="dashboard__row-primary">
        <LiveFeedCard
          streamUrl={cameraStreamUrl}
          personCount={status.personCount}
          sensorOnline={status.sensorOnline}
        />

        <div className="dashboard__tiles">
          <StatTile
            label="Person Count"
            value={formatInteger(status.personCount)}
            unit={status.personCount === 1 ? 'Person' : 'People'}
            icon={<PeopleIcon size={17} />}
            iconColor="var(--series-person)"
            stale={!status.visionOnline}
            staleLabel="Vision service offline"
            hint="Median over the last 10 s"
          />
          <StatTile
            label="Temperature"
            value={formatNumber(status.temperature, 1)}
            unit="°C"
            icon={<ThermometerIcon size={17} />}
            iconColor="var(--status-critical)"
            stale={!status.sensorOnline}
            hint={
              status.temperature !== null && status.temperature < status.thresholds.coldThreshold
                ? `Below the ${formatNumber(status.thresholds.coldThreshold, 0)} °C cold threshold`
                : `Target ${formatNumber(status.thresholds.targetTemperature, 0)} °C`
            }
            hintTone={
              status.temperature !== null && status.temperature < status.thresholds.coldThreshold
                ? 'warning'
                : undefined
            }
          />
          <StatTile
            label="Humidity"
            value={formatNumber(status.humidity, 0)}
            unit="%"
            icon={<DropletIcon size={17} />}
            iconColor="var(--accent-blue)"
            stale={!status.sensorOnline}
            hint="Relative humidity"
          />

          <StatTile
            label="Recommended AC Temp"
            value={status.recommendedTemperature ?? NO_VALUE}
            unit={status.recommendedTemperature ? '°C' : undefined}
            icon={<TargetIcon size={17} />}
            iconColor="var(--status-warning)"
            hint={status.recommendationReason}
          />
          <StatTile
            label="AC Status"
            value={status.acStatus ?? NO_VALUE}
            icon={<SnowflakeIcon size={17} />}
            iconColor={acOn ? 'var(--accent-cool)' : 'var(--text-muted)'}
            hint={
              status.acStatusSource === 'MANUAL'
                ? `${status.acMode} · set manually`
                : `${status.acMode} · vent probe`
            }
          />
          <StatTile
            label="AC Running Duration"
            value={formatDuration(liveAcSeconds)}
            icon={<TimerIcon size={17} />}
            iconColor={acOn ? 'var(--accent-cool)' : 'var(--text-muted)'}
            hint={
              acOn
                ? `HH:MM:SS · limit ${status.thresholds.acRuntimeLimitMinutes} min`
                : 'HH:MM:SS · time since AC switched off'
            }
            hintTone={
              acOn && liveAcSeconds > status.thresholds.acRuntimeLimitMinutes * 60 ? 'warning' : undefined
            }
          />
        </div>
      </div>

      <div className="dashboard__row-alert">
        <AlertBanner
          alert={status.alert}
          onDismiss={onDismissAlert}
          isDismissing={isDismissingAlert}
        />
        <RecommendationCard status={status} />
      </div>

      <div className="dashboard__row-charts">
        <Card
          title="Temperature History"
          tools={<RangeSelector hours={rangeHours} onChange={onRangeChange} />}
        >
          <HistoryChart
            points={history?.temperature}
            color="var(--series-temperature)"
            unit="°C"
            label="Temperature"
            yAxisLabel="°C"
            decimals={1}
          />
        </Card>

        <Card
          title="Person Count History"
          tools={<RangeSelector hours={rangeHours} onChange={onRangeChange} />}
        >
          <HistoryChart
            points={history?.personCount}
            color="var(--series-person)"
            unit="people"
            label="Person count"
            yAxisLabel="People"
            decimals={0}
            domainPadding={2}
            allowDecimalTicks={false}
            clampToZero
          />
        </Card>

        <Card
          title="Logs"
          subtitle="State changes only"
          tools={
            <button type="button" className="card__link" onClick={() => onNavigate('logs')}>
              View All
            </button>
          }
          flush
        >
          <LogsPanel events={logs} />
        </Card>
      </div>
    </div>
  )
}
