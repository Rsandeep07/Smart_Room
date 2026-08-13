/** Display helpers. All of them tolerate null, because every reading can be absent. */

/** Em dash for "no reading", never 0 - a missing sensor is not a temperature of zero. */
export const NO_VALUE = '—'

export function formatNumber(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return NO_VALUE
  }
  return value.toFixed(digits)
}

export function formatInteger(value) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return NO_VALUE
  }
  return String(Math.round(value))
}

/** HH:MM:SS, matching the AC Running Duration tile. */
export function formatDuration(totalSeconds) {
  if (totalSeconds === null || totalSeconds === undefined || Number.isNaN(totalSeconds)) {
    return '00:00:00'
  }
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(Math.floor(seconds / 3600))}:${pad(Math.floor((seconds % 3600) / 60))}:${pad(seconds % 60)}`
}

/** "1 h 15 min" / "45 min" - for prose, where HH:MM:SS reads as machine output. */
export function formatDurationWords(totalSeconds) {
  if (!totalSeconds || totalSeconds < 60) {
    return 'less than a minute'
  }
  const minutes = Math.floor(totalSeconds / 60)
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  if (hours > 0) {
    return remainder > 0 ? `${hours} h ${remainder} min` : `${hours} h`
  }
  return `${minutes} min`
}

/**
 * The backend sends LocalDateTime, which has no offset. It is the server's local time
 * and must be read as such - appending 'Z' or letting the browser guess would shift
 * every timestamp by the machine's UTC offset and put "now" in the future.
 */
export function parseServerTime(value) {
  if (!value) {
    return null
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

export function formatTime(value) {
  const date = value instanceof Date ? value : parseServerTime(value)
  if (!date) {
    return NO_VALUE
  }
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function formatClockTime(date) {
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function formatShortTime(value) {
  const date = value instanceof Date ? value : parseServerTime(value)
  if (!date) {
    return NO_VALUE
  }
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

export function formatLongDate(date) {
  return date.toLocaleDateString(undefined, {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    weekday: 'long',
  })
}

export function formatDateTime(value) {
  const date = value instanceof Date ? value : parseServerTime(value)
  if (!date) {
    return NO_VALUE
  }
  return `${date.toLocaleDateString(undefined, { day: '2-digit', month: 'short' })} ${formatTime(date)}`
}

/** "4 s ago" / "2 min ago" - for freshness, where an absolute time makes the reader do arithmetic. */
export function formatAge(value, now = Date.now()) {
  const date = value instanceof Date ? value : parseServerTime(value)
  if (!date) {
    return NO_VALUE
  }
  const seconds = Math.max(0, Math.round((now - date.getTime()) / 1000))
  if (seconds < 60) {
    return `${seconds} s ago`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes} min ago`
  }
  const hours = Math.floor(minutes / 60)
  return hours < 24 ? `${hours} h ago` : `${Math.floor(hours / 24)} d ago`
}

/** Signed Kelvin correction, e.g. "+1 K", for the recommendation breakdown. */
export function formatAdjustment(adjustment) {
  if (!adjustment) {
    return '0 K'
  }
  return `${adjustment > 0 ? '+' : ''}${adjustment} K`
}
