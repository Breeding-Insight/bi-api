--
-- See the NOTICE file distributed with this work for additional information
-- regarding copyright ownership.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

create table program_breeding_method
(
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    name              TEXT,
    abbreviation      TEXT,
    description       TEXT,
    category          TEXT,
    genetic_diversity TEXT,
    program_id        uuid not null references program (id),
    like base_edit_track_entity INCLUDING ALL
);

create table program_enabled_breeding_methods
(
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    breeding_method_id uuid not null references breeding_method (id),
    program_id         uuid not null references program (id),
    like base_edit_track_entity INCLUDING ALL
);

insert into program_enabled_breeding_methods(breeding_method_id, program_id, created_by, created_at, updated_by, updated_at)
select breeding_method.id, program.id, (select id from bi_user where name = 'system'), now(), (select id from bi_user where name = 'system'), now() from breeding_method, program;