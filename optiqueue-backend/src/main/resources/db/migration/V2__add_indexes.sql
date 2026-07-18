-- Secondary indexes on the hot filter/join columns (see optiqueue_master.md §4).
-- Deliberately shipped as V2, after benchmarking the V1 (index-less) schema —
-- the before/after latency numbers are recorded in PROJECT_CONTEXT.md.
-- Note: users.username and products.sku already have indexes from their UNIQUE
-- constraints in V1, so no extra index is needed for them.

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
