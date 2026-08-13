import { MoonIcon, SnowflakeIcon, SunIcon } from './Icons'
import { NAV_ITEMS } from '../navigation'
import { formatClockTime, formatLongDate } from '../utils/format'

export function Sidebar({ route, onNavigate, now, alertCount, theme, onToggleTheme }) {
  return (
    <aside className="sidebar">
      <div className="sidebar__brand">
        <span className="sidebar__brand-mark">
          <SnowflakeIcon size={17} />
        </span>
        <span className="sidebar__brand-text">
          <span className="sidebar__brand-title">Smart AC</span>
          <span className="sidebar__brand-sub">Controller</span>
        </span>
      </div>

      <nav className="sidebar__nav" aria-label="Main navigation">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon
          const isActive = route === item.id
          return (
            <button
              key={item.id}
              type="button"
              className={`sidebar__link${isActive ? ' sidebar__link--active' : ''}`}
              aria-current={isActive ? 'page' : undefined}
              onClick={() => onNavigate(item.id)}
            >
              <Icon size={15} />
              {item.label}
              {item.id === 'alerts' && alertCount > 0 && (
                <span className="sidebar__link-badge" aria-label={`${alertCount} active`}>
                  {alertCount}
                </span>
              )}
            </button>
          )
        })}
      </nav>

      <div className="sidebar__footer">
        {/* Local wall clock, ticking once a second - the reception desk reads this, and a
            time that only updated on each five-second poll would look frozen. */}
        <div className="sidebar__clock">{formatClockTime(now)}</div>
        <div className="sidebar__date">{formatLongDate(now)}</div>

        <button type="button" className="sidebar__theme" onClick={onToggleTheme}>
          {theme === 'dark' ? <SunIcon size={13} /> : <MoonIcon size={13} />}
          {theme === 'dark' ? 'Light theme' : 'Dark theme'}
        </button>
      </div>
    </aside>
  )
}
