DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

insert into bi_user (orcid, name, email, created_by, updated_by)
values
('5555-6666-7777-8888', 'Other Test User', 'other-test@test.com', user_id, user_id);

END $$;