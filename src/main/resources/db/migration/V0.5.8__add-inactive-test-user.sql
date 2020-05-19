DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

insert into bi_user (orcid, name, email, created_by, updated_by, active)
values
('1111-1111-1111-1111', 'Inactive Test User', 'inactivetest@test.com', user_id, user_id, false);

END $$;