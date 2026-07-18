import { useCallback, useEffect, useState } from "react";
import client from "../api/client";
import { useAuth } from "../context/AuthContext";

const NEXT_STATUSES = {
  PENDING: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["SHIPPED", "CANCELLED"],
  SHIPPED: [],
  CANCELLED: [],
};

export default function AdminDashboard() {
  const { user } = useAuth();
  const isAdmin = user.role === "ADMIN";

  const [products, setProducts] = useState(null);
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ sku: "", name: "", price: "", stockQuantity: "" });
  const [restockQty, setRestockQty] = useState({});

  const loadAll = useCallback(() => {
    client
      .get("/api/products", { params: { size: 100 } })
      .then((res) => setProducts(res.data))
      .catch(() => setError("Could not load products"));
    client
      .get("/api/orders", { params: { size: 50, sort: "id,desc" } })
      .then((res) => setOrders(res.data))
      .catch(() => setError("Could not load orders"));
  }, []);

  useEffect(loadAll, [loadAll]);

  const handle = (promise) =>
    promise
      .then(() => {
        setError("");
        loadAll();
      })
      .catch((err) => setError(err.response?.data?.message || "Action failed"));

  const createProduct = (e) => {
    e.preventDefault();
    handle(
      client
        .post("/api/products", {
          sku: form.sku,
          name: form.name,
          price: parseFloat(form.price),
          stockQuantity: parseInt(form.stockQuantity, 10),
        })
        .then(() => setForm({ sku: "", name: "", price: "", stockQuantity: "" }))
    );
  };

  const restock = (id) => {
    const qty = parseInt(restockQty[id], 10);
    if (!qty || qty < 1) return;
    handle(
      client.put(`/api/products/${id}/restock`, { quantity: qty }).then(() =>
        setRestockQty((prev) => ({ ...prev, [id]: "" }))
      )
    );
  };

  const setStatus = (orderId, status) =>
    handle(client.patch(`/api/orders/${orderId}/status`, { status }));

  return (
    <div className="page-pad">
      <h2>Dashboard</h2>
      {error && <div className="error">{error}</div>}

      {isAdmin && (
        <div className="card section">
          <h3>New product</h3>
          <form className="inline-form" onSubmit={createProduct}>
            <input placeholder="SKU" value={form.sku} required
              onChange={(e) => setForm({ ...form, sku: e.target.value })} />
            <input placeholder="Name" value={form.name} required
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <input placeholder="Price" type="number" step="0.01" min="0.01" value={form.price} required
              onChange={(e) => setForm({ ...form, price: e.target.value })} />
            <input placeholder="Stock" type="number" min="0" value={form.stockQuantity} required
              onChange={(e) => setForm({ ...form, stockQuantity: e.target.value })} />
            <button className="btn btn-primary">Create</button>
          </form>
        </div>
      )}

      <div className="card section">
        <h3>Inventory</h3>
        {!products ? (
          <p className="muted">Loading…</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>ID</th><th>SKU</th><th>Name</th><th>Price</th><th>Stock</th><th>Restock</th></tr>
            </thead>
            <tbody>
              {products.content.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td>
                  <td className="sku">{p.sku}</td>
                  <td>{p.name}</td>
                  <td>₹{Number(p.price).toFixed(2)}</td>
                  <td>{p.stockQuantity}</td>
                  <td>
                    <div className="qty">
                      <input type="number" min="1" placeholder="qty" className="qty-input"
                        value={restockQty[p.id] || ""}
                        onChange={(e) => setRestockQty({ ...restockQty, [p.id]: e.target.value })} />
                      <button className="btn" onClick={() => restock(p.id)}>Add</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card section">
        <h3>Orders</h3>
        {!orders ? (
          <p className="muted">Loading…</p>
        ) : orders.content.length === 0 ? (
          <p className="muted">No orders yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Order #</th><th>Status</th><th>Total</th><th>Change status</th></tr>
            </thead>
            <tbody>
              {orders.content.map((o) => (
                <tr key={o.orderId}>
                  <td>#{o.orderId}</td>
                  <td><span className={`badge status-${o.status.toLowerCase()}`}>{o.status}</span></td>
                  <td>₹{Number(o.totalAmount).toFixed(2)}</td>
                  <td>
                    {NEXT_STATUSES[o.status].map((s) => (
                      <button key={s} className="btn btn-small" onClick={() => setStatus(o.orderId, s)}>
                        {s === "CANCELLED" ? "Cancel (restores stock)" : s}
                      </button>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
