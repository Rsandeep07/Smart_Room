/** Panel primitive: optional title/subtitle, optional header tools, body. */
export function Card({ title, subtitle, tools, children, flush = false, className = '', ...rest }) {
  return (
    <section className={`card ${className}`} {...rest}>
      {(title || tools) && (
        <header className="card__header">
          <div>
            {title && <h2 className="card__title">{title}</h2>}
            {subtitle && <p className="card__subtitle">{subtitle}</p>}
          </div>
          {tools && <div className="card__tools">{tools}</div>}
        </header>
      )}
      <div className={`card__body${flush ? ' card__body--flush' : ''}`}>{children}</div>
    </section>
  )
}
