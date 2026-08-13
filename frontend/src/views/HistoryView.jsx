import { Card } from '../components/Card'
import { HistoryChart } from '../components/HistoryChart'
import { RangeSelector } from '../components/RangeSelector'
import { formatDateTime } from '../utils/format'

/**
 * All three recorded series at a readable size.
 *
 * One range control above the charts, scoping all of them - the filter belongs to the
 * view, not to any single card, and every chart re-renders against the same slice.
 *
 * Humidity is a separate chart rather than a second line on the temperature axis. Two
 * measures on two y-scales in one plot invent a correlation that is not in the data.
 */
export function HistoryView({ history, rangeHours, onRangeChange }) {
  return (
    <div className="stack">
      <Card
        title="Recorded history"
        subtitle={
          history
            ? `${formatDateTime(history.from)} to ${formatDateTime(history.to)} · ${history.bucketMinutes}-minute averages`
            : 'Loading'
        }
        tools={<RangeSelector hours={rangeHours} onChange={onRangeChange} />}
      >
        <p className="note">
          Samples are averaged into fixed buckets. A break in a line is a gap in the data,
          not a reading of zero.
        </p>
      </Card>

      <div className="grid-2">
        <Card title="Temperature">
          <HistoryChart
            points={history?.temperature}
            color="var(--series-temperature)"
            unit="°C"
            label="Temperature"
            yAxisLabel="°C"
            decimals={1}
          />
        </Card>

        <Card title="Person count">
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

        <Card title="Humidity">
          <HistoryChart
            points={history?.humidity}
            color="var(--accent-cool)"
            unit="%"
            label="Humidity"
            yAxisLabel="% RH"
            decimals={0}
            domainPadding={3}
            allowDecimalTicks={false}
            clampToZero
          />
        </Card>
      </div>
    </div>
  )
}
