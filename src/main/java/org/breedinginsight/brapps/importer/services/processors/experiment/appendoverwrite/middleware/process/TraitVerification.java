package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationVariableService;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_REGEX;

@Slf4j
public class TraitVerification extends ExpUnitMiddleware {
    ObservationVariableService observationVariableService;
    ObservationService observationService;
    BrAPIObservationDAO brAPIObservationDAO;
    FileMappingUtil fileMappingUtil;

    @Inject
    public TraitVerification(ObservationVariableService observationVariableService,
                             BrAPIObservationDAO brAPIObservationDAO,
                             ObservationService observationService,
                             FileMappingUtil fileMappingUtil) {
        this.observationVariableService = observationVariableService;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.observationService = observationService;
        this.fileMappingUtil = fileMappingUtil;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("verifying traits listed in import");

        // Get all the dynamic columns of the import
        ImportUpload upload = context.getImportContext().getUpload();
        Table data = context.getImportContext().getData();
        String[] dynamicColNames = upload.getDynamicColumnNames();
        List<Column<?>> dynamicCols = data.columns(dynamicColNames);

        // Collect the columns for observation variable data
        List<Column<?>> phenotypeCols = dynamicCols.stream().filter(col -> !col.name().startsWith(ExpImportProcessConstants.TIMESTAMP_PREFIX)).collect(Collectors.toList());
        Set<String> varNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toSet());

        // Collect the columns for observation timestamps
        List<Column<?>> timestampCols = dynamicCols.stream().filter(col -> col.name().startsWith(ExpImportProcessConstants.TIMESTAMP_PREFIX)).collect(Collectors.toList());
        Set<String> tsNames = timestampCols.stream().map(Column::name).collect(Collectors.toSet());

        // Construct validation errors for any timestamp columns that don't have a matching variable column
        List<BrAPIImport> importRows = context.getImportContext().getImportRows();
        ValidationErrors validationErrors = context.getPendingData().getValidationErrors();
        List<ValidationError> tsValErrs = observationVariableService.validateMatchedTimestamps(varNames, timestampCols).orElse(new ArrayList<>());
        for (int rowNum = 0; rowNum < importRows.size(); i++) {
            tsValErrs.forEach(validationError -> validationErrors.addError(rowNum, validationError));
        }

        // Stop processing the import if there are unmatched timestamp columns
        if (tsValErrs.size() > 0) {
            this.compensate(context, new MiddlewareError(() -> {
                // any handling...
            }));
        }

        //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
        Map<String, Column<?>> colByPheno = timestampCols.stream().collect(Collectors.toMap(col -> col.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY), col -> col));

        // Add the map to the context for use in processing import
        context.getPendingData().setTimeStampColByPheno(colByPheno);

        try {
            // Fetch the traits named in the observation variable columns
            Program program = context.getImportContext().getProgram();
            List<Trait> traits = observationVariableService.fetchTraitsByName(varNames, program);

            // Sort the traits to match the order of the headers in the import file
            List<Trait> sortedTraits = fileMappingUtil.sortByField(List.copyOf(varNames), new ArrayList<>(traits), TraitEntity::getObservationVariableName);

            // Read any observation data stored for these traits
            log.debug("fetching observation data stored for traits");
            Set<String> ouDbIds = context.getExpUnitContext().getPendingObsUnitByOUId().values().stream().map(u -> u.getBrAPIObject().getObservationUnitDbId()).collect(Collectors.toSet());
            Set<String> varDbIds = sortedTraits.stream().map(t->t.getObservationVariableDbId()).collect(Collectors.toSet());
            List<BrAPIObservation> observations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, varDbIds, program);

            // Construct helper lookup tables to use for hashing observations
            Map<String, String> unitNameByDbId = context.getExpUnitContext().getPendingObsUnitByOUId().values().stream().map(PendingImportObject::getBrAPIObject).collect(Collectors.toMap(BrAPIObservationUnit::getObservationUnitDbId, BrAPIObservationUnit::getObservationUnitName));
            Map<String, String> variableNameByDbId = sortedTraits.stream().collect(Collectors.toMap(Trait::getObservationVariableDbId, Trait::getObservationVariableName));
            Map<String, String> studyNameByDbId = context.getPendingData().getStudyByNameNoScope().values().stream()
                    .filter(pio -> StringUtils.isNotBlank(pio.getBrAPIObject().getStudyDbId()))
                    .map(PendingImportObject::getBrAPIObject)
                    .collect(Collectors.toMap(BrAPIStudy::getStudyDbId, brAPIStudy -> Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIStudy.getStudyName(), program.getKey())));

            // Hash observations using a signature of unit, variable, and study names
            Map<String, BrAPIObservation> observationByObsHash = observations.stream().collect(Collectors.toMap(o->{
                return observationService.getObservationHash(unitNameByDbId.get(o.getObservationUnitDbId()),
                        variableNameByDbId.get(o.getObservationVariableDbId()),
                        studyNameByDbId.get(o.getStudyDbId()));
            }, o->o));

            // Add the observation map to the context for use in processing import
            context.getPendingData().setExistingObsByObsHash(observationByObsHash);
        } catch (DoesNotExistException | ApiException e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }
}
