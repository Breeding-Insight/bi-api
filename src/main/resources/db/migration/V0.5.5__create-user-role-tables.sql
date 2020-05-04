CREATE TABLE system_user_role (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    bi_user_id UUID NOT NULL,
    system_role_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

CREATE TABLE system_role (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    domain text NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE system_user_role ADD FOREIGN KEY (bi_user_id) REFERENCES bi_user (id);
ALTER TABLE system_user_role ADD FOREIGN KEY (system_role_id) REFERENCES system_role (id);

DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

INSERT INTO system_role (domain, created_by, updated_by)
VALUES
('admin', user_id, user_id);

INSERT INTO system_user_role (bi_user_id, system_role_id, created_by, updated_by)
SELECT bi_user.id, system_role.id, user_id, user_id FROM
bi_user JOIN system_role
ON bi_user.name = 'BI-DEV Admin' and system_role.domain = 'admin';


END $$;