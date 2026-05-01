import { useEffect, useState } from "react";
import { AuthShell } from "../components/AuthShell";
import { useAuth } from "../context/AuthContext";
import { api } from "../lib/api";

const initialLogin = { username: "", password: "" };
const initialRegister = {
  username: "",
  email: "",
  password: "",
  fullName: "",
  phoneNumber: "",
  role: "USER"
};
const initialRecovery = { email: "", otp: "", newPassword: "" };

function trimFormValues(values) {
  return Object.fromEntries(
    Object.entries(values).map(([key, value]) => [key, typeof value === "string" ? value.trim() : value])
  );
}

function validateRecoveryStep(recoveryStep, recoveryForm) {
  if (recoveryStep === "request") {
    return "";
  }

  if (!recoveryForm.otp.trim()) {
    return "OTP is required.";
  }

  if (recoveryStep === "reset" && recoveryForm.newPassword.length < 6) {
    return "Password must be at least 6 characters.";
  }

  return "";
}

function extractMessage(payload, fallback) {
  if (typeof payload === "string" && payload.trim()) {
    return payload;
  }

  if (payload && typeof payload === "object") {
    const parts = [];
    if (typeof payload.message === "string" && payload.message.trim()) {
      parts.push(payload.message.trim());
    }
    if (parts.length) {
      return parts.join(" ");
    }
  }

  return fallback;
}

