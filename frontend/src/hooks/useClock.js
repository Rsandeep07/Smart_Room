import { useEffect, useState } from 'react'

/**
 * A once-a-second ticking clock for the sidebar, and for anything that has to keep
 * counting between polls.
 *
 * The AC running duration is the reason this exists. The status endpoint is polled every
 * five seconds, so a duration rendered straight from the response would sit still and
 * then jump five seconds. Ticking locally and adding the elapsed time since the poll
 * makes it count up like the timer it represents.
 */
export function useClock(intervalMs = 1000) {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])

  return now
}
