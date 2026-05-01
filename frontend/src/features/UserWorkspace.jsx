import { useEffect, useMemo, useRef, useState } from "react";
import { EmptyState } from "../components/EmptyState";
import { SectionCard } from "../components/SectionCard";
import { StatCard } from "../components/StatCard";
import { ThemeSwitcher } from "../components/ThemeSwitcher";
import { api } from "../lib/api";
import { formatCurrency, formatDate, formatUtcDate, titleize } from "../lib/format";
import { optimizeProfileImage } from "../lib/images";

const navItems = [
  ["overview", "Overview"],
  ["wallet", "Wallet"],
  ["transactions", "Transactions"],
  ["rewards", "Rewards"],
  ["kyc", "KYC"],
  ["profile", "Profile"],
  ["notifications", "Notifications"]
];

const initialKycForm = {
  documentType: "AADHAR",
  documentNumber: "",
  documentFile: null
};

const walletLockedTabs = new Set(["wallet", "transactions", "rewards"]);

function buildInitials(name) {
  return String(name || "User")
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");
}

export function UserWorkspace({ token, user, logout, syncUser }) {
  const [activeTab, setActiveTab] = useState("overview");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [data, setData] = useState({
    profile: null,
    walletBalance: 0,
    ledgerBalance: 0,
    rewardsSummary: null,
    transactions: [],
    rewardsCatalog: [],
    notifications: [],
    kycStatus: null
  });
  const [topupForm, setTopupForm] = useState({ amount: "", paymentMethod: "UPI" });
  const [transferForm, setTransferForm] = useState({ targetUserId: "", amount: "", notes: "" });
  const [kycForm, setKycForm] = useState(initialKycForm);
  const [kycPdfName, setKycPdfName] = useState("");
  const [profileForm, setProfileForm] = useState({
    fullName: "",
    email: "",
    username: "",
    phoneNumber: "",
    profileImageUrl: ""
  });
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [busyAction, setBusyAction] = useState("");
  const [isProfileImageProcessing, setIsProfileImageProcessing] = useState(false);
  const [isProfileEditing, setIsProfileEditing] = useState(false);
  const [isPasswordEditing, setIsPasswordEditing] = useState(false);
  const [rewardCelebration, setRewardCelebration] = useState(null);
  const loadSequenceRef = useRef(0);

  const swallowMissingKyc = async () => {
    try {
      return await api.getKycStatus(token);
    } catch (error) {
      if (String(error.message).toLowerCase().includes("kyc details not found")) {
        return null;
      }
      throw error;
    }
  };

  const loadWorkspace = async () => {
    const requestId = ++loadSequenceRef.current;
    setError("");
    setNotice("");
    const profileResult = await api.getProfile(user.userId, token);
    const notificationsResult = await api.getNotifications(token).catch(() => []);
    const kycStatus = await swallowMissingKyc();
    const walletAccessEnabled = String(kycStatus?.status || "").toUpperCase() === "APPROVED";

    let walletBalanceResponse = { balance: 0 };
    let ledgerBalanceResponse = { ledgerBalance: 0 };
    let rewardsSummary = { totalPoints: 0, tier: "STANDARD" };
    let transactionsResponse = { content: [] };
    let rewardsCatalog = [];
    let workspaceWarning = "";

    if (walletAccessEnabled) {
      const settled = await Promise.allSettled([
        api.getWalletBalance(token),
        api.getLedgerBalance(token),
        api.getRewardsSummary(token),
        api.getTransactions(token),
        api.getRewardsCatalog(token)
      ]);

      const [
        walletBalanceResult,
        ledgerBalanceResult,
        rewardsSummaryResult,
        transactionsResult,
        rewardsCatalogResult
      ] = settled;

      const hardFailure = [walletBalanceResult, ledgerBalanceResult, rewardsSummaryResult].find(
        (result) => result.status === "rejected"
      );

      if (hardFailure?.reason) {
        workspaceWarning = hardFailure.reason.message || "Some wallet details are unavailable.";
      }

      walletBalanceResponse =
        walletBalanceResult.status === "fulfilled" ? walletBalanceResult.value : walletBalanceResponse;
      ledgerBalanceResponse =
        ledgerBalanceResult.status === "fulfilled" ? ledgerBalanceResult.value : ledgerBalanceResponse;
      rewardsSummary =
        rewardsSummaryResult.status === "fulfilled" ? rewardsSummaryResult.value : rewardsSummary;
      transactionsResponse =
        transactionsResult.status === "fulfilled" ? transactionsResult.value : transactionsResponse;
      rewardsCatalog =
        rewardsCatalogResult.status === "fulfilled" ? rewardsCatalogResult.value : rewardsCatalog;
    }

    if (requestId !== loadSequenceRef.current) {
      return;
    }

    setData({
      profile: profileResult,
      walletBalance: walletBalanceResponse?.balance ?? 0,
      ledgerBalance: ledgerBalanceResponse?.ledgerBalance ?? 0,
      rewardsSummary,
      transactions: Array.isArray(transactionsResponse?.content)
        ? transactionsResponse.content
        : Array.isArray(transactionsResponse)
          ? transactionsResponse
          : [],
      rewardsCatalog,
      notifications: Array.isArray(notificationsResult)
        ? notificationsResult.filter((item) => !item.userId || item.userId === user.userId)
        : [],
      kycStatus
    });
    setProfileForm({
      fullName: profileResult?.fullName || "",
      email: profileResult?.email || "",
      username: profileResult?.username || "",
      phoneNumber: profileResult?.phoneNumber || "",
      profileImageUrl: profileResult?.profileImageUrl || ""
    });
    if (workspaceWarning) {
      setNotice(workspaceWarning);
    }
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

  const transactions = useMemo(() => data.transactions || [], [data.transactions]);
  const notifications = useMemo(() => data.notifications || [], [data.notifications]);
  const rewardsCatalog = useMemo(() => data.rewardsCatalog || [], [data.rewardsCatalog]);
  const sortedRewardsCatalog = useMemo(() => {
    const rewardOrder = ["amazon", "flipkart", "cashback"];
    return [...rewardsCatalog].sort((first, second) => {
      const firstName = String(first.name || "").toLowerCase();
      const secondName = String(second.name || "").toLowerCase();
      const firstIndex = rewardOrder.findIndex((name) => firstName.includes(name));
      const secondIndex = rewardOrder.findIndex((name) => secondName.includes(name));
      const normalizedFirst = firstIndex === -1 ? rewardOrder.length : firstIndex;
      const normalizedSecond = secondIndex === -1 ? rewardOrder.length : secondIndex;

      if (normalizedFirst !== normalizedSecond) {
        return normalizedFirst - normalizedSecond;
      }

      return firstName.localeCompare(secondName);
    });
  }, [rewardsCatalog]);
  const kycStatusValue = String(data.kycStatus?.status || "").toUpperCase();
  const walletAccessEnabled = kycStatusValue === "APPROVED";
  const canSubmitKyc = !data.kycStatus || kycStatusValue === "REJECTED";
  const kycStatusMessage =
    kycStatusValue === "APPROVED"
      ? "KYC approved. Wallet features are active."
      : kycStatusValue === "PENDING"
        ? "KYC submitted. Waiting for approval."
        : kycStatusValue === "REJECTED"
          ? data.kycStatus?.rejectionReason
            ? `Rejected: ${data.kycStatus.rejectionReason}`
            : "KYC rejected. Submit a new PDF to continue."
          : "Submit KYC to start verification.";
  const walletGateMessage =
    kycStatusValue === "PENDING"
      ? "Your KYC is pending. Wallet, transactions, and rewards unlock after approval."
      : kycStatusValue === "REJECTED"
        ? "Your KYC was rejected. Submit a new PDF to continue."
        : "Submit KYC to unlock wallet, transactions, and rewards.";
  const profileSnapshotItems = [
    {
      label: "Username",
      value: data.profile?.username || user.username || "Not set"
    },
    {
      label: "Email",
      value: data.profile?.email || user.email || "Not set"
    },
    {
      label: "Phone",
      value: data.profile?.phoneNumber || user.phoneNumber || "Not added"
    },
    {
      label: "Joined",
      value: data.profile?.createdAt ? formatUtcDate(data.profile.createdAt) : "Recently"
    }
  ];

  useEffect(() => {
    if (!walletAccessEnabled && walletLockedTabs.has(activeTab)) {
      setActiveTab("kyc");
    }
  }, [activeTab, walletAccessEnabled]);

  useEffect(() => {
    if (!canSubmitKyc) {
      setKycForm(initialKycForm);
      setKycPdfName("");
      setError((current) =>
        String(current || "").toLowerCase().includes("kyc is already approved") ? "" : current
      );
    }
  }, [canSubmitKyc]);

  const navigateToTab = (key) => {
    setNotice("");
    if (!walletAccessEnabled && walletLockedTabs.has(key)) {
      setNotice(walletGateMessage);
      setError("");
      setActiveTab("kyc");
      return;
    }
    setActiveTab(key);
  };

  const submitTopUp = async (event) => {
    event.preventDefault();
    setBusyAction("topup");
    setError("");
    setNotice("");
    try {
      await api.topUpWallet(
        {
          userId: user.userId,
          amount: Number(topupForm.amount),
          paymentMethod: topupForm.paymentMethod
        },
        token
      );
      setNotice("Wallet topped up.");
      setTopupForm({ amount: "", paymentMethod: "UPI" });
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const submitTransfer = async (event) => {
    event.preventDefault();
    setBusyAction("transfer");
    setError("");
    setNotice("");
    try {
      await api.transferFunds(
        {
          targetUserId: transferForm.targetUserId,
          amount: Number(transferForm.amount),
          notes: transferForm.notes
        },
        token
      );
      setNotice("Transfer completed.");
      setTransferForm({ targetUserId: "", amount: "", notes: "" });
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const submitKyc = async (event) => {
    event.preventDefault();
    setBusyAction("kyc");
    setError("");
    setNotice("");
    if (!canSubmitKyc) {
      setBusyAction("");
      return;
    }
    if (!kycForm.documentFile) {
      setError("Please choose a PDF file before submitting KYC.");
      setBusyAction("");
      return;
    }
    try {
      await api.submitKyc(kycForm, token);
      setNotice("KYC submitted.");
      setKycForm(initialKycForm);
      setKycPdfName("");
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const handleKycPdfChange = async (event) => {
    const file = event.target.files?.[0];
    setError("");

    if (!file) {
      setKycPdfName("");
      setKycForm((current) => ({ ...current, documentFile: null }));
      return;
    }

    const isPdfFile = file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
    if (!isPdfFile) {
      setError("Only PDF files are accepted for KYC.");
      setKycPdfName("");
      setKycForm((current) => ({ ...current, documentFile: null }));
      event.target.value = "";
      return;
    }

    setKycPdfName(file.name);
    setKycForm((current) => ({ ...current, documentFile: file }));
  };

  const submitProfileUpdate = async (event) => {
    event.preventDefault();
    if (!isProfileEditing) {
      return;
    }
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
      setIsProfileEditing(false);
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  const handleProfileImageChange = async (event) => {
    if (!isProfileEditing) {
      event.target.value = "";
      return;
    }
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

  const cancelProfileEdit = () => {
    setProfileForm({
      fullName: data.profile?.fullName || "",
      email: data.profile?.email || "",
      username: data.profile?.username || "",
      phoneNumber: data.profile?.phoneNumber || "",
      profileImageUrl: data.profile?.profileImageUrl || ""
    });
    setIsProfileEditing(false);
    setError("");
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

  const buildRewardCelebration = (item) => {
    const rewardName = String(item?.name || "");
    const lowerName = rewardName.toLowerCase();

    if (lowerName.includes("amazon")) {
      return {
        title: "Amazon Voucher Unlocked",
        body: "You got an Amazon voucher. Hurray!",
        accent: "from-orange-400 to-amber-300"
      };
    }

    if (lowerName.includes("flipkart")) {
      return {
        title: "Flipkart Voucher Unlocked",
        body: "You got a Flipkart voucher. Hurray!",
        accent: "from-sky-400 to-yellow-300"
      };
    }

    if (lowerName.includes("myntra")) {
      return {
        title: "Myntra Voucher Unlocked",
        body: "You got a Myntra voucher. Hurray!",
        accent: "from-pink-500 to-orange-300"
      };
    }

    if (lowerName.includes("voucher") || lowerName.includes("gift card")) {
      return {
        title: `${rewardName || "Voucher"} Unlocked`,
        body: `You got ${rewardName || "a voucher"}. Hurray!`,
        accent: "from-fuchsia-500 to-emerald-300"
      };
    }

    return {
      title: `${rewardName || "Reward"} Unlocked`,
      body: `You redeemed ${rewardName || "a reward"}. Hurray!`,
      accent: "from-violet-500 to-cyan-300"
    };
  };

  const redeemReward = async (item) => {
    setBusyAction(`reward-${item.id}`);
    setError("");
    setNotice("");
    setRewardCelebration(null);
    try {
      await api.redeemReward(item.id, token);
      const celebration = buildRewardCelebration(item);
      if (celebration) {
        setRewardCelebration(celebration);
        window.setTimeout(() => setRewardCelebration(null), 5200);
      }
      await refresh();
      if (String(item.name || "").toLowerCase().includes("cashback")) {
        setNotice("Cashback redeemed and credited to your wallet.");
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyAction("");
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center px-6 text-center text-mist/70">
        Loading your wallet workspace...
      </div>
    );
  }

  return (
    <div className="app-shell min-h-screen" data-section={activeTab}>
      {rewardCelebration ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-6 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-[28px] border border-white/30 bg-white text-center shadow-2xl">
            <div className={`h-3 bg-gradient-to-r ${rewardCelebration.accent}`} />
            <div className="p-8">
              <p className="text-xs font-black uppercase tracking-[0.28em] text-slate-500">
                Reward Redeemed
              </p>
              <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950">
                {rewardCelebration.title}
              </h2>
              <p className="mt-3 text-base font-semibold text-slate-600">
                {rewardCelebration.body}
              </p>
              <button
                className="primary-btn mt-7"
                type="button"
                onClick={() => setRewardCelebration(null)}
              >
                Awesome
              </button>
            </div>
          </div>
        </div>
      ) : null}
      <aside className="workspace-sidebar">
        <div>
          <div className="sidebar-brand">
            <span className="brand-mark">DW</span>
            <div>
              <p className="brand-name">Digital Wallet</p>
              <p className="brand-subtitle">Personal account</p>
            </div>
          </div>

          <div className="sidebar-person">
            <div className="sidebar-avatar">
              {profileForm.profileImageUrl ? (
                <img src={profileForm.profileImageUrl} alt="Profile" />
              ) : (
                buildInitials(data.profile?.fullName || user.fullName)
              )}
            </div>
            <p className="mt-4 text-sm font-bold text-white">{data.profile?.fullName || user.fullName}</p>
            <p className="mt-1 break-all text-xs text-white/60">{data.profile?.email || user.username}</p>
          </div>

          <nav className="mt-6 grid gap-2">
            {navItems.map(([key, label]) => (
              <button
                key={key}
                className={`nav-chip ${activeTab === key ? "nav-chip-active" : ""} ${
                  !walletAccessEnabled && walletLockedTabs.has(key) ? "opacity-60" : ""
                }`}
                onClick={() => navigateToTab(key)}
              >
                {label}
                {!walletAccessEnabled && walletLockedTabs.has(key) ? " locked" : ""}
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
        <header className="workspace-hero customer-hero">
          <div>
            <p className="eyebrow">Account overview</p>
            <h1>Welcome back, {data.profile?.fullName || user.fullName}</h1>
            <p>
              Check wallet balance, transactions, rewards, KYC, profile, and notifications.
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
                <StatCard
                  eyebrow="Wallet"
                  title="Available Balance"
                  value={walletAccessEnabled ? formatCurrency(data.walletBalance) : "Locked"}
                />
                <StatCard
                  eyebrow="Ledger"
                  title="Ledger Balance"
                  value={walletAccessEnabled ? formatCurrency(data.ledgerBalance) : "Locked"}
                  accent="from-gold/30 to-aqua/12"
                />
                <StatCard
                  eyebrow="Loyalty"
                  title="Reward Points"
                  value={walletAccessEnabled ? String(data.rewardsSummary?.totalPoints || 0) : "Locked"}
                  accent="from-coral/25 to-gold/15"
                />
                <StatCard
                  eyebrow="KYC"
                  title="Verification Status"
                  value={titleize(data.kycStatus?.status || data.profile?.status || "pending")}
                  accent="from-aqua/25 to-aqua/10"
                />
              </div>

              <div className="grid gap-6 xl:grid-cols-[1.4fr,1fr]">
                <SectionCard
                  title="Wallet summary"
                  subtitle="Balance, rewards, and transaction count."
                >
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <p className="text-sm font-semibold text-white">Rewards tier</p>
                      <p className="theme-accent-text mt-3 text-4xl font-semibold tracking-tight">
                        {walletAccessEnabled ? titleize(data.rewardsSummary?.tier || "standard") : "KYC First"}
                      </p>
                      <p className="mt-2 text-sm text-mist/60">
                        {walletAccessEnabled
                          ? "Tier is based on wallet and reward activity."
                          : "Submit KYC to enable wallet and rewards."}
                      </p>
                    </div>
                    <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <p className="text-sm font-semibold text-white">Transactions</p>
                      <p className="mt-3 text-4xl font-semibold tracking-tight text-gold">
                        {walletAccessEnabled ? transactions.length : "Locked"}
                      </p>
                      <p className="mt-2 text-sm text-mist/60">
                        {!walletAccessEnabled
                          ? "Submit KYC to view transactions."
                          : transactions.length
                          ? "Recent wallet transactions are listed in Transactions."
                          : "Top up or transfer funds to create transactions."}
                      </p>
                    </div>
                  </div>
                </SectionCard>

                <SectionCard title="Profile snapshot" subtitle="Personal account details.">
                  <div className="divide-y divide-slate-200 text-sm">
                    {profileSnapshotItems.map((item) => (
                      <div
                        className="flex flex-col gap-1 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
                        key={item.label}
                      >
                        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                          {item.label}
                        </p>
                        <p className="break-all text-sm font-semibold text-slate-950 sm:text-right">
                          {item.value}
                        </p>
                      </div>
                    ))}
                  </div>
                </SectionCard>
              </div>
            </>
          ) : null}

          {activeTab === "wallet" ? (
            walletAccessEnabled ? (
            <div className="grid gap-6 xl:grid-cols-2">
              <SectionCard title="Add money" subtitle="Add balance to your wallet.">
                <form className="space-y-5" onSubmit={submitTopUp}>
                  <div>
                    <label className="label">Amount</label>
                    <input
                      className="field"
                      type="number"
                      min="1"
                      step="0.01"
                      value={topupForm.amount}
                      onChange={(event) =>
                        setTopupForm((current) => ({ ...current, amount: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Payment Method</label>
                    <select
                      className="field"
                      value={topupForm.paymentMethod}
                      onChange={(event) =>
                        setTopupForm((current) => ({ ...current, paymentMethod: event.target.value }))
                      }
                    >
                      <option value="UPI">UPI</option>
                      <option value="CARD">Card</option>
                      <option value="NET_BANKING">Net Banking</option>
                    </select>
                  </div>
                  <button className="primary-btn" disabled={busyAction === "topup"}>
                    {busyAction === "topup" ? "Processing..." : "Top Up Wallet"}
                  </button>
                </form>
              </SectionCard>

              <SectionCard title="Transfer funds" subtitle="Send money to another user ID.">
                <form className="space-y-5" onSubmit={submitTransfer}>
                  <div>
                    <label className="label">Recipient User ID</label>
                    <input
                      className="field"
                      value={transferForm.targetUserId}
                      onChange={(event) =>
                        setTransferForm((current) => ({ ...current, targetUserId: event.target.value }))
                      }
                      placeholder="Recipient user ID"
                      required
                    />
                    <p className="mt-2 text-xs text-mist/55">
                      Enter the recipient user ID.
                    </p>
                  </div>
                  <div>
                    <label className="label">Amount</label>
                    <input
                      className="field"
                      type="number"
                      min="1"
                      step="0.01"
                      value={transferForm.amount}
                      onChange={(event) =>
                        setTransferForm((current) => ({ ...current, amount: event.target.value }))
                      }
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Notes</label>
                    <textarea
                      className="field min-h-28"
                      value={transferForm.notes}
                      onChange={(event) =>
                        setTransferForm((current) => ({ ...current, notes: event.target.value }))
                      }
                      placeholder="Transfer note"
                    />
                  </div>
                  <button className="primary-btn" disabled={busyAction === "transfer"}>
                    {busyAction === "transfer" ? "Transferring..." : "Send Funds"}
                  </button>
                </form>
              </SectionCard>
            </div>
            ) : (
              <SectionCard title="Wallet locked" subtitle="Submit KYC to use wallet features.">
                <EmptyState
                  title="KYC required"
                  body={walletGateMessage}
                />
              </SectionCard>
            )
          ) : null}

          {activeTab === "transactions" ? (
            walletAccessEnabled ? (
            <SectionCard title="Transactions" subtitle="Wallet top-ups and transfers.">
              {transactions.length ? (
                <div className="overflow-hidden rounded-[24px] border border-white/10">
                  <div className="grid grid-cols-5 gap-4 border-b border-white/10 bg-white/[0.04] px-4 py-3 text-xs uppercase tracking-[0.2em] text-mist/55">
                    <span>Type</span>
                    <span>Amount</span>
                    <span>Status</span>
                    <span>Timestamp</span>
                    <span>Notes</span>
                  </div>
                  {transactions.map((item) => (
                    <div
                      key={item.id}
                      className="grid grid-cols-1 gap-3 border-b border-white/5 px-4 py-4 text-sm text-mist/80 md:grid-cols-5"
                    >
                      <span className="font-semibold text-white">{titleize(item.type)}</span>
                      <span>{formatCurrency(item.amount)}</span>
                      <span>{titleize(item.status)}</span>
                      <span>{formatUtcDate(item.timestamp)}</span>
                      <span>{item.referenceNotes || "-"}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState title="No transactions" body="Top-ups and transfers will appear here." />
              )}
            </SectionCard>
            ) : (
              <SectionCard title="Transactions locked" subtitle="Submit KYC to view transactions.">
                <EmptyState title="KYC required" body={walletGateMessage} />
              </SectionCard>
            )
          ) : null}

          {activeTab === "rewards" ? (
            walletAccessEnabled ? (
            <div className="grid gap-6 xl:grid-cols-[0.9fr,1.1fr]">
              <SectionCard title="Reward points" subtitle="Points and tier.">
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                    <p className="text-sm text-mist/60">Available points</p>
                    <p className="theme-accent-text mt-3 text-4xl font-semibold">
                      {data.rewardsSummary?.totalPoints || 0}
                    </p>
                  </div>
                  <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                    <p className="text-sm text-mist/60">Current tier</p>
                    <p className="mt-3 text-4xl font-semibold text-gold">
                      {titleize(data.rewardsSummary?.tier || "standard")}
                    </p>
                  </div>
                </div>
              </SectionCard>

              <SectionCard title="Rewards catalog" subtitle="Redeem rewards using points.">
                {sortedRewardsCatalog.length ? (
                  <div className="grid gap-4 md:grid-cols-2">
                    {sortedRewardsCatalog.map((item) => (
                      <div key={item.id} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                        <div className="flex items-start justify-between gap-4">
                          <div>
                            <h3 className="text-lg font-semibold text-white">{item.name}</h3>
                            <p className="mt-2 text-sm text-mist/65">{item.description}</p>
                          </div>
                          <span className="rounded-full border border-gold/30 bg-gold/10 px-3 py-1 text-xs text-gold">
                            {item.costInPoints} pts
                          </span>
                        </div>
                        <div className="mt-5 flex flex-wrap items-center gap-3 text-sm text-mist/65">
                          <span>Tier: {titleize(item.requiredTier || "all")}</span>
                          <span>Stock: {item.stockQuantity}</span>
                        </div>
                        <button
                          className="primary-btn mt-6"
                          disabled={busyAction === `reward-${item.id}`}
                          onClick={() => redeemReward(item)}
                        >
                          {busyAction === `reward-${item.id}` ? "Redeeming..." : "Redeem"}
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState title="No rewards" body="Available rewards will appear here." />
                )}
              </SectionCard>
            </div>
            ) : (
              <SectionCard title="Rewards locked" subtitle="Submit KYC to use rewards.">
                <EmptyState title="KYC required" body={walletGateMessage} />
              </SectionCard>
            )
          ) : null}

          {activeTab === "kyc" ? (
            <div className={`grid gap-6 ${canSubmitKyc ? "xl:grid-cols-[0.9fr,1.1fr]" : ""}`}>
              <SectionCard title="KYC status" subtitle="Verification status.">
                <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                  <p className="text-sm text-mist/60">Verification status</p>
                  <p className="mt-3 text-4xl font-semibold text-white">
                    {titleize(data.kycStatus?.status || "not_submitted")}
                  </p>
                  <p className="mt-3 text-sm text-mist/65">
                    {kycStatusMessage}
                  </p>
                </div>
              </SectionCard>

              {canSubmitKyc ? (
                <SectionCard
                  title={kycStatusValue === "REJECTED" ? "Resubmit KYC" : "Submit KYC"}
                  subtitle="Upload a PDF document."
                >
                  <form className="grid gap-5 md:grid-cols-2" onSubmit={submitKyc}>
                    <div>
                      <label className="label">Document Type</label>
                      <select
                        className="field"
                        value={kycForm.documentType}
                        onChange={(event) =>
                          setKycForm((current) => ({ ...current, documentType: event.target.value }))
                        }
                      >
                        <option value="AADHAR">Aadhar</option>
                        <option value="PAN">PAN</option>
                        <option value="PASSPORT">Passport</option>
                      </select>
                    </div>
                    <div>
                      <label className="label">Document Number</label>
                      <input
                        className="field"
                        value={kycForm.documentNumber}
                        onChange={(event) =>
                          setKycForm((current) => ({ ...current, documentNumber: event.target.value }))
                        }
                        required
                      />
                    </div>
                    <div className="md:col-span-2">
                      <label className="label">PDF Document</label>
                      <label className="file-upload">
                        <input
                          className="file-upload-input"
                          type="file"
                          accept="application/pdf,.pdf"
                          onChange={handleKycPdfChange}
                          required
                        />
                        <span className="file-upload-icon" aria-hidden="true">
                          PDF
                        </span>
                        <span className="file-upload-title">
                          {kycPdfName || "Choose PDF file"}
                        </span>
                        <span className="file-upload-meta">
                          Aadhar, PAN, or passport PDF only.
                        </span>
                      </label>
                    </div>
                    <button className="primary-btn md:col-span-2" disabled={busyAction === "kyc"}>
                      {busyAction === "kyc" ? "Submitting..." : "Submit KYC"}
                    </button>
                  </form>
                </SectionCard>
              ) : null}
            </div>
          ) : null}

          {activeTab === "profile" ? (
            <div className="grid gap-6 xl:grid-cols-[1.05fr,0.95fr]">
              <SectionCard
                title="Profile details"
                subtitle={isProfileEditing ? "Edit account details." : "Account details."}
                actions={
                  isProfileEditing ? (
                    <button className="secondary-btn" type="button" onClick={cancelProfileEdit}>
                      Cancel
                    </button>
                  ) : (
                    <button className="primary-btn" type="button" onClick={() => setIsProfileEditing(true)}>
                      Edit Profile
                    </button>
                  )
                }
              >
                <form className="grid gap-5 md:grid-cols-2" onSubmit={submitProfileUpdate}>
                  <div className="md:col-span-2 rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                    <div className="flex flex-col gap-5 md:flex-row md:items-center">
                      <div className="group relative flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-full border border-white/10 bg-white/[0.05] text-2xl font-semibold text-white">
                        {profileForm.profileImageUrl ? (
                          <img
                            src={profileForm.profileImageUrl}
                            alt="Profile preview"
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          buildInitials(profileForm.fullName || user.fullName)
                        )}
                        {isProfileEditing ? (
                          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1 bg-black/65 px-2 text-center opacity-0 transition group-hover:opacity-100">
                            <label className="cursor-pointer text-xs font-semibold text-aqua">
                              {profileForm.profileImageUrl ? "Change" : "Upload"}
                              <input
                                className="hidden"
                                type="file"
                                accept="image/*"
                                onChange={handleProfileImageChange}
                              />
                            </label>
                            {profileForm.profileImageUrl ? (
                              <button
                                className="text-xs font-semibold text-orange-100"
                                type="button"
                                onClick={() =>
                                  setProfileForm((current) => ({ ...current, profileImageUrl: "" }))
                                }
                              >
                                Remove
                              </button>
                            ) : null}
                          </div>
                        ) : null}
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-semibold text-white">Profile photo</p>
                        {isProfileEditing ? (
                          <p className="mt-2 text-sm text-mist/60">Update profile photo.</p>
                        ) : null}
                      </div>
                    </div>
                  </div>
                  <div>
                    <label className="label">Full Name</label>
                    <input
                      className={`field ${!isProfileEditing ? "profile-readonly-field cursor-default" : ""}`}
                      value={profileForm.fullName}
                      onChange={(event) => setProfileForm((current) => ({ ...current, fullName: event.target.value }))}
                      readOnly={!isProfileEditing}
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Username</label>
                    <input
                      className={`field ${!isProfileEditing ? "profile-readonly-field cursor-default" : ""}`}
                      value={profileForm.username}
                      onChange={(event) => setProfileForm((current) => ({ ...current, username: event.target.value }))}
                      readOnly={!isProfileEditing}
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Email</label>
                    <input
                      className={`field ${!isProfileEditing ? "profile-readonly-field cursor-default" : ""}`}
                      type="email"
                      value={profileForm.email}
                      onChange={(event) => setProfileForm((current) => ({ ...current, email: event.target.value }))}
                      readOnly={!isProfileEditing}
                      required
                    />
                  </div>
                  <div>
                    <label className="label">Phone Number</label>
                    <input
                      className={`field ${!isProfileEditing ? "profile-readonly-field cursor-default" : ""}`}
                      value={profileForm.phoneNumber}
                      onChange={(event) => setProfileForm((current) => ({ ...current, phoneNumber: event.target.value }))}
                      readOnly={!isProfileEditing}
                    />
                  </div>
                  <div className="md:col-span-2 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    {[
                      ["Role", titleize(data.profile?.role || user.role)],
                      ["Status", titleize(data.profile?.status || "active")],
                      ["User ID", data.profile?.id || user.userId]
                    ].map(([label, value]) => (
                      <div key={label} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                        <p className="text-xs uppercase tracking-[0.2em] text-mist/55">{label}</p>
                        <p className="mt-3 break-all text-base font-semibold text-white">{value}</p>
                      </div>
                    ))}
                  </div>
                  {isProfileEditing ? (
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
                  ) : null}
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

          {activeTab === "notifications" ? (
            <SectionCard title="Notifications" subtitle="Account updates.">
              {notifications.length ? (
                <div className="grid gap-4">
                  {notifications.map((item) => (
                    <div key={item.id} className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
                      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                        <div>
                          <h3 className="text-lg font-semibold text-white">{titleize(item.topic || "account update")}</h3>
                          <p className="mt-2 text-sm text-mist/65">{item.message}</p>
                        </div>
                        <div className="text-sm text-mist/55">
                          <p>{titleize(item.topic || "account update")}</p>
                          <p className="mt-1">{formatDate(item.sentAt)}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState title="No notifications" body="KYC, wallet, and rewards updates will appear here." />
              )}
            </SectionCard>
          ) : null}
        </main>
      </div>
    </div>
  );
}
