import { useCallback, useEffect, useMemo, useState } from 'react'
import { acknowledgeAlert, fetchConfig, fetchHistory, fetchLogs, fetchStatus } from './api/client'
import { Sidebar } from './components/Sidebar'
import { TopBar } from './components/TopBar'
import { PlugOffIcon } from './components/Icons'
import { DEFAULT_ROUTE, resolveRoute } from './navigation'
import { useClock } from './hooks/useClock'
import { useHashRoute } from './hooks/useHashRoute'
import { usePolling } from './hooks/usePolling'
import { AlertsView } from './views/AlertsView'
import { Dashboard } from './views/Dashboard'
import { HistoryView } from './views/HistoryView'
import { LiveFeedView } from './views/LiveFeedView'
import { LogsView } from './views/LogsView'
import { SettingsView } from './views/SettingsView'
import { parseServerTime } from './utils/format'

/** History is polled far less often than status: it is bucketed averages, not live readings. */
const HISTORY_POLL_MS = 60_000
const LOGS_LIMIT = 8

/** Placeholder loader used until the room id is known, so hook order stays stable. */
function noopLoader() {
  return Promise.resolve(null)
}

export default function App() {
  const [route, navigate] = useHashRoute(DEFAULT_ROUTE)
  const now = useClock(1000)

  const [theme, setTheme] = useState(() => localStorage.getItem('smartroom.theme') ?? 'dark')
  const [roomId, setRoomId] = useState(null)
  const [rangeHours, setRangeHours] = useState(24)
  const [isDismissingAlert, setIsDismissingAlert] = useState(false)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('smartroom.theme', theme)
  }, [theme])

  // Config is fetched once: it carries the camera URL and the room list, neither of which
  // changes while the page is open.
  const { data: config, error: configError } = usePolling(fetchConfig, null)

  useEffect(() => {
    if (config && !roomId) {
      setRoomId(config.defaultRoomId ?? config.rooms?.[0] ?? 'ROOM101')
    }
  }, [config, roomId])

  const pollIntervalMs = (config?.pollIntervalSeconds ?? 5) * 1000

  const statusLoader = useCallback(() => (roomId ? fetchStatus(roomId) : noopLoader()), [roomId])
  const {
    data: status,
    error: statusError,
    isLoading: statusLoading,
    isRefreshing,
    refresh: refreshStatus,
  } = usePolling(statusLoader, roomId ? pollIntervalMs : null)

  const historyLoader = useCallback(
    () => (roomId ? fetchHistory(roomId, rangeHours) : noopLoader()),
    [roomId, rangeHours],
  )
  const { data: history } = usePolling(historyLoader, roomId ? HISTORY_POLL_MS : null)

  const logsLoader = useCallback(() => (roomId ? fetchLogs(roomId, LOGS_LIMIT) : noopLoader()), [roomId])
  const { data: logs, refresh: refreshLogs } = usePolling(logsLoader, roomId ? pollIntervalMs * 2 : null)

  /**
   * AC duration, ticking locally.
   *
   * Derived from (now - acSince) rather than rendering the polled acDurationSeconds, so the
   * timer counts up smoothly instead of holding still for five seconds and jumping. Falls
   * back to the server's figure when acSince is absent, and clamps at zero because a client
   * clock a few seconds behind the server would otherwise show a negative duration.
   */
  const liveAcSeconds = useMemo(() => {
    if (!status) {
      return 0
    }
    const since = parseServerTime(status.acSince)
    if (!since) {
      return status.acDurationSeconds ?? 0
    }
    return Math.max(0, Math.round((now.getTime() - since.getTime()) / 1000))
  }, [status, now])

  const alertCount = status?.alert ? 1 : 0

  const handleDismissAlert = useCallback(
    async (alertId) => {
      setIsDismissingAlert(true)
      try {
        await acknowledgeAlert(alertId)
        await Promise.all([refreshStatus(), refreshLogs()])
      } finally {
        setIsDismissingAlert(false)
      }
    },
    [refreshStatus, refreshLogs],
  )

  const meta = resolveRoute(route)
  const knownRoute = meta.id

  return (
    <div className="app">
      <Sidebar
        route={knownRoute}
        onNavigate={navigate}
        now={now}
        alertCount={alertCount}
        theme={theme}
        onToggleTheme={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
      />

      <main className="main">
        <TopBar
          title={meta.title}
          subtitle={meta.subtitle}
          systemStatus={status?.systemStatus ?? 'NORMAL'}
          alertCount={alertCount}
          rooms={config?.rooms}
          roomId={roomId ?? ''}
          onRoomChange={setRoomId}
          onBellClick={() => navigate('alerts')}
        />

        {/* A dropped poll dims the numbers and says so; it does not blank the screen. The
            last reading received is still the best information available. */}
        {(statusError || configError) && (
          <div className="offline-banner" role="status">
            <PlugOffIcon size={16} />
            <span>
              Cannot reach the backend
              {status ? ' — showing the last values received.' : '.'} Check that it is running
              on the configured address.
            </span>
          </div>
        )}

        {statusLoading && !status ? (
          <p className="empty-state">Connecting to the backend…</p>
        ) : !status ? (
          <p className="empty-state">
            No data yet. Start the backend, then post a reading — or run{' '}
            <code>python tools/simulate_telemetry.py --backfill-hours 8</code>.
          </p>
        ) : (
          <div className={isRefreshing ? 'is-refreshing' : undefined}>
            {knownRoute === 'dashboard' && (
              <Dashboard
                status={status}
                history={history}
                logs={logs}
                rangeHours={rangeHours}
                onRangeChange={setRangeHours}
                onDismissAlert={handleDismissAlert}
                isDismissingAlert={isDismissingAlert}
                liveAcSeconds={liveAcSeconds}
                cameraStreamUrl={config?.cameraStreamUrl}
                onNavigate={navigate}
              />
            )}

            {knownRoute === 'live-feed' && (
              <LiveFeedView
                status={status}
                cameraStreamUrl={config?.cameraStreamUrl}
                liveAcSeconds={liveAcSeconds}
              />
            )}

            {knownRoute === 'history' && (
              <HistoryView history={history} rangeHours={rangeHours} onRangeChange={setRangeHours} />
            )}

            {knownRoute === 'logs' && <LogsView roomId={roomId} pollIntervalMs={pollIntervalMs * 2} />}

            {knownRoute === 'alerts' && (
              <AlertsView
                roomId={roomId}
                pollIntervalMs={pollIntervalMs * 2}
                onChanged={refreshStatus}
              />
            )}

            {knownRoute === 'settings' && (
              <SettingsView
                status={status}
                config={config}
                roomId={roomId}
                onChanged={() => Promise.all([refreshStatus(), refreshLogs()])}
              />
            )}
          </div>
        )}
      </main>
    </div>
  )
}
