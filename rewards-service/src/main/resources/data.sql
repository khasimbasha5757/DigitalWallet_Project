-- Seeding rewards catalog with user-specified items
INSERT INTO reward_catalog (id, name, description, cost_in_points, required_tier, stock_quantity)
VALUES
('a1a1a1a1-b1b1-c1c1-d1d1-e1e1e1e1e101', 'Amazon Voucher', '$10 Amazon Gift Card', 50, 'BASIC', 100),
('a1a1a1a1-b1b1-c1c1-d1d1-e1e1e1e1e102', 'Flipkart Voucher', '$5 Flipkart Gift Card', 30, 'BASIC', 100),
('a1a1a1a1-b1b1-c1c1-d1d1-e1e1e1e1e103', 'Cashback Rs 100', 'Direct wallet credit', 20, 'BASIC', 1000)
ON CONFLICT (id) DO NOTHING;
