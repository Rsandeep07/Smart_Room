/**
 * Thin fetch wrapper around the backend API.
 *
 * Read endpoints only. The dashboard never posts telemetry, and the ingest API key is
 * deliberately not present in this bundle - anything shipped to a browser is public,
 * so putting the shared secret here would publish it.
 *
 * The one write the dashboard does make is operator intent, not telemetry: dismissing
 * an alert and overriding AC state.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/** Raised for a non-2xx response, carrying the backend's ApiError body when there is one. */
export class ApiError extends Error {
  constructor(message, status, body) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
    ...options,
  })

  if (!response.ok) {
    // The backend returns a JSON ApiError for handled failures, but a proxy or a dead
    // server will not, so parsing has to be allowed to fail.
    let body = null
    try {
      body = await response.json()
    } catch {
      body = null
    }
    throw new ApiError(body?.message ?? `Request failed with ${response.status}`, response.status, body)
  }

  if (response.status === 204) {
    return null
  }
  return response.json()
}

export function fetchConfig() {
  return request('/api/config')
}

export function fetchStatus(roomId) {
  return request(`/api/room/${encodeURIComponent(roomId)}/status`)
}

export function fetchHistory(roomId, hours) {
  return request(`/api/room/${encodeURIComponent(roomId)}/history?hours=${hours}`)
}

export function fetchLogs(roomId, limit = 20) {
  return request(`/api/room/${encodeURIComponent(roomId)}/logs?limit=${limit}`)
}

export function fetchAlerts(roomId, limit = 50) {
  return request(`/api/room/${encodeURIComponent(roomId)}/alerts?limit=${limit}`)
}

export function acknowledgeAlert(alertId) {
  return request(`/api/alerts/${alertId}/acknowledge`, { method: 'POST' })
}

export function setAcStatus(roomId, status, note) {
  return request(`/api/room/${encodeURIComponent(roomId)}/ac`, {
    method: 'POST',
    body: JSON.stringify({ status, note }),
  })
}

export function clearAcOverride(roomId) {
  return request(`/api/room/${encodeURIComponent(roomId)}/ac`, { method: 'DELETE' })
}
