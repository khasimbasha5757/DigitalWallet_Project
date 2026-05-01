const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "") || "http://localhost:8090";

export const AUTH_STORAGE_KEY = "digital-wallet-auth";
export const AUTH_EXPIRED_EVENT = "digital-wallet-auth-expired";

function expireAuthSession() {
  window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
}

function collectMessage(payload) {
  if (typeof payload === "string" && payload.trim()) {
    return payload.trim();
  }

  if (!payload || typeof payload !== "object") {
    return "";
  }

  if (typeof payload.message === "string" && payload.message.trim()) {
    return payload.message.trim();
  }

  if (typeof payload.error === "string" && payload.error.trim()) {
    return payload.error.trim();
  }

  const values = Object.values(payload)
    .filter((value) => typeof value === "string" && value.trim())
    .map((value) => value.trim());

  return values[0] || "";
}

function normalizeErrorMessage(message, status, path) {
  const rawMessage = String(message || "").trim();
  const normalized = rawMessage.toLowerCase();

  if (
    status === 401 ||
    status === 403 ||
    normalized.includes("jwt signature does not match") ||
    normalized.includes("invalid authentication")
  ) {
    if (path !== "/api/auth/login") {
      expireAuthSession();
      return "Your session expired. Please sign in again.";
    }
  }

  if (
    status === 503 ||
    normalized.includes("connection refused") ||
    normalized.includes("i/o error on get request") ||
    normalized.includes("failed to connect") ||
    normalized.includes("no servers available")
  ) {
    return "The service is temporarily unavailable. Please try again.";
  }

  return rawMessage;
}

async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        ...(isFormData ? {} : { "Content-Type": "application/json" }),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
        ...(options.headers || {})
      },
      method: options.method || "GET",
      body: options.body ? (isFormData ? options.body : JSON.stringify(options.body)) : undefined
    });
  } catch (error) {
    throw new Error(
      "Unable to connect right now. Please try again."
    );
  }

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    let message = collectMessage(payload) || "Request failed";

    if (response.status === 401 && path === "/api/auth/login") {
      message = "Invalid username or password.";
    } else if (!message || !String(message).trim()) {
      message = `Request failed with status ${response.status}.`;
    }

    message = normalizeErrorMessage(message, response.status, path);

    throw new Error(message);
  }

  return payload;
}

export const api = {
  login: (body) => request("/api/auth/login", { method: "POST", body }),
  register: (body) => request("/api/auth/signup", { method: "POST", body }),
  checkPasswordResetAccount: (identifier) =>
    request(`/api/auth/forgot-password/account-exists?identifier=${encodeURIComponent(identifier)}`),
  requestPasswordResetOtp: (body) =>
    request("/api/auth/forgot-password/request-otp", { method: "POST", body }),
  verifyPasswordResetOtp: (body) =>
    request("/api/auth/forgot-password/verify-otp", { method: "POST", body }),
  resetPassword: (body) =>
    request("/api/auth/forgot-password/reset", { method: "POST", body }),
  getProfile: (userId, token) => request(`/api/auth/users/${userId}/profile`, { token }),
  updateProfile: (userId, body, token) =>
    request(`/api/auth/users/${userId}/profile`, { method: "POST", body, token }),
  changePassword: (userId, body, token) =>
    request(`/api/auth/users/${userId}/change-password`, { method: "POST", body, token }),
  getKycStatus: (token) => request("/api/users/kyc/status", { token }),
  submitKyc: (body, token) => {
    const formData = new FormData();
    formData.append("documentType", body.documentType);
    formData.append("documentNumber", body.documentNumber);
    formData.append("documentFile", body.documentFile);
    return request("/api/users/kyc", { method: "POST", body: formData, token });
  },
  getWalletBalance: (token) => request("/api/wallet/balance", { token }),
  topUpWallet: (body, token) => request("/api/wallet/topup", { method: "POST", body, token }),
  transferFunds: (body, token) => request("/api/wallet/transfer", { method: "POST", body, token }),
  getTransactions: (token) => request("/api/transactions/history", { token }),
  getLedgerBalance: (token) => request("/api/transactions/ledger-balance", { token }),
  getRewardsSummary: (token) => request("/api/rewards/summary", { token }),
  getRewardsCatalog: (token) => request("/api/rewards/catalog", { token }),
  redeemReward: (catalogId, token) =>
    request(`/api/rewards/redeem/${catalogId}`, { method: "POST", token }),
  getNotifications: (token) => request("/api/notifications", { token }),
  getAdminDashboard: (token) => request("/api/admin/dashboard", { token }),
  getCampaigns: (token) => request("/api/admin/campaigns", { token }),
  createCampaign: (body, token) => request("/api/admin/campaigns", { method: "POST", body, token }),
  getPendingKycs: (token) => request("/api/admin/kyc/pending", { token }),
  approveKyc: (userId, token) =>
    request(`/api/admin/kyc/${userId}/approve?documentReviewed=true`, { method: "POST", token }),
  rejectKyc: (userId, reason, token) =>
    request(`/api/admin/kyc/${userId}/reject?reason=${encodeURIComponent(reason || "")}`, {
      method: "POST",
      token
    }),
  createRewardCatalog: (body, token) =>
    request("/api/rewards/catalog", { method: "POST", body, token })
};
