import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import client from "../api/client";

export default function MyOrders() {
  const location = useLocation();
  const placed = location.state?.placed;
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    client
      .get("/api/orders/mine", { params: { size: 50, sort: "id,desc" } })
      .then((res) => setData(res.data))
      .catch(() => setError("Could not load orders"));
  }, []);

  if (error) return <div className="error page-pad">{error}</div>;
  if (!data) return <div className="page-pad muted">Loading…</div>;

  return (
    <div className="page-pad">
      <h2>My Orders</h2>
      {placed && (
        <div className="success">
          Order #{placed.orderId} placed — total ₹{Number(placed.totalAmount).toFixed(2)}
        </div>
      )}
      {data.content.length === 0 ? (
        <p className="muted">No orders yet.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Order #</th>
              <th>Status</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((o) => (
              <tr key={o.orderId}>
                <td>#{o.orderId}</td>
                <td>
                  <span className={`badge status-${o.status.toLowerCase()}`}>{o.status}</span>
                </td>
                <td>₹{Number(o.totalAmount).toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
