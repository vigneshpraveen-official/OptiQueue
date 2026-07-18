import { createContext, useContext, useState } from "react";

const CartContext = createContext(null);

export function CartProvider({ children }) {
  // items: { [productId]: { product, quantity } }
  const [items, setItems] = useState({});

  const add = (product, quantity = 1) =>
    setItems((prev) => {
      const existing = prev[product.id];
      return {
        ...prev,
        [product.id]: { product, quantity: (existing?.quantity || 0) + quantity },
      };
    });

  const setQuantity = (productId, quantity) =>
    setItems((prev) => {
      if (quantity <= 0) {
        const { [productId]: _, ...rest } = prev;
        return rest;
      }
      return { ...prev, [productId]: { ...prev[productId], quantity } };
    });

  const clear = () => setItems({});

  const list = Object.values(items);
  const count = list.reduce((n, i) => n + i.quantity, 0);
  const total = list.reduce((s, i) => s + i.product.price * i.quantity, 0);

  return (
    <CartContext.Provider value={{ items: list, add, setQuantity, clear, count, total }}>
      {children}
    </CartContext.Provider>
  );
}

export const useCart = () => useContext(CartContext);
