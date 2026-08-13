import {
  BellIcon,
  CameraIcon,
  ChartIcon,
  GaugeIcon,
  ListIcon,
  SettingsIcon,
} from './components/Icons'

/**
 * The six views, in sidebar order.
 *
 * Kept out of `Sidebar.jsx` so that file only exports a component - a module that mixes
 * component and non-component exports breaks Vite's fast refresh.
 */
export const NAV_ITEMS = [
  { id: 'dashboard', label: 'Dashboard', icon: GaugeIcon, title: 'Dashboard', subtitle: 'Real-time monitoring' },
  { id: 'live-feed', label: 'Live Feed', icon: CameraIcon, title: 'Live Feed', subtitle: 'ESP32-CAM stream and current readings' },
  { id: 'history', label: 'History', icon: ChartIcon, title: 'History', subtitle: 'Recorded temperature, occupancy and humidity' },
  { id: 'logs', label: 'Logs', icon: ListIcon, title: 'Logs', subtitle: 'Everything the system has done' },
  { id: 'alerts', label: 'Alerts', icon: BellIcon, title: 'Alerts', subtitle: 'Cold-room alerts and their outcomes' },
  { id: 'settings', label: 'Settings', icon: SettingsIcon, title: 'Settings', subtitle: 'Engine configuration and AC status source' },
]

export const DEFAULT_ROUTE = 'dashboard'

/** Falls back to the dashboard for an unrecognised hash, so a stale bookmark still loads. */
export function resolveRoute(route) {
  return NAV_ITEMS.find((item) => item.id === route) ?? NAV_ITEMS[0]
}
