-- Indexes to improve read performance of Germplasm operations.
CREATE INDEX CONCURRENTLY "pedigree_edge_this_node_id" ON pedigree_edge (this_node_id);
CREATE INDEX CONCURRENTLY "pedigree_edge_connected_node_id" ON pedigree_edge (connceted_node_id);
CREATE INDEX CONCURRENTLY "pedigree_edge_edge_type" ON pedigree_edge (edge_type);
CREATE INDEX CONCURRENTLY "program_external_references_program_entity_id" ON program_external_references (program_entity_id);
CREATE INDEX CONCURRENTLY "external_reference_composite" ON external_reference (external_reference_source, external_reference_id);
CREATE INDEX CONCURRENTLY "program_additional_info_composite" ON program_additional_info (additional_info_id, program_entity_id);
CREATE INDEX CONCURRENTLY "list_list_name" ON list (list_name);
CREATE INDEX CONCURRENTLY "pedigree_node_germplasm_id" ON pedigree_node (germplasm_id);
CREATE INDEX CONCURRENTLY "germplasm_additional_info_germplasm_entity_id" ON germplasm_additional_info (germplasm_entity_id);
CREATE INDEX CONCURRENTLY "germplasm_external_references_germplasm_entity_id" ON germplasm_external_references (germplasm_entity_id);
CREATE INDEX CONCURRENTLY "germplasm_synonym_germplasm_id" ON germplasm_synonym (germplasm_id);
CREATE INDEX CONCURRENTLY "germplasm_taxon_germplasm_id" ON germplasm_taxon (germplasm_id);