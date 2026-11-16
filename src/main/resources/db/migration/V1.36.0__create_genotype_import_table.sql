/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Join table linking sample submissions to genotype imports
create table genotype_import
(
    like base_entity including defaults including constraints including indexes,
    sample_submission_id uuid not null,
    importer_import_id   uuid not null,
    like base_edit_track_entity including all
);

alter table genotype_import
    add constraint fk_gi_sample_submission
        foreign key (sample_submission_id)
            references sample_submission (id);

alter table genotype_import
    add constraint fk_gi_importer_import
        foreign key (importer_import_id)
            references importer_import (id);

alter table genotype_import
    add constraint fk_gi_created_by
        foreign key (created_by)
            references bi_user (id);

alter table genotype_import
    add constraint fk_gi_updated_by
        foreign key (updated_by)
            references bi_user (id);

alter table genotype_import
    add constraint uq_gi_importer_import_id
        unique (importer_import_id);

create index idx_gi_sample_submission_id
    on genotype_import (sample_submission_id);

create index idx_gi_importer_import_id
    on genotype_import (importer_import_id);