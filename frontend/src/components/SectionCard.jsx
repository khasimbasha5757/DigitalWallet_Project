export function SectionCard({ title, subtitle, actions, children, className = "" }) {
  return (
    <section className={`section-card rounded-2xl p-6 ${className}`}>
      <div className="mb-5 flex flex-col gap-4 border-b border-slate-200 pb-5 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="text-[1.35rem] font-bold tracking-tight text-slate-950">{title}</h2>
          {subtitle ? <p className="mt-2 text-sm font-medium text-slate-500">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap gap-3">{actions}</div> : null}
      </div>
      {children}
    </section>
  );
}
