DO $$
DECLARE
user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

-- just putting blank strings in for descriptions until later date, can update if needed
INSERT INTO species (common_name, description, created_by, updated_by)
VALUES
    ('Sunflower', '', user_id, user_id) ON CONFLICT DO NOTHING;
END $$;
