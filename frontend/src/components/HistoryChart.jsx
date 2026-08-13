import { useId, useMemo, useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatShortTime, formatTime, NO_VALUE, parseServerTime } from '../utils/format'

/**
 * Single-series area chart over time, with a table-view twin.
 *
 * Form: trend over time, one series -> an area chart, and no legend. A legend box with
 * one swatch would only restate the card title.
 *
 * Marks follow the fixed specs: 2px line, area fill at ~10% opacity (a wash, never a
 * saturated block), solid hairline gridlines one step off the surface, and no dashing.
 * Value labels are selective - the current reading is called out under the plot and the
 * rest of the values are reachable from the crosshair tooltip and the table view, rather
 * than a number printed on every point.
 *
 * Gaps are real. `connectNulls` is left off so a Wi-Fi outage draws as a break in the
 * line instead of a straight interpolation across missing data.
 */
export function HistoryChart({
  points,
  color,
  unit,
  label,
  yAxisLabel,
  decimals = 1,
  domainPadding = 1,
  allowDecimalTicks = true,
  /** Floor the axis at zero. A person count cannot be negative, and an axis that
      offers "-2 people" as a tick reads as a bug in the counting. */
  clampToZero = false,
}) {
  const [showTable, setShowTable] = useState(false)
  const gradientId = useId().replace(/:/g, '')

  const data = useMemo(
    () =>
      (points ?? []).map((point) => {
        const date = parseServerTime(point.t)
        return { time: date ? date.getTime() : 0, value: point.value }
      }),
    [points],
  )

  const measured = useMemo(() => data.filter((d) => d.value !== null && d.value !== undefined), [data])

  const latest = measured.length > 0 ? measured[measured.length - 1] : null

  /**
   * Rounded domain with a little air.
   *
   * A room sits between about 19 and 30 degrees, so a zero-based axis would squeeze the
   * whole day into the top fifth of the plot and hide exactly the variation the chart
   * exists to show. The axis label states the unit so the non-zero baseline is not
   * misleading, and the domain is rounded to whole steps rather than the data extremes.
   */
  const domain = useMemo(() => {
    if (measured.length === 0) {
      return ['auto', 'auto']
    }
    const values = measured.map((d) => d.value)
    let lower = Math.floor(Math.min(...values) - domainPadding)
    if (clampToZero) {
      lower = Math.max(0, lower)
    }
    return [lower, Math.ceil(Math.max(...values) + domainPadding)]
  }, [measured, domainPadding, clampToZero])

  if (measured.length === 0) {
    return (
      <div className="chart">
        <div className="chart__empty">
          No {label.toLowerCase()} recorded in this range yet.
        </div>
      </div>
    )
  }

  return (
    <div className="chart">
      <div className="chart__axis-label">{yAxisLabel}</div>

      {showTable ? (
        <div className="data-table-wrap">
          <table className="data-table">
            <caption className="visually-hidden">{label} readings</caption>
            <thead>
              <tr>
                <th scope="col">Time</th>
                <th scope="col">
                  {label} ({unit})
                </th>
              </tr>
            </thead>
            <tbody>
              {[...data].reverse().map((row) => (
                <tr key={row.time}>
                  <td>{formatTime(new Date(row.time))}</td>
                  <td className={row.value === null || row.value === undefined ? 'is-gap' : undefined}>
                    {row.value === null || row.value === undefined ? NO_VALUE : row.value.toFixed(decimals)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="chart__plot">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 6, right: 10, bottom: 0, left: -18 }}>
              <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={color} stopOpacity={0.18} />
                  <stop offset="100%" stopColor={color} stopOpacity={0.02} />
                </linearGradient>
              </defs>

              {/* Solid hairlines, horizontal only: vertical rules here would compete
                  with the crosshair, which is the thing the reader is meant to follow. */}
              <CartesianGrid stroke="var(--gridline)" strokeWidth={1} vertical={false} />

              <XAxis
                dataKey="time"
                type="number"
                scale="time"
                domain={['dataMin', 'dataMax']}
                tickFormatter={(value) => formatShortTime(new Date(value))}
                tick={{ fill: 'var(--text-muted)', fontSize: 10 }}
                stroke="var(--gridline)"
                tickLine={false}
                minTickGap={38}
              />
              <YAxis
                domain={domain}
                allowDecimals={allowDecimalTicks}
                tick={{ fill: 'var(--text-muted)', fontSize: 10 }}
                stroke="var(--gridline)"
                tickLine={false}
                width={44}
              />

              <Tooltip
                cursor={{ stroke: 'var(--border-strong)', strokeWidth: 1 }}
                content={({ active, payload }) => {
                  if (!active || !payload?.length) {
                    return null
                  }
                  const point = payload[0].payload
                  return (
                    <div className="tooltip">
                      <div className="tooltip__time">{formatTime(new Date(point.time))}</div>
                      <div className="tooltip__value">
                        <span className="chart__swatch" style={{ background: color }} />
                        {point.value === null || point.value === undefined
                          ? 'no reading'
                          : `${point.value.toFixed(decimals)} ${unit}`}
                      </div>
                    </div>
                  )
                }}
              />

              <Area
                type="monotone"
                dataKey="value"
                stroke={color}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                fill={`url(#${gradientId})`}
                connectNulls={false}
                isAnimationActive={false}
                /* >= 8px with a 2px surface-coloured ring, so the marker stays legible
                   where it crosses the line and is large enough to hover. */
                activeDot={{ r: 4, fill: color, stroke: 'var(--surface-1)', strokeWidth: 2 }}
                dot={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      <div className="chart__footnote">
        <span className="chart__current">
          <span className="chart__swatch" style={{ background: color }} />
          Now {latest ? `${latest.value.toFixed(decimals)} ${unit}` : NO_VALUE}
        </span>
        <button
          type="button"
          className="toggle-link"
          aria-pressed={showTable}
          onClick={() => setShowTable((v) => !v)}
        >
          {showTable ? 'Chart' : 'Table'}
        </button>
      </div>
    </div>
  )
}
