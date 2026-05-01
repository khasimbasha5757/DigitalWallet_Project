import { useEffect, useRef, useState } from "react";
import { EmptyState } from "../components/EmptyState";
import { SectionCard } from "../components/SectionCard";
import { StatCard } from "../components/StatCard";
import { ThemeSwitcher } from "../components/ThemeSwitcher";
import { api } from "../lib/api";
import { formatDate, titleize } from "../lib/format";
import { optimizeProfileImage } from "../lib/images";

const navItems = [
  ["overview", "Overview"],
  ["kyc", "KYC Queue"],
  ["campaigns", "Campaigns"],
  ["catalog", "Reward Catalog"],
  ["notifications", "Notifications"],
  ["profile", "Profile"]
];

const initialCampaign = {
  name: "",
  rewardPoints: "50",
  triggerEvent: "FIRST_TRANSACTION",
  targetTier: "ALL",
  status: "ACTIVE"
};

const initialCatalog = {
  name: "",
  description: "",
  costInPoints: "",
  stockQuantity: "",
  requiredTier: "ALL"
};

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "") || "http://localhost:8090";

function normalizeDocumentUrl(value) {
  const documentValue = String(value || "").trim();
  if (!documentValue) {
    return "";
  }
  return documentValue.replace(/^http:\/\/localhost:8082/i, API_BASE_URL);
}

