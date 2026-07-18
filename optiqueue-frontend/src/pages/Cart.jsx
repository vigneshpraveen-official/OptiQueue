import { useState } from "react";
import { useNavigate } from "react-router-dom";
import client from "../api/client";
import { useCart } from "../context/CartContext";

export default function Cart() {
  const { items, setQuantity, clear, total } = useCart();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const placeOrder = async () => {
    setError("");
    setBusy(true);
    try {
      const { data } = await client.post("/api/orders", {
        items: items.map((i) => ({ productId: i.product.id, quantity: i.quantity })),
      });
      clear();
      navigate("/orders", { state: { placed: data } });
    } catch (err) {
      const data = err.response?.data;
      if (data?.error === "STOCK_CONFLICT") {
        setError("Stock changed while ordering (someone beat you to it). Please try again.");
      } else if (data?.error === "INSUFFICIENT_STOCK") {
        setError(data.message);
      } else {
        setError(data?.message || "Order failed. Please retry.");
      }
    } finally {
      setBusy(false);
    }
  };

  if (items.length === 0)
    return (
      <div className="page-pad">
        <h2>Cart</h2>
        <p className="muted">Your cart is empty.</p>
      </div>
    );

  return (
    <div className="page-pad">
      <h2>Cart</h2>
      <table className="table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Price</th>
            <th>Qty</th>
            <th>Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {items.map(({ product, quantity }) => (
            <tr key={product.id}>
              <td>{product.name}</td>
              <td>₹{Number(product.price).toFixed(2)}</td>
              <td>
                <div className="qty">
                  <button className="btn" onClick={() => setQuantity(product.id, quantity - 1)}>−</button>
                  <span>{quantity}</span>
                  <button
                    className="btn"
                    disabled={quantity >= product.stockQuantity}
                    onClick={() => setQuantity(product.id, quantity + 1)}
                  >
                    +
                  </button>
                </div>
              </td>
              <td>₹{(product.price * quantity).toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="cart-footer">
        <div className="total">Total: ₹{total.toFixed(2)}</div>
        {error && <div className="error">{error}</div>}
        <button className="btn btn-primary" onClick={placeOrder} disabled={busy}>
          {busy ? "Placing…" : "Place order"}
        </button>
      </div>
    </div>
  );
}
