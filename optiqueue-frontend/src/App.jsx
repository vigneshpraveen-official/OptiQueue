import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { CartProvider } from "./context/CartContext";
import NavBar from "./components/NavBar";
import ProtectedRoute from "./components/ProtectedRoute";
import Login from "./pages/Login";
import ProductList from "./pages/ProductList";
import Cart from "./pages/Cart";
import MyOrders from "./pages/MyOrders";
import AdminDashboard from "./pages/AdminDashboard";

export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <NavBar />
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={
              <ProtectedRoute><ProductList /></ProtectedRoute>
            } />
            <Route path="/cart" element={
              <ProtectedRoute roles={["CUSTOMER"]}><Cart /></ProtectedRoute>
            } />
            <Route path="/orders" element={
              <ProtectedRoute roles={["CUSTOMER"]}><MyOrders /></ProtectedRoute>
            } />
            <Route path="/admin" element={
              <ProtectedRoute roles={["ADMIN", "STAFF"]}><AdminDashboard /></ProtectedRoute>
            } />
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
}
