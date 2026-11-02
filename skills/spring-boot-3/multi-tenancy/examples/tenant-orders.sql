CREATE TABLE tenant_orders (
    tenant_id VARCHAR(128) NOT NULL,
    id UUID NOT NULL,
    description TEXT NOT NULL,
    PRIMARY KEY (tenant_id, id)
);
