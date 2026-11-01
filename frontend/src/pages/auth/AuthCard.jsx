export default function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="mx-auto flex max-w-md flex-col px-5 py-12 md:py-16">
      <h1 className="text-[28px] font-bold tracking-tight">{title}</h1>
      {subtitle && <p className="mt-1.5 text-muted">{subtitle}</p>}
      <div className="mt-8">{children}</div>
      {footer && <div className="mt-6 text-center text-sm text-muted">{footer}</div>}
    </div>
  )
}
