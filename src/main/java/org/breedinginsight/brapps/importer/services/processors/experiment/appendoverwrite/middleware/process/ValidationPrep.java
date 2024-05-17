package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.ObservationProcessor.getObservationHash;

@Slf4j
public class ValidationPrep extends ExpUnitMiddleware {
    ObservationService observationService;

    @Inject
    public ValidationPrep(ObservationService observationService) {
        this.observationService = observationService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        // Get all the dynamic columns of the import
        ImportUpload upload = context.getImportContext().getUpload();
        Table data = context.getImportContext().getData();
        String[] dynamicColNames = upload.getDynamicColumnNames();
        List<Column<?>> dynamicCols = data.columns(dynamicColNames);

        // Collect the columns for observation variable data
        List<Column<?>> phenotypeCols = dynamicCols.stream().filter(col -> !col.name().startsWith(ExpImportProcessConstants.TIMESTAMP_PREFIX)).collect(Collectors.toList());
        Set<String> varNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toSet());

        for (int i = 0; i < context.getImportContext().getImportRows().size(); i++) {
            Integer rowNum = i;
            ExperimentObservation row = (ExperimentObservation) context.getImportContext().getImportRows().get(rowNum);

            // Fetch the pending import for the row, creating one if null
            PendingImport mappedImportRow = context.getImportContext().getMappedBrAPIImport().getOrDefault(rowNum, new PendingImport());

            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();

            String unitId = row.getObsUnitID();
            mappedImportRow.setTrial(context.getExpUnitContext().getPendingTrialByOUId().get(unitId));
            mappedImportRow.setLocation(context.getExpUnitContext().getPendingLocationByOUId().get(unitId));
            mappedImportRow.setStudy(context.getExpUnitContext().getPendingStudyByOUId().get(unitId));
            mappedImportRow.setObservationUnit(context.getExpUnitContext().getPendingObsUnitByOUId().get(unitId));
            mappedImportRow.setGermplasm(context.getExpUnitContext().getPendingGermplasmByOUId().get(unitId));

            // loop over phenotype column observation data for current row
            for (Column<?> column : phenotypeCols) {
                String observationHash = observationService.getObservationHash(
                        pendingStudyByOUId.get(unitId).getBrAPIObject().getStudyName() +
                                pendingObsUnitByOUId.get(unitId).getBrAPIObject().getObservationUnitName(),
                        getVariableNameFromColumn(column),
                        pendingStudyByOUId.get(unitId).getBrAPIObject().getStudyName()
                );

                // if value was blank won't be entry in map for this observation
                observations.add(observationByHash.get(observationHash));
            }
        }

//        try {
//
//        } catch (ApiException e) {
//            this.compensate(context, new MiddlewareError(() -> {
//                throw new RuntimeException(e);
//            }));
//        }

        return processNext(context);
    }
}
