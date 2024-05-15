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

/**
  Table maps workflows to import mappings and provides required configuration options

  mapping_id - link to importer_mapping that this provides a workflow for
  name - name that will be displayed on front end
  bean - must match @Named("<bean>") annotation on Workflow class
  position - for ordering records explicitly, wanted for front end default option and order
 */
CREATE TABLE importer_mapping_workflow
(
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    mapping_id      UUID NOT NULL,
    name            TEXT NOT NULL,
    bean            TEXT NOT NULL,
    position        INTEGER NOT NULL
);

ALTER TABLE importer_mapping_workflow
    ADD FOREIGN KEY (mapping_id) REFERENCES importer_mapping (id);

DO
$$
DECLARE
  exp_mapping_id UUID;
BEGIN
  exp_mapping_id := (SELECT id FROM importer_mapping WHERE name = 'ExperimentsTemplateMap');

INSERT INTO public.importer_mapping_workflow (mapping_id, name, bean, position)
VALUES
    (exp_mapping_id, 'Create new experiment', 'CreateNewExperimentWorkflow', 0),
    (exp_mapping_id, 'Append experimental dataset', 'AppendOverwritePhenotypesWorkflow', 1),
    (exp_mapping_id, 'Create new experimental environment', 'CreateNewEnvironmentWorkflow', 2);
END
$$;