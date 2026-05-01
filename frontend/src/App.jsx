import { AuthPage } from "./features/AuthPage";
import { AdminWorkspace } from "./features/AdminWorkspace";
import { UserWorkspace } from "./features/UserWorkspace";
import { useAuth } from "./context/AuthContext";

export default function App() {
  const { isAuthenticated, user, token, logout, syncUser } = useAuth();

  if (!isAuthenticated || !user) {
    return <AuthPage />;
  }

  if (String(user.role).toUpperCase() === "ADMIN") {
    return <AdminWorkspace token={token} user={user} logout={logout} syncUser={syncUser} />;
  }

  return <UserWorkspace token={token} user={user} logout={logout} syncUser={syncUser} />;
}