export function AuthPage() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState("login");
  const [loginForm, setLoginForm] = useState(initialLogin);
  const [registerForm, setRegisterForm] = useState(initialRegister);
  const [recoveryStep, setRecoveryStep] = useState("request");
  const [recoveryForm, setRecoveryForm] = useState(initialRecovery);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [showForgotPassword, setShowForgotPassword] = useState(false);

  useEffect(() => {
    setLoginForm(initialLogin);
  }, []);

  const handleLogin = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");

    try {
      await login(trimFormValues(loginForm));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleRegister = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");

    try {
      const normalizedRegisterForm = {
        ...trimFormValues(registerForm),
        password: registerForm.password
      };
      await register(normalizedRegisterForm);
      setMessage("Registration completed. You can sign in now.");
      setMode("login");
      setLoginForm({ ...initialLogin, username: normalizedRegisterForm.username });
      setRegisterForm(initialRegister);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const openForgotPassword = async () => {
    const identifier = loginForm.username.trim();
    if (!identifier) {
      setError("Enter your username or email first.");
      setMessage("");
      return;
    }

    setBusy(true);
    setError("");
    setMessage("");

    try {
      const response = await api.checkPasswordResetAccount(identifier);
      if (!response?.exists) {
        setError("Enter a registered username or email.");
        return;
      }

      setShowForgotPassword(true);
      setRecoveryStep("request");
      setRecoveryForm({
        ...initialRecovery,
        email: identifier.includes("@") ? identifier : ""
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const submitForgotPassword = async (event) => {
    event.preventDefault();
    const validationMessage = validateRecoveryStep(recoveryStep, recoveryForm);
    if (validationMessage) {
      setError(validationMessage);
      setMessage("");
      return;
    }

    setBusy(true);
    setError("");
    setMessage("");

    try {
      if (recoveryStep === "request") {
        const response = await api.requestPasswordResetOtp({
          email: recoveryForm.email.trim()
        });
        setMessage(extractMessage(response, "OTP sent to your email."));
        setRecoveryStep("verify");
      } else if (recoveryStep === "verify") {
        const response = await api.verifyPasswordResetOtp({
          email: recoveryForm.email.trim(),
          otp: recoveryForm.otp.trim()
        });
        setMessage(extractMessage(response, "OTP verified."));
        setRecoveryStep("reset");
      } else {
        const response = await api.resetPassword({
          email: recoveryForm.email.trim(),
          otp: recoveryForm.otp.trim(),
          newPassword: recoveryForm.newPassword
        });
        setMessage(extractMessage(response, "Password reset successful."));
        setShowForgotPassword(false);
        setRecoveryStep("request");
        setRecoveryForm(initialRecovery);
        setMode("login");
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell>
      <div className="auth-panel glass-panel w-full max-w-xl">
        {showForgotPassword ? (
          <>
            <div className="flex items-start justify-between gap-5">
              <div>
                <p className="auth-panel-eyebrow">Password reset</p>
                <h2 className="auth-panel-title">Reset your password</h2>
                <p className="auth-panel-copy">
                  Enter your email, verify the OTP, and set a new password.
                </p>
              </div>
              <button
                type="button"
                className="secondary-btn"
                onClick={() => {
                  setShowForgotPassword(false);
                  setRecoveryStep("request");
                  setRecoveryForm(initialRecovery);
                  setError("");
                  setMessage("");
                }}
              >
                Back
              </button>
            </div>

            {error ? (
              <div className="auth-alert auth-alert-error">
                {error}
              </div>
            ) : null}
            {message ? (
              <div className="auth-alert auth-alert-success">
                {message}
              </div>
            ) : null}

            <form className="mt-8 space-y-5" onSubmit={submitForgotPassword} autoComplete="off">
              <div>
                <label className="label">Email</label>
                  <input
                    className="field"
                    type="email"
                    name="recovery-email"
                    autoComplete="email"
                    value={recoveryForm.email}
                    onChange={(event) =>
                      setRecoveryForm((current) => ({ ...current, email: event.target.value }))
                    }
                  required
                  disabled={recoveryStep !== "request"}
                />
              </div>

              {recoveryStep !== "request" ? (
                <div>
                  <label className="label">OTP</label>
                  <input
                    className="field"
                    name="recovery-otp"
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    value={recoveryForm.otp}
                    onChange={(event) =>
                      setRecoveryForm((current) => ({ ...current, otp: event.target.value }))
                    }
                    placeholder="Enter the 6-digit OTP"
                    required
                  />
                </div>
              ) : null}

              {recoveryStep === "reset" ? (
                <div>
                  <label className="label">New Password</label>
                  <input
                    className="field"
                    type="password"
                    name="recovery-new-password"
                    autoComplete="new-password"
                    value={recoveryForm.newPassword}
                    onChange={(event) =>
                      setRecoveryForm((current) => ({ ...current, newPassword: event.target.value }))
                    }
                    placeholder="Use at least 6 characters"
                    minLength={6}
                    required
                  />
                  <p className="auth-help-text">Password must be at least 6 characters.</p>
                </div>
              ) : null}

              <button className="primary-btn w-full" disabled={busy}>
                {busy
                  ? "Please wait..."
                  : recoveryStep === "request"
                    ? "Send OTP"
                    : recoveryStep === "verify"
                      ? "Verify OTP"
                      : "Reset Password"}
              </button>
            </form>
          </>
        ) : (
          <>
        <div className="auth-mode-switch">
          {[
            ["login", "Sign In"],
            ["register", "Create Account"]
          ].map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => {
                setMode(key);
                setError("");
                setMessage("");
              }}
              className={`auth-mode-button ${
                mode === key ? "auth-mode-button-active" : ""
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="auth-panel-heading">
          <p className="auth-panel-eyebrow">{mode === "login" ? "Login" : "Account registration"}</p>
          <h2 className="auth-panel-title">
            {mode === "login" ? "Sign in to your account" : "Create your account"}
          </h2>
          <p className="auth-panel-copy">
            {mode === "login"
              ? "Enter your username or email and password."
              : "Enter your details to create a Digital Wallet account."}
          </p>
          {mode === "register" ? (
            <p className="auth-help-text">
              Use an active email address.
            </p>
          ) : null}
        </div>

        {error ? (
          <div className="auth-alert auth-alert-error">
            {error}
          </div>
        ) : null}
        {message ? (
          <div className="auth-alert auth-alert-success">
            {message}
          </div>
        ) : null}

        {mode === "login" ? (
          <form className="mt-8 space-y-5" onSubmit={handleLogin} autoComplete="off">
            <div>
              <label className="label">Username or Email</label>
              <input
                className="field"
                name="signin-user"
                autoComplete="off"
                value={loginForm.username}
                onChange={(event) =>
                  setLoginForm((current) => ({ ...current, username: event.target.value }))
                }
                placeholder="Enter username or email"
                required
              />
            </div>
            <div>
              <label className="label">Password</label>
              <input
                type="password"
                className="field"
                name="signin-password"
                autoComplete="new-password"
                value={loginForm.password}
                onChange={(event) =>
                  setLoginForm((current) => ({ ...current, password: event.target.value }))
                }
                placeholder="Enter password"
                required
              />
            </div>
            <div className="flex justify-end">
              <button
                type="button"
                className="auth-link-button"
                onClick={openForgotPassword}
                disabled={busy}
              >
                Forgot password?
              </button>
            </div>
            <button className="primary-btn w-full" disabled={busy}>
              {busy ? "Signing in..." : "Sign In"}
            </button>
          </form>
        ) : (
          <form className="mt-8 grid gap-5 md:grid-cols-2" onSubmit={handleRegister} autoComplete="off">
            <div>
              <label className="label">Full Name</label>
              <input
                className="field"
                autoComplete="off"
                value={registerForm.fullName}
                onChange={(event) =>
                  setRegisterForm((current) => ({ ...current, fullName: event.target.value }))
                }
                required
              />
            </div>
            <div>
              <label className="label">Username</label>
              <input
                className="field"
                autoComplete="off"
                value={registerForm.username}
                onChange={(event) =>
                  setRegisterForm((current) => ({ ...current, username: event.target.value }))
                }
                required
              />
            </div>
            <div>
              <label className="label">Email</label>
              <input
                type="email"
                className="field"
                autoComplete="off"
                value={registerForm.email}
                onChange={(event) =>
                  setRegisterForm((current) => ({ ...current, email: event.target.value }))
                }
                required
              />
            </div>
            <div>
              <label className="label">Phone Number</label>
              <input
                className="field"
                autoComplete="off"
                value={registerForm.phoneNumber}
                onChange={(event) =>
                  setRegisterForm((current) => ({ ...current, phoneNumber: event.target.value }))
                }
                required
              />
            </div>
            <div className="md:col-span-2">
              <label className="label">Password</label>
              <input
                type="password"
                className="field"
                autoComplete="new-password"
                value={registerForm.password}
                onChange={(event) =>
                  setRegisterForm((current) => ({ ...current, password: event.target.value }))
                }
                required
              />
            </div>
            <button className="primary-btn md:col-span-2" disabled={busy}>
              {busy ? "Creating account..." : "Create Account"}
            </button>
          </form>
        )}
        </>
        )}
      </div>
    </AuthShell>
  );
}
