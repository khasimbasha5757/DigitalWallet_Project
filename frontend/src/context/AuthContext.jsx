import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api, AUTH_EXPIRED_EVENT, AUTH_STORAGE_KEY } from "../lib/api";
import { parseJwt } from "../lib/jwt";

const AuthContext = createContext(null);

function hydrateAuthState() {
  const raw = window.sessionStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return { token: "", user: null };
  }

  try {
    const parsed = JSON.parse(raw);
    return {
      token: parsed.token || "",
      user: parsed.user || null
    };
  } catch (error) {
    return { token: "", user: null };
  }
}

function buildUser(authResponse, token) {
  const claims = parseJwt(token);
  return {
    userId: authResponse?.userId || claims.userId || "",
    email: authResponse?.email || claims.sub || "",
    role: authResponse?.role || claims.role || "USER",
    fullName: authResponse?.fullName || claims.fullName || authResponse?.username || "Wallet User",
    phoneNumber: authResponse?.phoneNumber || claims.phoneNumber || "",
    profileImageUrl: authResponse?.profileImageUrl || ""
  };
}

export function AuthProvider({ children }) {
  const [authState, setAuthState] = useState(hydrateAuthState);

  const persist = (nextState) => {
    setAuthState(nextState);
    window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextState));
  };

  const login = async (credentials) => {
    const response = await api.login(credentials);
    const nextState = {
      token: response.token,
      user: buildUser(response, response.token)
    };
    persist(nextState);
    return response;
  };

  const register = async (payload) => {
    const response = await api.register(payload);
    return response;
  };

  const logout = () => {
    window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
    setAuthState({ token: "", user: null });
  };

  useEffect(() => {
    const handleAuthExpired = () => {
      setAuthState({ token: "", user: null });
    };

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  }, []);

  const syncUser = (profile) => {
    const nextState = {
      ...authState,
      user: {
        ...authState.user,
        userId: profile?.id || authState.user?.userId || "",
        email: profile?.email || authState.user?.email || "",
        role: profile?.role || authState.user?.role || "USER",
        fullName: profile?.fullName || authState.user?.fullName || "",
        phoneNumber: profile?.phoneNumber || authState.user?.phoneNumber || "",
        profileImageUrl: profile?.profileImageUrl || authState.user?.profileImageUrl || ""
      }
    };
    persist(nextState);
  };

  const value = useMemo(
    () => ({
      token: authState.token,
      user: authState.user,
      isAuthenticated: Boolean(authState.token),
      login,
      register,
      logout,
      syncUser
    }),
    [authState]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
