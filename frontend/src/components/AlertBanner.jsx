import { CheckCircleIcon, CloseIcon, WarningIcon } from './Icons'
import { formatTime } from '../utils/format'

/**
 * The sticky alert banner (build plan Step 6.5).
 *
 * Severity is carried by an icon and the word ALERT as well as by the amber wash, so the
 * state never depends on colour alone. `role="alert"` announces it to a screen reader the
 * moment it appears, which is the point of an alert nobody is watching for.
 *
 * When there is nothing to report the slot keeps its height and says so. Collapsing it
 * would shift the charts up and down as alerts come and go.
 */
export function AlertBanner({ alert, onDismiss, isDismissing }) {
  if (!alert) {
    return (
      <div className="alert-banner alert-banner--placeholder">
        <span className="alert-banner__icon">
          <CheckCircleIcon size={17} />
        </span>
        <div className="alert-banner__body">
          <p className="alert-banner__message" style={{ margin: 0, color: 'var(--text-secondary)' }}>
            No active alerts. Room conditions are within the configured limits.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="alert-banner" role="alert">
      <span className="alert-banner__icon">
        <WarningIcon size={18} />
      </span>

      <div className="alert-banner__body">
        <div className="alert-banner__title">
          ALERT
          <span className="alert-banner__time">{formatTime(alert.createdAt)}</span>
        </div>
        <p className="alert-banner__message">{alert.message}</p>
      </div>

      <button
        type="button"
        className="alert-banner__dismiss"
        onClick={() => onDismiss(alert.id)}
        disabled={isDismissing}
        aria-label="Dismiss alert"
        title="Dismiss alert"
      >
        <CloseIcon size={15} />
      </button>
    </div>
  )
}
