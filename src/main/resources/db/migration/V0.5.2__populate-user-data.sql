insert into bi_user (id, name, created_by, updated_by)
values
('00000000-0000-0000-0000-000000000000', 'system', '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000');

DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

insert into bi_user (orcid, name, created_by, updated_by)
values
('0000-0003-0437-8310', 'BI-DEV Admin', user_id, user_id);

insert into bi_user (orcid, name, created_by, updated_by)
values
('0000-0002-5527-2711', 'Chris Tucker', user_id, user_id);

insert into bi_user (orcid, name, email, created_by, updated_by)
values
('0000-0002-7156-4503', 'Nick Palladino', 'nicksandbox@mailinator.com', user_id, user_id);

insert into bi_user (orcid, name, email, created_by, updated_by)
values
('1111-2222-3333-4444', 'Test User', 'test@test.com', user_id, user_id);

END $$;