-- Sample sellable products (fixed UUIDs for combo_items FKs)
INSERT INTO products (id, name, product_type, category, price, options_enabled, customisation_notes, active, created_at, updated_at)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'Garlic Bread', 'simple', 'veg', 80.00, FALSE, NULL, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('a1000000-0000-4000-8000-000000000002', 'Chicken Wings', 'simple', 'non-veg', 150.00, FALSE, NULL, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('a1000000-0000-4000-8000-000000000003', 'Snack Combo', 'combo', 'non-veg', 199.00, FALSE, NULL, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('a1000000-0000-4000-8000-000000000004', 'Margherita Pizza', 'pizza', 'veg', 299.00, TRUE, 'Extra basil on request', TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00');

INSERT INTO combo_items (combo_id, simple_id)
VALUES
    ('a1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000001'),
    ('a1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000002');

-- FR-4a crust sizes (10 inch small = base)
INSERT INTO option_entities (id, kind, name, price, is_base, created_at, updated_at)
VALUES
    ('b1000000-0000-4000-8000-000000000001', 'CRUST_SIZE', '10 inch (small)', 0.00, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000002', 'CRUST_SIZE', '12 inch (medium)', 40.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000003', 'CRUST_SIZE', '15 inch (large)', 80.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00');

-- FR-4b crust types (thin crust = base, price 10; deep dish = 25)
INSERT INTO option_entities (id, kind, name, price, is_base, created_at, updated_at)
VALUES
    ('b1000000-0000-4000-8000-000000000011', 'CRUST_TYPE', 'thin crust', 10.00, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000012', 'CRUST_TYPE', 'cheese burst', 20.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000013', 'CRUST_TYPE', 'deep dish', 25.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00');

-- FR-4c toppings (olive = base)
INSERT INTO option_entities (id, kind, name, price, is_base, created_at, updated_at)
VALUES
    ('b1000000-0000-4000-8000-000000000021', 'TOPPING', 'olive', 15.00, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000022', 'TOPPING', 'chicken', 35.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000023', 'TOPPING', 'mushrooms', 25.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000024', 'TOPPING', 'pepperoni', 40.00, FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00');

-- First PDF job can produce v1 when dirty=true and last_pdf_version=0
INSERT INTO catalog_meta (id, dirty, last_catalog_change_at, last_pdf_version)
VALUES (1, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', 0);
