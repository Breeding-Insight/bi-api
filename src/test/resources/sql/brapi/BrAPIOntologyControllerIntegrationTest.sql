-- name: CopyrightNotice
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

-- name: AddObservations
insert into observation (id, observation_variable_id, program_id, value)
select md5(random()::text || clock_timestamp()::text)::uuid, observation_variable.id, matching_program.id, 'test'
from observation_variable
join
 (
     select p.id, er.external_reference_id from
         "program" p
             join
         program_external_references per on p.id = per.program_entity_id
             join
         external_reference er on per.external_references_id  = er.id
            where
         er.external_reference_id = ?::text
 ) as matching_program on 1=1;

-- name: DeleteObservations
delete from observation;