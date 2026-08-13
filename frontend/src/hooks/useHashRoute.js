import { useCallback, useEffect, useState } from 'react'

/**
 * Minimal hash router.
 *
 * The dashboard has six flat views and no nested routes, so a routing library would be
 * a dependency for nothing. The hash keeps views linkable and survives a reload, which
 * matters for a screen someone leaves open on the Alerts view all day.
 */
export function useHashRoute(defaultRoute) {
  const read = () => window.location.hash.replace(/^#\/?/, '') || defaultRoute

  const [route, setRoute] = useState(read)

  useEffect(() => {
    const onHashChange = () => setRoute(read())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultRoute])

  const navigate = useCallback((next) => {
    window.location.hash = `/${next}`
  }, [])

  return [route, navigate]
}
