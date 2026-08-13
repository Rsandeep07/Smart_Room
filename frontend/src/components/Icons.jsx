/**
 * Inline SVG icons.
 *
 * Inline rather than an icon package: the dashboard needs about fifteen glyphs, and
 * they inherit `currentColor` so a tile's accent is set once in CSS. Every icon here
 * accompanies a text label - none of them carries meaning on its own.
 */

const base = {
  width: 16,
  height: 16,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.9,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
}

function Icon({ size = 16, children, ...rest }) {
  return (
    <svg {...base} width={size} height={size} {...rest}>
      {children}
    </svg>
  )
}

export const SnowflakeIcon = (props) => (
  <Icon {...props}>
    <path d="M12 2v20M4.5 6.5l15 11M19.5 6.5l-15 11" />
    <path d="M12 6l2.5-2.5M12 6L9.5 3.5M12 18l2.5 2.5M12 18l-2.5 2.5" />
  </Icon>
)

export const GaugeIcon = (props) => (
  <Icon {...props}>
    <path d="M12 14l3.5-3.5" />
    <path d="M3.5 17a9 9 0 1 1 17 0" />
    <circle cx="12" cy="14" r="1.4" />
  </Icon>
)

export const CameraIcon = (props) => (
  <Icon {...props}>
    <path d="M3 8.5A2.5 2.5 0 0 1 5.5 6h1.9l1.2-1.8h6.8L16.6 6h1.9A2.5 2.5 0 0 1 21 8.5v8A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5z" />
    <circle cx="12" cy="12.5" r="3.2" />
  </Icon>
)

export const ChartIcon = (props) => (
  <Icon {...props}>
    <path d="M4 19V5M4 19h16" />
    <path d="M7.5 15.5l3.5-4 3 2.5 4.5-6" />
  </Icon>
)

export const ListIcon = (props) => (
  <Icon {...props}>
    <path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01" />
  </Icon>
)

export const BellIcon = (props) => (
  <Icon {...props}>
    <path d="M18 9A6 6 0 0 0 6 9c0 5-2 6.5-2 6.5h16S18 14 18 9z" />
    <path d="M10.3 19a2 2 0 0 0 3.4 0" />
  </Icon>
)

export const SettingsIcon = (props) => (
  <Icon {...props}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2V21a2 2 0 1 1-4 0v-.1A1.7 1.7 0 0 0 7 19.4l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0-1.2-2.9H3a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 4.6 7l-.1-.1a2 2 0 1 1 2.8-2.8L7.4 4a1.7 1.7 0 0 0 2.9-1.2V3a2 2 0 1 1 4 0v.1A1.7 1.7 0 0 0 17 4.6l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1A1.7 1.7 0 0 0 21 10.3h.1a2 2 0 1 1 0 4H21a1.7 1.7 0 0 0-1.6 1z" />
  </Icon>
)

export const PeopleIcon = (props) => (
  <Icon {...props}>
    <path d="M16 19v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 17.5V19" />
    <circle cx="10" cy="8" r="3.2" />
    <path d="M20 19v-1.5a3.5 3.5 0 0 0-2.6-3.4M15.5 5.2a3.2 3.2 0 0 1 0 5.6" />
  </Icon>
)

export const ThermometerIcon = (props) => (
  <Icon {...props}>
    <path d="M14 14.8V4.5a2 2 0 1 0-4 0v10.3a4 4 0 1 0 4 0z" />
    <path d="M12 8.5v6.8" />
  </Icon>
)

export const DropletIcon = (props) => (
  <Icon {...props}>
    <path d="M12 3s6 6 6 10a6 6 0 0 1-12 0c0-4 6-10 6-10z" />
  </Icon>
)

export const TimerIcon = (props) => (
  <Icon {...props}>
    <circle cx="12" cy="13" r="8" />
    <path d="M12 9.5V13l2.5 1.8M9 2.5h6" />
  </Icon>
)

export const TargetIcon = (props) => (
  <Icon {...props}>
    <circle cx="12" cy="12" r="8.5" />
    <circle cx="12" cy="12" r="4.5" />
    <circle cx="12" cy="12" r="1" />
  </Icon>
)

export const WarningIcon = (props) => (
  <Icon {...props}>
    <path d="M12 3.8 2.8 19.4h18.4z" />
    <path d="M12 9.5v4.4M12 16.8h.01" />
  </Icon>
)

export const CheckCircleIcon = (props) => (
  <Icon {...props}>
    <circle cx="12" cy="12" r="8.8" />
    <path d="M8.2 12.4l2.6 2.6 5-5.4" />
  </Icon>
)

export const CloseIcon = (props) => (
  <Icon {...props}>
    <path d="M6 6l12 12M18 6L6 18" />
  </Icon>
)

export const PlugOffIcon = (props) => (
  <Icon {...props}>
    <path d="M3 3l18 18" />
    <path d="M9 7V3M15 7V3" />
    <path d="M7 7h10v4a5 5 0 0 1-5 5 5 5 0 0 1-5-5z" />
    <path d="M12 16v5" />
  </Icon>
)

export const SunIcon = (props) => (
  <Icon {...props}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </Icon>
)

export const MoonIcon = (props) => (
  <Icon {...props}>
    <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z" />
  </Icon>
)