function isPdfDocumentUrl(value) {
  const documentValue = normalizeDocumentUrl(value);
  return documentValue.startsWith("data:application/pdf") || /^https?:\/\/.+\.pdf($|[?#].*)/i.test(documentValue);
}

function buildInitials(name) {
  return String(name || "Admin")
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");
}

export function AdminWorkspace({ token, user, logout, syncUser }) {
  const [activeTab, setActiveTab] = useState("overview");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busyAction, setBusyAction] = useState("");
  const [campaignForm, setCampaignForm] = useState(initialCampaign);
  const [catalogForm, setCatalogForm] = useState(initialCatalog);
  const [reviewedKycIds, setReviewedKycIds] = useState(() => new Set());
  const [documentPreviews, setDocumentPreviews] = useState({});
  const [profileForm, setProfileForm] = useState({
    fullName: "",
    email: "",
    username: "",
    phoneNumber: "",
    profileImageUrl: ""
  });
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [isProfileImageProcessing, setIsProfileImageProcessing] = useState(false);
  const [isPasswordEditing, setIsPasswordEditing] = useState(false);
  const loadSequenceRef = useRef(0);
  const [data, setData] = useState({
    dashboard: null,
    campaigns: [],
    pendingKycs: [],
    rewardsCatalog: [],
    notifications: [],
    profile: null
  });

  const adminNotificationTopics = new Set(["kyc.status.updated"]);

  const loadWorkspace = async () => {
    const requestId = ++loadSequenceRef.current;
    setError("");
    const [dashboard, campaigns, pendingKycs, rewardsCatalog, notifications, profile] = await Promise.all([
      api.getAdminDashboard(token),
      api.getCampaigns(token),
      api.getPendingKycs(token),
      api.getRewardsCatalog(token),
      api.getNotifications(token),
      api.getProfile(user.userId, token)
    ]);

    if (requestId !== loadSequenceRef.current) {
      return;
    }

    setData({
      dashboard,
      campaigns,
      pendingKycs,
      rewardsCatalog,
      notifications: Array.isArray(notifications)
        ? notifications.filter((item) => adminNotificationTopics.has(String(item.topic || "").toLowerCase()))
        : [],
      profile
    });
    setProfileForm({
      fullName: profile?.fullName || "",
      email: profile?.email || "",
      username: profile?.username || "",
      phoneNumber: profile?.phoneNumber || "",
      profileImageUrl: profile?.profileImageUrl || ""
    });
  };

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      try {
        await loadWorkspace();
      } catch (err) {
        if (!cancelled) {
          setError(err.message);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    bootstrap();
    return () => {
      cancelled = true;
    };
  }, []);

  const refresh = async () => {
    setRefreshing(true);
    setNotice("");
    try {
      await loadWorkspace();
    } catch (err) {
      setError(err.message);
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    const objectUrls = [];
    let cancelled = false;

    async function loadDocumentPreviews() {
      const entries = await Promise.all(
        data.pendingKycs
          .filter((item) => isPdfDocumentUrl(item.documentUrl))
          .map(async (item) => {
            try {
              const response = await fetch(normalizeDocumentUrl(item.documentUrl), {
                headers: { Authorization: `Bearer ${token}` }
              });
              if (!response.ok) {
                throw new Error("PDF unavailable");
              }
              const blob = await response.blob();
              const objectUrl = URL.createObjectURL(blob);
              objectUrls.push(objectUrl);
              return [item.userId, objectUrl];
            } catch (error) {
              return [item.userId, ""];
            }
          })
      );

      if (!cancelled) {
        setDocumentPreviews(Object.fromEntries(entries));
      } else {
        objectUrls.forEach((url) => URL.revokeObjectURL(url));
      }
    }

    loadDocumentPreviews();

    return () => {
      cancelled = true;
      objectUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [data.pendingKycs, token]);

  const handleApprove = async (userId) => {
    if (!reviewedKycIds.has(userId)) {
      setError("Open and review the submitted PDF before approving this KYC request.");
      return;
    }

    setBusyAction(`approve-${userId}`);
    setError("");
    setNotice("");
    try {
      const response = await api.approveKyc(userId, token);
      setNotice(typeof response === "string" ? response : "KYC approved.");
      setReviewedKycIds((current) => {
        const next = new Set(current);
        next.delete(userId);
        return next;
      });
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const handleReject = async (userId) => {
    const reason = window.prompt("Enter the rejection reason for this KYC request:");
    if (reason === null) {
      return;
    }

    setBusyAction(`reject-${userId}`);
    setError("");
    setNotice("");
    try {
      const response = await api.rejectKyc(userId, reason, token);
      setNotice(typeof response === "string" ? response : "KYC rejected.");
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const createCampaign = async (event) => {
    event.preventDefault();
    setBusyAction("campaign");
    setError("");
    setNotice("");
    try {
      await api.createCampaign(
        {
          ...campaignForm,
          rewardPoints: Number(campaignForm.rewardPoints)
        },
        token
      );
      setNotice("Campaign created.");
      setCampaignForm(initialCampaign);
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const createCatalogItem = async (event) => {
    event.preventDefault();
    setBusyAction("catalog");
    setError("");
    setNotice("");
    try {
      await api.createRewardCatalog(
        {
          ...catalogForm,
          costInPoints: Number(catalogForm.costInPoints),
          stockQuantity: Number(catalogForm.stockQuantity)
        },
        token
      );
      setNotice("Reward item created.");
      setCatalogForm(initialCatalog);
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const submitProfileUpdate = async (event) => {
    event.preventDefault();
    setBusyAction("profile");
    setError("");
    setNotice("");
    try {
      const normalizedProfile = {
        fullName: profileForm.fullName.trim(),
        email: profileForm.email.trim(),
        username: profileForm.username.trim(),
        phoneNumber: profileForm.phoneNumber.trim(),
        profileImageUrl: profileForm.profileImageUrl
      };
      const updatedProfile = await api.updateProfile(
        user.userId,
        normalizedProfile,
        token
      );
      syncUser(updatedProfile);
      setNotice("Profile updated.");
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const handleProfileImageChange = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    if (!file.type.startsWith("image/")) {
      setError("Please choose a valid image file for the profile photo.");
      return;
    }

    try {
      setIsProfileImageProcessing(true);
      const optimizedImage = await optimizeProfileImage(file);
      setProfileForm((current) => ({
        ...current,
        profileImageUrl: optimizedImage || current.profileImageUrl
      }));
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setIsProfileImageProcessing(false);
      event.target.value = "";
    }
  };

  const submitPasswordChange = async (event) => {
    event.preventDefault();
    setBusyAction("password");
    setError("");
    setNotice("");
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setError("New password and confirm password do not match.");
      setBusyAction("");
      return;
    }
    try {
      const response = await api.changePassword(
        user.userId,
        {
          currentPassword: passwordForm.currentPassword,
          newPassword: passwordForm.newPassword
        },
        token
      );
      setNotice(typeof response === "string" ? response : "Password updated.");
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setIsPasswordEditing(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const cancelPasswordChange = () => {
    setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
    setIsPasswordEditing(false);
    setError("");
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center px-6 text-center text-mist/70">
        Loading your admin workspace...
      </div>
    );
  }

  return (
    <div className="app-shell min-h-screen" data-section={activeTab}>
      <aside className="workspace-sidebar admin-sidebar">
        <div>
          <div className="sidebar-brand">
            <span className="brand-mark">DW</span>
            <div>
              <p className="brand-name">Digital Wallet</p>
              <p className="brand-subtitle">Admin account</p>
            </div>
          </div>

          <div className="sidebar-person">
            <div className="sidebar-avatar">
              {profileForm.profileImageUrl ? (
                <img src={profileForm.profileImageUrl} alt="Profile" />
              ) : (
                buildInitials(profileForm.fullName || user.fullName)
              )}
            </div>
            <p className="mt-4 text-sm font-bold text-white">{user.fullName}</p>
            <p className="mt-1 text-xs text-white/60">{titleize(user.role)} account</p>
          </div>

          <nav className="mt-6 grid gap-2">
            {navItems.map(([key, label]) => (
              <button
                key={key}
                className={`nav-chip ${activeTab === key ? "nav-chip-active" : ""}`}
                onClick={() => setActiveTab(key)}
              >
                {label}
              </button>
            ))}
          </nav>
        </div>

        <div className="sidebar-actions">
          <ThemeSwitcher />
          <button className="secondary-btn w-full" onClick={refresh}>
            {refreshing ? "Refreshing..." : "Refresh Account"}
          </button>
          <button className="secondary-btn w-full" onClick={logout}>
            Sign Out
          </button>
        </div>
      </aside>

      <div className="workspace-content">
        <header className="workspace-hero admin-hero">
          <div>
            <p className="eyebrow">Admin overview</p>
            <h1>Admin dashboard</h1>
            <p>
              Review KYC requests, campaigns, reward catalog, notifications, and profile.
            </p>
          </div>
        </header>

        {error ? (
          <div className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-semibold text-rose-700">
            {error}
          </div>
        ) : null}
        {notice ? (
          <div className="mt-6 rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm font-semibold text-teal-800">
            {notice}
          </div>
        ) : null}

        <main className="connected-main">
          {activeTab === "overview" ? (
            <>
              <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
                <StatCard eyebrow="System" title="Status" value={data.dashboard?.status || "Active"} />
                <StatCard
                  eyebrow="Campaigns"
                  title="Active Campaigns"
                  value={String(data.dashboard?.activeCampaigns || data.campaigns.length || 0)}
                  accent="from-gold/30 to-coral/15"
                />
                <StatCard
                  eyebrow="KYC"
                  title="Pending KYC"
                  value={String(data.pendingKycs.length)}
                  accent="from-coral/25 to-aqua/12"
                />
                <StatCard
                  eyebrow="Catalog"
                  title="Reward Items"
                  value={String(data.rewardsCatalog.length)}
                  accent="from-aqua/25 to-gold/12"
                />
              </div>

              <div className="grid gap-6 xl:grid-cols-[1.05fr,0.95fr]">
                <SectionCard title="Admin summary" subtitle="KYC, campaigns, catalog, and notifications.">
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <p className="text-sm font-semibold text-white">Pending KYC</p>
                      <p className="theme-accent-text mt-3 text-4xl font-semibold">{data.pendingKycs.length}</p>
                      <p className="mt-2 text-sm text-mist/60">
                        Review submitted KYC documents.
                      </p>
                    </div>
                    <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <p className="text-sm font-semibold text-white">Notifications</p>
                      <p className="mt-3 text-4xl font-semibold text-gold">{data.notifications.length}</p>
                      <p className="mt-2 text-sm text-mist/60">
                        KYC notification records.
                      </p>
                    </div>
                  </div>
                </SectionCard>

                <SectionCard title="Admin actions" subtitle="Available admin tasks.">
                  <div className="space-y-3 text-sm text-mist/70">
                    <div className="rounded-[22px] border border-white/10 bg-white/[0.04] p-4">
                      Review pending KYC requests.
                    </div>
                    <div className="rounded-[22px] border border-white/10 bg-white/[0.04] p-4">
                      Create and review campaigns.
                    </div>
                    <div className="rounded-[22px] border border-white/10 bg-white/[0.04] p-4">
                      Create and review reward catalog items.
                    </div>
                  </div>
                </SectionCard>
              </div>
            </>
          ) : null}

          {activeTab === "kyc" ? (
            <SectionCard title="KYC queue" subtitle="Review submitted PDF documents.">
              {data.pendingKycs.length ? (
                <div className="grid gap-4">
                  {data.pendingKycs.map((item) => (
                    <div key={item.id || item.userId} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                        <div className="grid flex-1 gap-2 text-sm text-mist/70">
                          <p><span className="text-mist/50">User ID:</span> {item.userId}</p>
                          <p><span className="text-mist/50">Email:</span> {item.email || "Not available"}</p>
                          <p><span className="text-mist/50">Document:</span> {titleize(item.documentType)} / {item.documentNumber}</p>
                          <p><span className="text-mist/50">Submitted:</span> {formatDate(item.submittedAt)}</p>
                          {isPdfDocumentUrl(item.documentUrl) ? (
                            <>
                              {documentPreviews[item.userId] ? (
                                <div className="mt-3 overflow-hidden rounded-[18px] border border-white/10 bg-black/20">
                                  <iframe
                                    className="h-80 w-full"
                                    src={documentPreviews[item.userId]}
                                    title={`KYC PDF for ${item.userId}`}
                                  />
                                </div>
                              ) : (
                                <p className="mt-3 rounded-2xl border border-coral/30 bg-coral/10 px-4 py-3 text-sm text-orange-100">
                                  PDF preview is unavailable. Refresh and try again.
                                </p>
                              )}
                              <div className="mt-2 flex flex-wrap items-center gap-4">
                                {documentPreviews[item.userId] ? (
                                  <a className="text-aqua" href={documentPreviews[item.userId]} target="_blank" rel="noreferrer">
                                    Open PDF in new tab
                                  </a>
                                ) : null}
                                <label className="flex items-center gap-2 text-sm text-mist/70">
                                  <input
                                    type="checkbox"
                                    checked={reviewedKycIds.has(item.userId)}
                                    onChange={(event) =>
                                      setReviewedKycIds((current) => {
                                        const next = new Set(current);
                                        if (event.target.checked) {
                                          next.add(item.userId);
                                        } else {
                                          next.delete(item.userId);
                                        }
                                        return next;
                                      })
                                    }
                                  />
                                  PDF reviewed
                                </label>
                              </div>
                            </>
                          ) : (
                            <p className="rounded-2xl border border-coral/30 bg-coral/10 px-4 py-3 text-sm text-orange-100">
                              This submission is not a PDF. Reject it and ask the user to resubmit a PDF document.
                            </p>
                          )}
                        </div>
                        <div className="flex flex-wrap gap-3">
                          <button
                            className="primary-btn"
                            disabled={
                              busyAction === `approve-${item.userId}` ||
                              !isPdfDocumentUrl(item.documentUrl) ||
                              !reviewedKycIds.has(item.userId)
                            }
                            onClick={() => handleApprove(item.userId)}
                          >
                            {busyAction === `approve-${item.userId}` ? "Approving..." : "Approve"}
                          </button>
                          <button
                            className="secondary-btn"
                            disabled={busyAction === `reject-${item.userId}`}
                            onClick={() => handleReject(item.userId)}
                          >
                            {busyAction === `reject-${item.userId}` ? "Rejecting..." : "Reject"}
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState title="No pending KYC" body="Submitted KYC requests will appear here." />
              )}
            </SectionCard>
          ) : null}

          {activeTab === "campaigns" ? (
            <div className="grid gap-6 xl:grid-cols-[0.9fr,1.1fr]">
              <SectionCard title="Create campaign" subtitle="Add a reward campaign.">
                <form className="space-y-5" onSubmit={createCampaign}>
                  <div>
                    <label className="label">Campaign Name</label>
                    <input
                      className="field"
                      value={campaignForm.name}
                      onChange={(event) =>
                        setCampaignForm((current) => ({ ...current, name: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Target Tier</label>
                    <select
                      className="field"
                      value={campaignForm.targetTier}
                      onChange={(event) =>
                        setCampaignForm((current) => ({ ...current, targetTier: event.target.value }))
                      }
                    >
                      <option value="ALL">All</option>
                      <option value="SILVER">Silver</option>
                      <option value="GOLD">Gold</option>
                      <option value="PLATINUM">Platinum</option>
                    </select>
                  </div>
                  <div className="grid gap-5 md:grid-cols-2">
                    <div>
                      <label className="label">Reward Points</label>
                      <input
                        className="field"
                        type="number"
                        min="1"
                        value={campaignForm.rewardPoints}
                        onChange={(event) =>
                          setCampaignForm((current) => ({ ...current, rewardPoints: event.target.value }))
                        }
                        required
                      />
                    </div>
                    <div>
                      <label className="label">Trigger</label>
                      <select
                        className="field"
                        value={campaignForm.triggerEvent}
                        onChange={(event) =>
                          setCampaignForm((current) => ({ ...current, triggerEvent: event.target.value }))
                        }
                      >
                        <option value="FIRST_TRANSACTION">First Transaction</option>
                        <option value="EVERY_TRANSACTION">Every Transaction</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label className="label">Status</label>
                    <select
                      className="field"
                      value={campaignForm.status}
                      onChange={(event) =>
                        setCampaignForm((current) => ({ ...current, status: event.target.value }))
                      }
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="INACTIVE">Inactive</option>
                    </select>
                  </div>
                  <button className="primary-btn" disabled={busyAction === "campaign"}>
                    {busyAction === "campaign" ? "Publishing..." : "Create Campaign"}
                  </button>
                </form>
              </SectionCard>

              <SectionCard title="Campaigns" subtitle="Created campaigns.">
                {data.campaigns.length ? (
                  <div className="grid gap-4">
                    {data.campaigns.map((item) => (
                      <div key={item.id} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                          <div>
                            <h3 className="text-lg font-semibold text-white">{item.name}</h3>
                            <p className="mt-2 text-sm text-mist/65">
                              Target tier: {titleize(item.targetTier || "all")} | Status: {titleize(item.status)}
                            </p>
                            <p className="mt-1 text-sm text-mist/65">
                              Reward: {item.rewardPoints || 0} pts | Trigger: {titleize(item.triggerEvent || "first_transaction")}
                            </p>
                          </div>
                          <p className="text-sm text-mist/55">{formatDate(item.createdAt)}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState title="No campaigns" body="Created campaigns will appear here." />
                )}
              </SectionCard>
            </div>
          ) : null}

          {activeTab === "catalog" ? (
            <div className="grid gap-6 xl:grid-cols-[0.9fr,1.1fr]">
              <SectionCard title="Create reward item" subtitle="Add an item to the reward catalog.">
                <form className="grid gap-5" onSubmit={createCatalogItem}>
                  <div>
                    <label className="label">Name</label>
                    <input
                      className="field"
                      value={catalogForm.name}
                      onChange={(event) =>
                        setCatalogForm((current) => ({ ...current, name: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Description</label>
                    <textarea
                      className="field min-h-28"
                      value={catalogForm.description}
                      onChange={(event) =>
                        setCatalogForm((current) => ({ ...current, description: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div className="grid gap-5 md:grid-cols-2">
                    <div>
                      <label className="label">Cost In Points</label>
                      <input
                        className="field"
                        type="number"
                        min="1"
                        value={catalogForm.costInPoints}
                        onChange={(event) =>
                          setCatalogForm((current) => ({ ...current, costInPoints: event.target.value }))
                        }
                        required
                      />
                    </div>
                    <div>
                      <label className="label">Stock Quantity</label>
                      <input
                        className="field"
                        type="number"
                        min="0"
                        value={catalogForm.stockQuantity}
                        onChange={(event) =>
                          setCatalogForm((current) => ({ ...current, stockQuantity: event.target.value }))
                        }
                        required
                      />
                    </div>
                  </div>
                  <div>
                    <label className="label">Required Tier</label>
                    <select
                      className="field"
                      value={catalogForm.requiredTier}
                      onChange={(event) =>
                        setCatalogForm((current) => ({ ...current, requiredTier: event.target.value }))
                      }
                    >
                      <option value="ALL">All</option>
                      <option value="SILVER">Silver</option>
                      <option value="GOLD">Gold</option>
                      <option value="PLATINUM">Platinum</option>
                    </select>
                  </div>
                  <button className="primary-btn" disabled={busyAction === "catalog"}>
                    {busyAction === "catalog" ? "Creating..." : "Create Catalog Item"}
                  </button>
                </form>
              </SectionCard>

              <SectionCard title="Reward catalog" subtitle="Reward items.">
                {data.rewardsCatalog.length ? (
                  <div className="grid gap-4 md:grid-cols-2">
                    {data.rewardsCatalog.map((item) => (
                      <div key={item.id} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                        <h3 className="text-lg font-semibold text-white">{item.name}</h3>
                        <p className="mt-2 text-sm text-mist/65">{item.description}</p>
                        <div className="mt-4 flex flex-wrap gap-3 text-sm text-mist/60">
                          <span>{item.costInPoints} pts</span>
                          <span>Stock {item.stockQuantity}</span>
                          <span>{titleize(item.requiredTier)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState title="No reward items" body="Created reward items will appear here." />
                )}
              </SectionCard>
            </div>
          ) : null}

          {activeTab === "notifications" ? (
            <SectionCard title="Notifications" subtitle="KYC notification records.">
              {data.notifications.length ? (
                <div className="grid gap-4">
                  {data.notifications.map((item) => (
                    <div key={item.id} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                        <div>
                          <h3 className="text-lg font-semibold text-white">{item.topic}</h3>
                          <p className="mt-2 text-sm text-mist/65">{item.message}</p>
                          <p className="mt-3 text-sm text-mist/50">User: {item.userId || "System"}</p>
                        </div>
                        <div className="text-sm text-mist/55">
                          <p>{titleize(item.type)}</p>
                          <p className="mt-1">{formatDate(item.sentAt)}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState title="No notifications" body="KYC notification records will appear here." />
              )}
            </SectionCard>
          ) : null}

          {activeTab === "profile" ? (
            <div className="grid gap-6 xl:grid-cols-[1.05fr,0.95fr]">
              <SectionCard title="Admin profile" subtitle="Account details.">
                <form className="grid gap-5 md:grid-cols-2" onSubmit={submitProfileUpdate}>
                  <div className="md:col-span-2 rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                    <div className="flex flex-col gap-5 md:flex-row md:items-center">
                      <div className="flex h-24 w-24 items-center justify-center overflow-hidden rounded-full border border-white/10 bg-white/[0.05] text-2xl font-semibold text-white">
                        {profileForm.profileImageUrl ? (
                          <img
                            src={profileForm.profileImageUrl}
                            alt="Profile preview"
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          buildInitials(profileForm.fullName || user.fullName)
                        )}
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-semibold text-white">Profile photo</p>
                        <p className="mt-2 text-sm text-mist/60">
                          Upload profile photo.
                        </p>
                        <div className="mt-4 flex flex-wrap gap-3">
                          <label className="secondary-btn cursor-pointer">
                            Upload Photo
                            <input
                              className="hidden"
                              type="file"
                              accept="image/*"
                              onChange={handleProfileImageChange}
                            />
                          </label>
                          {profileForm.profileImageUrl ? (
                            <button
                              className="secondary-btn"
                              type="button"
                              onClick={() =>
                                setProfileForm((current) => ({ ...current, profileImageUrl: "" }))
                              }
                            >
                              Remove Photo
                            </button>
                          ) : null}
                        </div>
                      </div>
                    </div>
                  </div>
                  <div>
                    <label className="label">Full Name</label>
                    <input
                      className="field"
                      value={profileForm.fullName}
                      onChange={(event) => setProfileForm((current) => ({ ...current, fullName: event.target.value }))}
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Username</label>
                    <input
                      className="field"
                      value={profileForm.username}
                      onChange={(event) => setProfileForm((current) => ({ ...current, username: event.target.value }))}
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Email</label>
                    <input
                      className="field"
                      type="email"
                      value={profileForm.email}
                      onChange={(event) => setProfileForm((current) => ({ ...current, email: event.target.value }))}
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Phone Number</label>
                    <input
                      className="field"
                      value={profileForm.phoneNumber}
                      onChange={(event) => setProfileForm((current) => ({ ...current, phoneNumber: event.target.value }))}
                    />
                  </div>
                  <div className="md:col-span-2 grid gap-4">
                    {[
                      ["Role", titleize(data.profile?.role || user.role)]
                    ].map(([label, value]) => (
                      <div key={label} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                        <p className="text-xs uppercase tracking-[0.2em] text-mist/55">{label}</p>
                        <p className="mt-3 break-all text-base font-semibold text-white">{value}</p>
                      </div>
                    ))}
                  </div>
                  <button
                    className="primary-btn md:col-span-2"
                    disabled={busyAction === "profile" || isProfileImageProcessing}
                  >
                    {isProfileImageProcessing
                      ? "Preparing image..."
                      : busyAction === "profile"
                        ? "Saving..."
                        : "Save Profile"}
                  </button>
                </form>
              </SectionCard>

              <SectionCard
                title="Password"
                subtitle={isPasswordEditing ? "Enter current and new password." : "Change account password."}
                actions={
                  isPasswordEditing ? (
                    <button className="secondary-btn" type="button" onClick={cancelPasswordChange}>
                      Cancel
                    </button>
                  ) : (
                    <button className="primary-btn" type="button" onClick={() => setIsPasswordEditing(true)}>
                      Change Password
                    </button>
                  )
                }
              >
                {isPasswordEditing ? (
                  <form className="space-y-5" onSubmit={submitPasswordChange}>
                    <div>
                      <label className="label">Current Password</label>
                      <input
                        className="field"
                        type="password"
                        value={passwordForm.currentPassword}
                        onChange={(event) => setPasswordForm((current) => ({ ...current, currentPassword: event.target.value }))}
                        required
                      />
                    </div>
                    <div>
                      <label className="label">New Password</label>
                      <input
                        className="field"
                        type="password"
                        value={passwordForm.newPassword}
                        onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
                        required
                      />
                    </div>
                    <div>
                      <label className="label">Confirm New Password</label>
                      <input
                        className="field"
                        type="password"
                        value={passwordForm.confirmPassword}
                        onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                        required
                      />
                    </div>
                    <button className="primary-btn" disabled={busyAction === "password"}>
                      {busyAction === "password" ? "Updating..." : "Save Password"}
                    </button>
                  </form>
                ) : null}
              </SectionCard>
            </div>
          ) : null}
        </main>
      </div>
    </div>
  );
}
