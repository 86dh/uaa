SET foreign_key_checks = 0;
ALTER TABLE SPRING_SESSION MODIFY COLUMN PRIMARY_ID char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_520_ci;
ALTER TABLE SPRING_SESSION_ATTRIBUTES MODIFY COLUMN SESSION_PRIMARY_ID char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_520_ci;
SET foreign_key_checks = 1;
