package org.breedinginsight.brapps.importer.daos;

import org.breedinginsight.brapps.importer.model.workflow.ImportMappingWorkflow;
import org.breedinginsight.dao.db.tables.daos.ImporterMappingWorkflowDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

public class ImportMappingWorkflowDAO extends ImporterMappingWorkflowDao {

    private DSLContext dsl;

    @Inject
    public ImportMappingWorkflowDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    /**
     * Retrieves a list of ImportMappingWorkflow objects associated with the given mappingId.
     *
     * @param mappingId The UUID of the mapping to retrieve the workflows for.
     * @return A list of ImportMappingWorkflow objects.
     */
    public List<ImportMappingWorkflow> getWorkflowsByImportMappingId(UUID mappingId) {

        List<Record> records = dsl.select()
                .from(IMPORTER_MAPPING_WORKFLOW)
                .where(IMPORTER_MAPPING_WORKFLOW.MAPPING_ID.eq(mappingId))
                .fetch();

        List<ImportMappingWorkflow> workflows = new ArrayList<>();
        for (Record record: records) {
            workflows.add(ImportMappingWorkflow.parseSQLRecord(record));
        }
        return workflows;
    }

}
