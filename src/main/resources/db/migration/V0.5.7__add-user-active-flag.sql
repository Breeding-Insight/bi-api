ALTER TABLE bi_user ADD active bool NOT NULL DEFAULT true;

-- Fixes for missing foreign keys
ALTER TABLE system_role ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE system_role ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
ALTER TABLE system_user_role ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE system_user_role ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);