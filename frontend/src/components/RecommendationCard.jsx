import { CheckCircleIcon } from './Icons'
import { formatAdjustment, formatShortTime } from '../utils/format'

/**
 * The current recommendation in words, with the engine's working underneath.
 *
 * The breakdown line - occupancy base, temperature correction - is there because
 * Section 20.5's objection to the original rules was that an occupancy-only setpoint is
 * indefensible under examination. Showing how the number was reached answers that on the
 * screen rather than in the report.
 *
 * When hysteresis is holding a change back, that is stated. A dashboard that silently
 * lags the live value looks broken, which is the fastest way to lose an operator's trust.
 */
export function RecommendationCard({ status }) {
  const {
    recommendationMessage,
    recommendationBase,
    recommendationAdjustment,
    recommendedTemperature,
    pendingRecommendedTemperature,
    recommendationHoldUntil,
  } = status

  const hasBreakdown = recommendationBase !== null && recommendationBase !== undefined

  return (
    <div className="recommendation">
      <span className="recommendation__icon">
        <CheckCircleIcon size={17} />
      </span>

      <div>
        <div className="recommendation__label">Current Recommendation</div>
        <p className="recommendation__text">
          {recommendationMessage ?? 'Waiting for the first readings.'}
        </p>

        {hasBreakdown && (
          <p className="recommendation__meta">
            {recommendationBase} °C from occupancy, {formatAdjustment(recommendationAdjustment)} from
            measured temperature
            {recommendedTemperature !== null && recommendedTemperature !== undefined
              ? ` → ${recommendedTemperature} °C`
              : ''}
          </p>
        )}

        {pendingRecommendedTemperature !== null && pendingRecommendedTemperature !== undefined && (
          <p className="recommendation__meta">
            Change to {pendingRecommendedTemperature} °C is held until{' '}
            {formatShortTime(recommendationHoldUntil)} to stop the value flickering.
          </p>
        )}
      </div>
    </div>
  )
}
