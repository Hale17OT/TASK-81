-- ============================================================
-- V2__seed_data.sql
-- Flyway migration: Seed initial data for CampusStore
-- MySQL 8.0 compatible
-- ============================================================

-- ============================================================
-- 1. DEPARTMENTS
-- ============================================================

INSERT INTO department (name, description) VALUES
    ('Science', 'Science department including biology, chemistry, physics'),
    ('Engineering', 'Engineering department including mechanical, electrical, civil'),
    ('General Studies', 'General studies including literature, history, arts');

-- ============================================================
-- 2. ZONES
-- ============================================================

INSERT INTO zone (name, description, building, floor_level) VALUES
    ('Building A Floor 1', 'First floor of Building A', 'Building A', 1),
    ('Building A Floor 2', 'Second floor of Building A', 'Building A', 2),
    ('Building B Floor 1', 'First floor of Building B', 'Building B', 1),
    ('Library', 'Campus library', 'Library', 1);

-- ============================================================
-- 3. ZONE DISTANCES (bidirectional)
-- ============================================================

INSERT INTO zone_distance (from_zone_id, to_zone_id, weight) VALUES
    -- A1 <-> A2: weight 2.0
    ((SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     (SELECT id FROM zone WHERE name = 'Building A Floor 2'), 2.00),
    ((SELECT id FROM zone WHERE name = 'Building A Floor 2'),
     (SELECT id FROM zone WHERE name = 'Building A Floor 1'), 2.00),
    -- A1 <-> B1: weight 5.0
    ((SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     (SELECT id FROM zone WHERE name = 'Building B Floor 1'), 5.00),
    ((SELECT id FROM zone WHERE name = 'Building B Floor 1'),
     (SELECT id FROM zone WHERE name = 'Building A Floor 1'), 5.00),
    -- A1 <-> Library: weight 4.0
    ((SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     (SELECT id FROM zone WHERE name = 'Library'), 4.00),
    ((SELECT id FROM zone WHERE name = 'Library'),
     (SELECT id FROM zone WHERE name = 'Building A Floor 1'), 4.00),
    -- A2 <-> B1: weight 6.0
    ((SELECT id FROM zone WHERE name = 'Building A Floor 2'),
     (SELECT id FROM zone WHERE name = 'Building B Floor 1'), 6.00),
    ((SELECT id FROM zone WHERE name = 'Building B Floor 1'),
     (SELECT id FROM zone WHERE name = 'Building A Floor 2'), 6.00),
    -- A2 <-> Library: weight 5.0
    ((SELECT id FROM zone WHERE name = 'Building A Floor 2'),
     (SELECT id FROM zone WHERE name = 'Library'), 5.00),
    ((SELECT id FROM zone WHERE name = 'Library'),
     (SELECT id FROM zone WHERE name = 'Building A Floor 2'), 5.00),
    -- B1 <-> Library: weight 3.0
    ((SELECT id FROM zone WHERE name = 'Building B Floor 1'),
     (SELECT id FROM zone WHERE name = 'Library'), 3.00),
    ((SELECT id FROM zone WHERE name = 'Library'),
     (SELECT id FROM zone WHERE name = 'Building B Floor 1'), 3.00);

-- ============================================================
-- 4. STORAGE LOCATIONS (2 per zone)
-- ============================================================

-- Zone A1: Building A Floor 1
INSERT INTO storage_location (zone_id, name, x_coord, y_coord, level, temperature_zone, security_level, capacity) VALUES
    ((SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     'Science Lab Storage', 1.00, 2.00, 0, 'AMBIENT', 'STANDARD', 50),
    ((SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     'Chemical Cabinet', 3.00, 2.00, 0, 'COOL', 'RESTRICTED', 20);

-- Zone A2: Building A Floor 2
INSERT INTO storage_location (zone_id, name, x_coord, y_coord, level, temperature_zone, security_level, capacity) VALUES
    ((SELECT id FROM zone WHERE name = 'Building A Floor 2'),
     'Engineering Workshop', 1.00, 1.00, 1, 'AMBIENT', 'STANDARD', 80),
    ((SELECT id FROM zone WHERE name = 'Building A Floor 2'),
     'Electronics Lock-up', 4.00, 1.00, 1, 'AMBIENT', 'HIGH_SECURITY', 30);

-- Zone B1: Building B Floor 1
INSERT INTO storage_location (zone_id, name, x_coord, y_coord, level, temperature_zone, security_level, capacity) VALUES
    ((SELECT id FROM zone WHERE name = 'Building B Floor 1'),
     'General Storage Room', 2.00, 3.00, 0, 'AMBIENT', 'STANDARD', 100),
    ((SELECT id FROM zone WHERE name = 'Building B Floor 1'),
     'Art Supplies Shelf', 5.00, 3.00, 0, 'AMBIENT', 'STANDARD', 60);

-- Zone Library
INSERT INTO storage_location (zone_id, name, x_coord, y_coord, level, temperature_zone, security_level, capacity) VALUES
    ((SELECT id FROM zone WHERE name = 'Library'),
     'Book Reserve', 1.00, 1.00, 0, 'AMBIENT', 'STANDARD', 200),
    ((SELECT id FROM zone WHERE name = 'Library'),
     'Media Center', 3.00, 1.00, 0, 'COOL', 'STANDARD', 40);

-- ============================================================
-- 5. USERS
-- ============================================================

-- admin (ADMIN, no department, home_zone: A1)
-- SECURITY NOTE: Seed data stores raw BCrypt hashes as BINARY bytes.
-- On first application startup, SeedDataInitializer automatically detects these
-- raw BCrypt hashes (by their $2b$ prefix) and AES-256-GCM encrypts them using
-- the runtime MASTER_KEY_PASSPHRASE. After startup, all password hashes are
-- AES-encrypted at rest. CampusUserDetailsService also has a compatibility
-- branch that handles both formats during the brief migration window.
-- Bootstrap credentials (rotate on first login via /account/change-password):
--   admin / admin123   |   teacher1,teacher2 / teacher123   |   student1,student2 / student123
INSERT INTO `user` (username, password_hash_encrypted, display_name, home_zone_id, department_id, account_status, personalization_enabled) VALUES
    ('admin',
     CAST('$2b$10$38qmGJtofbBF0/5zxpyWy.t9g6iyGh8FrIDgNr3j0SNpr2I5757vu' AS BINARY),
     'System Administrator',
     (SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     NULL,
     'ACTIVE',
     TRUE);

-- teacher1 (TEACHER, Science, home_zone: A1)
INSERT INTO `user` (username, password_hash_encrypted, display_name, home_zone_id, department_id, account_status, personalization_enabled) VALUES
    ('teacher1',
     CAST('$2b$10$ZKwtL3Qp0s/8ALgCZ7ha/OVqBVBmqacPN4OUpZkW1fakObzvRMWIC' AS BINARY),
     'Dr. Sarah Chen',
     (SELECT id FROM zone WHERE name = 'Building A Floor 1'),
     (SELECT id FROM department WHERE name = 'Science'),
     'ACTIVE',
     TRUE);

-- teacher2 (TEACHER, Engineering, home_zone: A2)
INSERT INTO `user` (username, password_hash_encrypted, display_name, home_zone_id, department_id, account_status, personalization_enabled) VALUES
    ('teacher2',
     CAST('$2b$10$ZKwtL3Qp0s/8ALgCZ7ha/OVqBVBmqacPN4OUpZkW1fakObzvRMWIC' AS BINARY),
     'Prof. James Miller',
     (SELECT id FROM zone WHERE name = 'Building A Floor 2'),
     (SELECT id FROM department WHERE name = 'Engineering'),
     'ACTIVE',
     TRUE);

-- student1 (STUDENT, no department, home_zone: B1)
INSERT INTO `user` (username, password_hash_encrypted, display_name, home_zone_id, department_id, account_status, personalization_enabled) VALUES
    ('student1',
     CAST('$2b$10$Ypotw0TkKuZ0XReh4InaVua.1nNisVuRyyBIW9l0QsOwvMfX0kGzm' AS BINARY),
     'Alex Johnson',
     (SELECT id FROM zone WHERE name = 'Building B Floor 1'),
     NULL,
     'ACTIVE',
     TRUE);

-- student2 (STUDENT, no department, home_zone: Library)
INSERT INTO `user` (username, password_hash_encrypted, display_name, home_zone_id, department_id, account_status, personalization_enabled) VALUES
    ('student2',
     CAST('$2b$10$Ypotw0TkKuZ0XReh4InaVua.1nNisVuRyyBIW9l0QsOwvMfX0kGzm' AS BINARY),
     'Maria Garcia',
     (SELECT id FROM zone WHERE name = 'Library'),
     NULL,
     'ACTIVE',
     TRUE);

-- ============================================================
-- 5b. USER ROLES
-- ============================================================

INSERT INTO user_role (user_id, role) VALUES
    ((SELECT id FROM `user` WHERE username = 'admin'), 'ADMIN'),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'TEACHER'),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'TEACHER'),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'STUDENT'),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'STUDENT');

-- ============================================================
-- 6. CATEGORIES
-- ============================================================

INSERT INTO category (name, description) VALUES
    ('Electronics', 'Electronic devices and components'),
    ('Chemicals', 'Laboratory chemicals and reagents'),
    ('Stationery', 'Office and classroom supplies'),
    ('Lab Equipment', 'Laboratory instruments and tools'),
    ('Books', 'Textbooks and reference materials'),
    ('Graduation Supplies', 'Caps, gowns, and ceremony items');

-- ============================================================
-- 7. INVENTORY ITEMS (20 total)
-- ============================================================

-- ----------------------------------------------------------
-- 7a. Electronics (4 items, requires_approval=true, dept=Engineering)
-- ----------------------------------------------------------

INSERT INTO inventory_item (name, description, sku, category_id, department_id, location_id,
    price_usd, item_condition, quantity_total, quantity_available, requires_approval, abc_classification, is_active) VALUES
    ('Arduino Uno R3 Board',
     'Microcontroller board based on the ATmega328P for prototyping and embedded systems projects',
     'ELEC-001',
     (SELECT id FROM category WHERE name = 'Electronics'),
     (SELECT id FROM department WHERE name = 'Engineering'),
     (SELECT id FROM storage_location WHERE name = 'Electronics Lock-up'),
     24.99, 'NEW', 40, 35, TRUE, 'A', TRUE),

    ('Digital Multimeter Fluke 117',
     'True-RMS multimeter for accurate voltage, current, and resistance measurements',
     'ELEC-002',
     (SELECT id FROM category WHERE name = 'Electronics'),
     (SELECT id FROM department WHERE name = 'Engineering'),
     (SELECT id FROM storage_location WHERE name = 'Electronics Lock-up'),
     189.99, 'GOOD', 15, 12, TRUE, 'A', TRUE),

    ('Breadboard and Jumper Wire Kit',
     'Solderless breadboard with 65-piece jumper wire assortment for circuit prototyping',
     'ELEC-003',
     (SELECT id FROM category WHERE name = 'Electronics'),
     (SELECT id FROM department WHERE name = 'Engineering'),
     (SELECT id FROM storage_location WHERE name = 'Engineering Workshop'),
     12.50, 'NEW', 60, 55, TRUE, 'B', TRUE),

    ('Oscilloscope Probe Set',
     'Pair of 100MHz oscilloscope probes with 1x/10x switch for signal analysis',
     'ELEC-004',
     (SELECT id FROM category WHERE name = 'Electronics'),
     (SELECT id FROM department WHERE name = 'Engineering'),
     (SELECT id FROM storage_location WHERE name = 'Electronics Lock-up'),
     45.00, 'LIKE_NEW', 20, 18, TRUE, 'B', TRUE);

-- ----------------------------------------------------------
-- 7b. Chemicals (3 items, requires_approval=true, dept=Science, Chemical Cabinet)
-- ----------------------------------------------------------

INSERT INTO inventory_item (name, description, sku, category_id, department_id, location_id,
    price_usd, item_condition, quantity_total, quantity_available, requires_approval, abc_classification, is_active) VALUES
    ('Hydrochloric Acid 1M Solution',
     '500ml bottle of 1 molar hydrochloric acid for titration experiments',
     'CHEM-001',
     (SELECT id FROM category WHERE name = 'Chemicals'),
     (SELECT id FROM department WHERE name = 'Science'),
     (SELECT id FROM storage_location WHERE name = 'Chemical Cabinet'),
     18.75, 'NEW', 25, 20, TRUE, 'A', TRUE),

    ('Sodium Hydroxide Pellets',
     '500g container of analytical-grade sodium hydroxide pellets for solution preparation',
     'CHEM-002',
     (SELECT id FROM category WHERE name = 'Chemicals'),
     (SELECT id FROM department WHERE name = 'Science'),
     (SELECT id FROM storage_location WHERE name = 'Chemical Cabinet'),
     14.50, 'NEW', 30, 28, TRUE, 'B', TRUE),

    ('Universal pH Indicator Solution',
     '100ml bottle of universal indicator for pH range 1-14 color-change experiments',
     'CHEM-003',
     (SELECT id FROM category WHERE name = 'Chemicals'),
     (SELECT id FROM department WHERE name = 'Science'),
     (SELECT id FROM storage_location WHERE name = 'Chemical Cabinet'),
     9.99, 'NEW', 40, 36, TRUE, 'B', TRUE);

-- ----------------------------------------------------------
-- 7c. Stationery (4 items, requires_approval=false, dept=General Studies)
-- ----------------------------------------------------------

INSERT INTO inventory_item (name, description, sku, category_id, department_id, location_id,
    price_usd, item_condition, quantity_total, quantity_available, requires_approval, abc_classification, is_active) VALUES
    ('Whiteboard Marker Set (12-pack)',
     'Assorted color dry-erase markers for classroom whiteboards',
     'STAT-001',
     (SELECT id FROM category WHERE name = 'Stationery'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'General Storage Room'),
     8.99, 'NEW', 100, 90, FALSE, 'C', TRUE),

    ('A4 Copy Paper Ream (500 sheets)',
     'Multipurpose 80gsm white copy paper for printing and handouts',
     'STAT-002',
     (SELECT id FROM category WHERE name = 'Stationery'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'General Storage Room'),
     5.49, 'NEW', 80, 70, FALSE, 'C', TRUE),

    ('Composition Notebook (College Ruled)',
     '100-page college-ruled composition notebook for student use',
     'STAT-003',
     (SELECT id FROM category WHERE name = 'Stationery'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'General Storage Room'),
     2.99, 'NEW', 100, 95, FALSE, 'C', TRUE),

    ('Heavy-Duty Stapler with Staples',
     'Desktop stapler with 5000-count staple box for office and classroom use',
     'STAT-004',
     (SELECT id FROM category WHERE name = 'Stationery'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'Art Supplies Shelf'),
     15.99, 'GOOD', 25, 22, FALSE, 'C', TRUE);

-- ----------------------------------------------------------
-- 7d. Lab Equipment (3 items, requires_approval=true, dept=Science)
-- ----------------------------------------------------------

INSERT INTO inventory_item (name, description, sku, category_id, department_id, location_id,
    price_usd, item_condition, quantity_total, quantity_available, requires_approval, abc_classification, is_active) VALUES
    ('Compound Microscope 40x-1000x',
     'Binocular compound microscope with LED illumination for biology lab work',
     'LBEQ-001',
     (SELECT id FROM category WHERE name = 'Lab Equipment'),
     (SELECT id FROM department WHERE name = 'Science'),
     (SELECT id FROM storage_location WHERE name = 'Science Lab Storage'),
     499.99, 'GOOD', 10, 8, TRUE, 'A', TRUE),

    ('Analytical Balance (0.0001g)',
     'Precision analytical balance with draft shield for accurate mass measurement',
     'LBEQ-002',
     (SELECT id FROM category WHERE name = 'Lab Equipment'),
     (SELECT id FROM department WHERE name = 'Science'),
     (SELECT id FROM storage_location WHERE name = 'Science Lab Storage'),
     350.00, 'LIKE_NEW', 5, 4, TRUE, 'A', TRUE),

    ('Bunsen Burner with Gas Tubing',
     'Standard laboratory Bunsen burner with adjustable flame and 1m rubber gas tubing',
     'LBEQ-003',
     (SELECT id FROM category WHERE name = 'Lab Equipment'),
     (SELECT id FROM department WHERE name = 'Science'),
     (SELECT id FROM storage_location WHERE name = 'Science Lab Storage'),
     29.99, 'FAIR', 20, 17, TRUE, 'B', TRUE);

-- ----------------------------------------------------------
-- 7e. Books (3 items, requires_approval=false, dept=General Studies, Book Reserve)
-- ----------------------------------------------------------

INSERT INTO inventory_item (name, description, sku, category_id, department_id, location_id,
    price_usd, item_condition, quantity_total, quantity_available, requires_approval, abc_classification, is_active) VALUES
    ('Introduction to Algorithms (4th Edition)',
     'Comprehensive textbook on algorithms and data structures by Cormen, Leiserson, Rivest, and Stein',
     'BOOK-001',
     (SELECT id FROM category WHERE name = 'Books'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'Book Reserve'),
     89.99, 'GOOD', 15, 12, FALSE, 'B', TRUE),

    ('Organic Chemistry (9th Edition)',
     'Core organic chemistry textbook covering reaction mechanisms and molecular structure',
     'BOOK-002',
     (SELECT id FROM category WHERE name = 'Books'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'Book Reserve'),
     74.50, 'FAIR', 20, 16, FALSE, 'B', TRUE),

    ('World History: Patterns of Interaction',
     'Survey textbook covering global history from ancient civilizations to the modern era',
     'BOOK-003',
     (SELECT id FROM category WHERE name = 'Books'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'Book Reserve'),
     62.00, 'GOOD', 25, 22, FALSE, 'C', TRUE);

-- ----------------------------------------------------------
-- 7f. Graduation Supplies (3 items, requires_approval=false, dept=General Studies)
-- ----------------------------------------------------------

INSERT INTO inventory_item (name, description, sku, category_id, department_id, location_id,
    price_usd, item_condition, quantity_total, quantity_available, requires_approval, abc_classification, is_active) VALUES
    ('Graduation Cap and Gown Set',
     'Standard black cap and gown set available in sizes S through XXL for commencement ceremonies',
     'GRAD-001',
     (SELECT id FROM category WHERE name = 'Graduation Supplies'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'General Storage Room'),
     35.00, 'NEW', 100, 85, FALSE, 'B', TRUE),

    ('Diploma Frame (8.5x11)',
     'Mahogany-finish diploma frame with gold trim and glass cover for standard diploma size',
     'GRAD-002',
     (SELECT id FROM category WHERE name = 'Graduation Supplies'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'General Storage Room'),
     22.50, 'NEW', 50, 45, FALSE, 'C', TRUE),

    ('Honor Cord (Gold)',
     'Gold braided honor cord for academic achievement recognition at graduation ceremony',
     'GRAD-003',
     (SELECT id FROM category WHERE name = 'Graduation Supplies'),
     (SELECT id FROM department WHERE name = 'General Studies'),
     (SELECT id FROM storage_location WHERE name = 'Art Supplies Shelf'),
     0.99, 'NEW', 75, 70, FALSE, 'C', TRUE);

-- ============================================================
-- 8. PATH COST CONFIG
-- ============================================================

INSERT INTO path_cost_config (name, level_multiplier, is_active, config_json) VALUES
    ('Default Strategy', 2.00, TRUE, '{}');

-- ============================================================
-- 9. DATA RETENTION POLICIES
-- ============================================================

INSERT INTO data_retention_policy (entity_type, retention_days, description) VALUES
    ('search_log', 365, 'Search log entries retained for 1 year'),
    ('browsing_history', 365, 'Browsing history entries retained for 1 year'),
    ('audit_log', 2555, 'Audit log entries retained for 7 years'),
    ('notification', 365, 'Notification entries retained for 1 year'),
    ('email_outbox', 365, 'Email outbox entries retained for 1 year');

-- ============================================================
-- 10. USER PREFERENCES (DND and personalization)
-- ============================================================

-- admin: no DND, personalization enabled
INSERT INTO user_preference (user_id, dnd_start_time, dnd_end_time, personalization_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'admin'), NULL, NULL, TRUE);

-- teacher1: no DND, personalization enabled
INSERT INTO user_preference (user_id, dnd_start_time, dnd_end_time, personalization_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'teacher1'), NULL, NULL, TRUE);

