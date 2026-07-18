import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const u =
        mode === "login"
          ? await login(username, password)
          : await register(username, password);
      navigate(u.role === "CUSTOMER" ? "/" : "/admin");
    } catch (err) {
      const data = err.response?.data;
      setError(
        data?.message ||
          (data?.fields && Object.values(data.fields).join("; ")) ||
          "Something went wrong. Is the server running?"
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="card auth-card">
        <h1 className="brand">OptiQueue</h1>
        <p className="muted">Concurrent order processing &amp; inventory</p>
        <form onSubmit={submit}>
          <label>
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              minLength={8}
              required
            />
          </label>
          {error && <div className="error">{error}</div>}
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "…" : mode === "login" ? "Log in" : "Create account"}
          </button>
        </form>
        <button
          className="btn btn-ghost"
          onClick={() => {
            setMode(mode === "login" ? "register" : "login");
            setError("");
          }}
        >
          {mode === "login"
            ? "New here? Create a customer account"
            : "Already have an account? Log in"}
        </button>
      </div>
    </div>
  );
}
