import { Link } from 'react-router-dom'

export default function Logo({ to = '/', light = false }) {
  return (
    <Link to={to} className="flex items-center gap-2" aria-label="Campus Rider home">
      <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill={light ? '#ffffff' : '#0b0b0c'} />
        <circle cx="11" cy="21" r="4" fill="none" stroke={light ? '#0b0b0c' : '#fff'} strokeWidth="2.2" />
        <circle cx="22" cy="21" r="4" fill="none" stroke={light ? '#0b0b0c' : '#fff'} strokeWidth="2.2" />
        <path d="M11 21l4-9h5l2 9M15 12h-3" fill="none" stroke="#12a571" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className={`text-[17px] font-bold tracking-tight ${light ? 'text-white' : 'text-ink'}`}>Campus Rider</span>
    </Link>
  )
}
