export function StatCard({ eyebrow, title, value, accent = "from-aqua/25 to-coral/10" }) {
  return (
    <div className="stat-card stat-orbit rounded-2xl p-5 transition hover:-translate-y-1">
      <div className={`mesh-strip mb-5 h-1.5 rounded-full bg-gradient-to-r ${accent}`} />
      <p className="text-[11px] font-bold uppercase tracking-[0.22em] text-slate-400">{eyebrow}</p>
      <h3 className="mt-3 text-sm font-semibold text-slate-500">{title}</h3>
      <p className="mt-5 text-3xl font-black tracking-tight text-slate-950">{value}</p>
    </div>
  );
}
