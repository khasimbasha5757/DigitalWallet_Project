export function AuthShell({ children }) {
  return (
    <div className="auth-page min-h-screen">
      <aside className="auth-hero" aria-hidden="true">
        <div className="auth-hero-content">
          <div className="auth-brand-row">
            <span className="auth-brand-mark">D</span>
            <div>
              <p className="auth-brand-name">Digital Wallet</p>
              <p className="auth-brand-subtitle">Control Center</p>
            </div>
          </div>
        </div>
      </aside>

      <main className="auth-form-area">{children}</main>
    </div>
  );
}
