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

alter table program add column obs_unit_sequence text;

do
$$
declare
f record;
begin
for f in select * from program
loop
    if f.key is NULL then
       RAISE EXCEPTION 'Programs must have a key associated with them';
    end if;
    execute format('create sequence %s_obs_unit_sequence',f.key);
    update program set obs_unit_sequence = format('%s_obs_unit_sequence', f.key) where id = f.id;
end loop;
end;
$$