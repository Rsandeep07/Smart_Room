import { WarningIcon } from './Icons'

/**
 * A KPI tile: label, icon, value, unit, optional hint.
 *
 * The form choice is deliberate - each of these is a single current value, and the
 * data-viz guidance is explicit that a single value is a stat tile, not a one-bar chart.
 *
 * The value text never wears the series colour. The icon beside it carries the accent;
 * the number stays in primary ink, because a mid-lightness hue is hard to read as text
 * and colour alone must never be the thing that identifies a reading.
 *
 * @param stale when true the tile is flagged as showing a reading the producer has
 *              stopped updating, rather than quietly presenting it as current
 */
export function StatTile({
  label,
  value,
  unit,
  hint,
  hintTone,
  icon,
  iconColor,
  stale = false,
  staleLabel = 'No recent reading',
}) {
  return (
    <div className={`tile${stale ? ' tile--stale' : ''}`}>
      <span className="tile__label">{label}</span>

      <div className="tile__row">
        {icon && (
          <span className="tile__icon" style={iconColor ? { color: iconColor } : undefined}>
            {icon}
          </span>
        )}
        <span className="tile__value">
          <span className="tile__number">{value}</span>
          {unit && <span className="tile__unit">{unit}</span>}
        </span>
      </div>

      {stale ? (
        <span className="tile__stale-flag">
          <WarningIcon size={12} />
          {staleLabel}
        </span>
      ) : (
        hint && <span className={`tile__hint${hintTone === 'warning' ? ' tile__hint--warning' : ''}`}>{hint}</span>
      )}
    </div>
  )
}
