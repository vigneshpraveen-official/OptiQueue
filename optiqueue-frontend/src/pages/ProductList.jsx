import { useEffect, useState } from "react";
import client from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";

export default function ProductList() {
  const { user } = useAuth();
  const { add } = useCart();
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [added, setAdded] = useState(null);

  useEffect(() => {
    client
      .get("/api/products", { params: { page, size: 12 } })
      .then((res) => setData(res.data))
      .catch(() => setError("Could not load products"));
  }, [page]);

  if (error) return <div className="error page-pad">{error}</div>;
  if (!data) return <div className="page-pad muted">Loading…</div>;

  return (
    <div className="page-pad">
      <h2>Products</h2>
      <div className="grid">
        {data.content.map((p) => (
          <div className="card product-card" key={p.id}>
            <div className="product-name">{p.name}</div>
            <div className="muted sku">{p.sku}</div>
            <div className="price">₹{Number(p.price).toFixed(2)}</div>
            <div className={p.stockQuantity > 0 ? "stock" : "stock out"}>
              {p.stockQuantity > 0 ? `${p.stockQuantity} in stock` : "Out of stock"}
            </div>
            {user.role === "CUSTOMER" && (
              <button
                className="btn btn-primary"
                disabled={p.stockQuantity === 0}
                onClick={() => {
                  add(p);
                  setAdded(p.id);
                  setTimeout(() => setAdded(null), 900);
                }}
              >
                {added === p.id ? "Added ✓" : "Add to cart"}
              </button>
            )}
          </div>
        ))}
      </div>
      {data.totalPages > 1 && (
        <div className="pager">
          <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>
            ‹ Prev
          </button>
          <span className="muted">
            Page {data.page + 1} of {data.totalPages}
          </span>
          <button
            className="btn"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            Next ›
          </button>
        </div>
      )}
    </div>
  );
}
