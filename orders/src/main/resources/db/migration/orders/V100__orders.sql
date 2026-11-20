CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    teams_user_id VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    food_type     VARCHAR(100) NOT NULL,
    item          VARCHAR(200) NOT NULL,
    notes         VARCHAR(500) NOT NULL DEFAULT '',
    order_date    DATE         NOT NULL,
    placed_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_orders_user_day UNIQUE (teams_user_id, order_date)
);
