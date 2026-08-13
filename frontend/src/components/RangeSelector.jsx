/**
 * Time-range control for the history charts.
 *
 * One piece of state drives every chart on the view, so changing the range on either
 * chart re-renders both against the same slice. The design places a control in each chart
 * header, which is kept - but they are two views of one selection, not two independent
 * per-chart filters, because charts side by side on different ranges invite a comparison
 * that is not valid.
 */
const RANGES = [
  { label: 'Today', hours: 24 },
  { label: '6 h', hours: 6 },
  { label: '1 h', hours: 1 },
  { label: '7 d', hours: 168 },
]

export function RangeSelector({ hours, onChange, options = RANGES, label = 'Time range' }) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.hours}
          type="button"
          className="segmented__option"
          aria-pressed={hours === option.hours}
          onClick={() => onChange(option.hours)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export { RANGES }
