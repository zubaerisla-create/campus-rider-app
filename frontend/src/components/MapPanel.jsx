/**
 * Overlay panel on top of the full-size map: a floating card on desktop and a
 * bottom sheet on phones.
 */
export default function MapPanel({ children, side = 'left' }) {
  return (
    <aside
      className={`absolute inset-x-0 bottom-0 z-20 flex max-h-[56%] flex-col rounded-t-3xl bg-white shadow-[0_-8px_30px_rgba(0,0,0,0.12)]
        md:inset-x-auto md:bottom-4 md:top-4 md:max-h-none md:w-[400px] md:rounded-2xl md:shadow-[var(--shadow-float)]
        ${side === 'left' ? 'md:left-4' : 'md:right-4'}`}>
      <div className="mx-auto mt-2.5 h-1.5 w-10 shrink-0 rounded-full bg-line md:hidden" aria-hidden="true" />
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>
    </aside>
  )
}
