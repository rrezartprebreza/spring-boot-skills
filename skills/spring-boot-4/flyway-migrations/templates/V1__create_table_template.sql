-- V1__create_orders_table.sql
-- Initial schema for the orders domain

CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_email  VARCHAR(255)    NOT NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(19, 2)  NOT NULL DEFAULT 0.00,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL,
    product_name    VARCHAR(255)    NOT NULL,
    quantity        INT             NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(19, 2)  NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Always index foreign keys — JPA doesn't create these automatically
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- Index commonly filtered/sorted columns
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_customer_email ON orders (customer_email);
CREATE INDEX idx_orders_created_at ON orders (created_at);