-- teacher2: no DND, personalization enabled
INSERT INTO user_preference (user_id, dnd_start_time, dnd_end_time, personalization_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'teacher2'), NULL, NULL, TRUE);

-- student1: DND 22:00-07:00, personalization enabled
INSERT INTO user_preference (user_id, dnd_start_time, dnd_end_time, personalization_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'student1'), '22:00:00', '07:00:00', TRUE);

-- student2: DND 22:00-07:00, personalization enabled
INSERT INTO user_preference (user_id, dnd_start_time, dnd_end_time, personalization_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'student2'), '22:00:00', '07:00:00', TRUE);

-- ============================================================
-- 11. DEFAULT NOTIFICATION PREFERENCES
--     All notification types enabled, email disabled, for all 5 users
-- ============================================================

-- admin
INSERT INTO notification_preference (user_id, notification_type, enabled, email_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'admin'), 'REQUEST_SUBMITTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'REQUEST_APPROVED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'REQUEST_REJECTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'PICKUP_READY', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'PICKUP_OVERDUE', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'MISSED_CHECKIN', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'SYSTEM_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'CRAWLER_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'admin'), 'POLICY_CHANGE', TRUE, FALSE);

-- teacher1
INSERT INTO notification_preference (user_id, notification_type, enabled, email_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'REQUEST_SUBMITTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'REQUEST_APPROVED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'REQUEST_REJECTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'PICKUP_READY', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'PICKUP_OVERDUE', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'MISSED_CHECKIN', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'SYSTEM_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'CRAWLER_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher1'), 'POLICY_CHANGE', TRUE, FALSE);

