CREATE TABLE bi_user (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    orcid text,
    name text,
    email text,
    like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);
ALTER TABLE bi_user ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE bi_user ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);