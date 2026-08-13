import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Polls an async loader on an interval (build plan Step 6.6).
 *
 * Two behaviours matter more than they look:
 *
 * 1. `data` is held across a refetch. Clearing it to show a spinner every five seconds
 *    would make the dashboard flash and the layout jump under the operator's cursor;
 *    `isRefreshing` lets the caller dim the stale render instead.
 * 2. A failed poll does not discard the last good `data`. A single dropped request on
 *    campus Wi-Fi should dim the numbers and raise a connection banner, not blank the
 *    screen - the readings from four seconds ago are still the best information there is.
 *
 * @param loader async function returning the payload; must be stable (useCallback)
 * @param intervalMs polling period; null or 0 disables polling and loads once
 */
export function usePolling(loader, intervalMs) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null)

  // Guards against a slow response landing after the component unmounted or after the
  // loader changed (room switch), which would write the wrong room's data into state.
  const generation = useRef(0)

  const load = useCallback(
    async (isInitial) => {
      const current = ++generation.current
      if (isInitial) {
        setIsLoading(true)
      } else {
        setIsRefreshing(true)
      }
      try {
        const result = await loader()
        if (current !== generation.current) {
          return
        }
        setData(result)
        setError(null)
        setLastUpdatedAt(Date.now())
      } catch (e) {
        if (current !== generation.current) {
          return
        }
        setError(e)
      } finally {
        if (current === generation.current) {
          setIsLoading(false)
          setIsRefreshing(false)
        }
      }
    },
    [loader],
  )

  useEffect(() => {
    let timer = null
    let cancelled = false

    const run = async (isInitial) => {
      if (cancelled) {
        return
      }
      await load(isInitial)
      if (!cancelled && intervalMs) {
        // setTimeout chained after completion rather than setInterval: a slow response
        // must not let requests pile up on top of each other.
        timer = setTimeout(() => run(false), intervalMs)
      }
    }

    run(true)

    return () => {
      cancelled = true
      generation.current += 1
      if (timer) {
        clearTimeout(timer)
      }
    }
  }, [load, intervalMs])

  const refresh = useCallback(() => load(false), [load])

  return { data, error, isLoading, isRefreshing, lastUpdatedAt, refresh }
}
