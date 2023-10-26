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

create table sample_submission
(
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    name            TEXT,
    submitted       bool default false,
    submitted_date   timestamp(0) with time zone,
    submitted_by UUID,
    vendor_order_id TEXT,
    vendor_status TEXT,
    vendor_status_last_check timestamp(0) with time zone,
    shipmentforms   jsonb,
    program_id      UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE sample_submission
    ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE sample_submission
    ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
ALTER TABLE sample_submission
    ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE sample_submission
    ADD FOREIGN KEY (submitted_by) REFERENCES bi_user (id);