CREATE TABLE settings (
    id         INT PRIMARY KEY CHECK (id = 1),
    cut_off    TIME NOT NULL,
    shop_email VARCHAR(320) NOT NULL
);

INSERT INTO settings (id, cut_off, shop_email) VALUES (1, '10:00', '');
