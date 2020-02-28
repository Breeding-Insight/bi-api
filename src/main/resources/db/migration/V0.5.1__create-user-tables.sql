CREATE TABLE bi_user (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    orcid text,
    name text,
    email text,
    like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

ALTER TABLE base_edit_track_entity ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);

ALTER TABLE base_edit_track_entity ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);