-- teacher2
INSERT INTO notification_preference (user_id, notification_type, enabled, email_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'REQUEST_SUBMITTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'REQUEST_APPROVED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'REQUEST_REJECTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'PICKUP_READY', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'PICKUP_OVERDUE', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'MISSED_CHECKIN', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'SYSTEM_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'CRAWLER_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'teacher2'), 'POLICY_CHANGE', TRUE, FALSE);

-- student1
INSERT INTO notification_preference (user_id, notification_type, enabled, email_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'student1'), 'REQUEST_SUBMITTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'REQUEST_APPROVED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'REQUEST_REJECTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'PICKUP_READY', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'PICKUP_OVERDUE', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'MISSED_CHECKIN', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'SYSTEM_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'CRAWLER_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student1'), 'POLICY_CHANGE', TRUE, FALSE);

-- student2
INSERT INTO notification_preference (user_id, notification_type, enabled, email_enabled) VALUES
    ((SELECT id FROM `user` WHERE username = 'student2'), 'REQUEST_SUBMITTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'REQUEST_APPROVED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'REQUEST_REJECTED', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'PICKUP_READY', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'PICKUP_OVERDUE', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'MISSED_CHECKIN', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'SYSTEM_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'CRAWLER_ALERT', TRUE, FALSE),
    ((SELECT id FROM `user` WHERE username = 'student2'), 'POLICY_CHANGE', TRUE, FALSE);
