import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";

export default function NavBar() {
  const { user, logout } = useAuth();
  const { count } = useCart();
  const navigate = useNavigate();

  if (!user) return null;

  return (
    <nav className="navbar">
      <Link to="/" className="brand">OptiQueue</Link>
      <div className="nav-links">
        <Link to="/">Products</Link>
        {user.role === "CUSTOMER" && (
          <>
            <Link to="/cart">Cart{count > 0 ? ` (${count})` : ""}</Link>
            <Link to="/orders">My Orders</Link>
          </>
        )}
        {(user.role === "ADMIN" || user.role === "STAFF") && (
          <Link to="/admin">Dashboard</Link>
        )}
      </div>
      <div className="nav-user">
        <span className="badge">{user.role}</span>
        <span>{user.username}</span>
        <button
          className="btn btn-ghost"
          onClick={() => {
            logout();
            navigate("/login");
          }}
        >
          Logout
        </button>
      </div>
    </nav>
  );
}
