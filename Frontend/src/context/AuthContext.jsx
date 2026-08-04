import { createContext, useContext, useEffect, useState } from "react";
import client, { unwrap } from "../api/client";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem("pukar_user");
    return raw ? JSON.parse(raw) : null;
  });
  const [loading, setLoading] = useState(false);

  const login = async (username, password) => {
    setLoading(true);
    try {
      const data = await unwrap(
        client.post("/auth/login", { username, password }),
      );
      localStorage.setItem("pukar_access", data.accessToken);
      localStorage.setItem("pukar_refresh", data.refreshToken);
      const u = {
        username: data.username,
        fullName: data.fullName,
        role: data.role,
        departmentId: data.departmentId,
        departmentName: data.departmentName,
      };
      localStorage.setItem("pukar_user", JSON.stringify(u));
      setUser(u);
      return u;
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem("pukar_access");
    localStorage.removeItem("pukar_refresh");
    localStorage.removeItem("pukar_user");
    setUser(null);
  };

  useEffect(() => {}, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
