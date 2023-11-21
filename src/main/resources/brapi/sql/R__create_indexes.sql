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

-- Indexes to improve read performance of Germplasm operations.
CREATE INDEX CONCURRENTLY IF NOT EXISTS "pedigree_edge_this_node_id" ON pedigree_edge (this_node_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "pedigree_edge_connected_node_id" ON pedigree_edge (connceted_node_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "pedigree_edge_edge_type" ON pedigree_edge (edge_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "program_external_references_program_entity_id" ON program_external_references (program_entity_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "external_reference_composite" ON external_reference (external_reference_source, external_reference_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "list_list_name" ON list (list_name);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "pedigree_node_germplasm_id" ON pedigree_node (germplasm_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "germplasm_external_references_germplasm_entity_id" ON germplasm_external_references (germplasm_entity_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "germplasm_synonym_germplasm_id" ON germplasm_synonym (germplasm_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS "germplasm_taxon_germplasm_id" ON germplasm_taxon (germplasm_id);