insert into bi_user (id, orcid, name, created_by, updated_by)
values
('74a6ebfe-d114-419b-8bdc-2f7b52d26172', '${admin-orcid}', 'BI-DEV Admin', '74a6ebfe-d114-419b-8bdc-2f7b52d26172', '74a6ebfe-d114-419b-8bdc-2f7b52d26172');

DO $$
DECLARE
    user_id UUID;
    admin_orcid TEXT := '${admin-orcid}';
BEGIN

user_id := (SELECT id FROM bi_user WHERE orcid = admin_orcid);

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