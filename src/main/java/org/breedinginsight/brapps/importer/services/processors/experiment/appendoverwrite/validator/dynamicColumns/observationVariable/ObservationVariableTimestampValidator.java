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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.validator.dynamicColumns.observationVariable;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationVariableService;
import org.breedinginsight.services.exceptions.BadRequestException;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.*;

@Slf4j
@Singleton
public class ObservationVariableTimestampValidator implements DynamicObsVarValidator {
    private final ObservationVariableService observationVariableService;

    public ObservationVariableTimestampValidator(ObservationVariableService observationVariableService) {
        this.observationVariableService = observationVariableService;
    }

    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        log.debug("verifying traits listed in import");

        // Get all the phenotypic columns of the import
        ImportUpload upload = ctx.getImportContext().getUpload();
        Table data = ctx.getImportContext().getData();
        List<String> phenotypeColNames = upload
                .getDynamicColumnNamesList()
                .stream()
                .filter(name -> !name.endsWith(OBSERVATION_UNIT_ID_SUFFIX))
                .filter(name -> !name.contains(SUB_UNIT_ID))
                .filter(name -> !name.contains(SUB_OBS_UNIT))
                .collect(Collectors.toList());

        // don't allow periods (.) or square brackets in Phenotype Column Names
        for (String phenotypeColumnName: phenotypeColNames) {
            if(phenotypeColumnName.contains(".") || phenotypeColumnName.contains("[") || phenotypeColumnName.contains("]")){
                String errorMsg = String.format("Observation columns may not contain periods or square brackets (see column '%s')", phenotypeColumnName);
                throw new BadRequestException(errorMsg);
            }
        }
        List<Column<?>> dynamicCols = data.columns(phenotypeColNames.toArray(new String[0]));

        // Collect the columns for observation variable data
        List<Column<?>> phenotypeCols = dynamicCols.stream().filter(col -> !col.name().startsWith(TIMESTAMP_PREFIX)).collect(Collectors.toList());
        List<String> varNames = phenotypeCols.stream().map(Column::name).collect(Collectors.toList());

        // Add the phenotypes to the context for use in processing import
        ctx.getAppendOverwriteWorkflowContext().setPhenotypeCols(phenotypeCols);
        ctx.getAppendOverwriteWorkflowContext().setVarNames(varNames);

        // Collect the columns for observation timestamps
        List<Column<?>> timestampCols = dynamicCols.stream().filter(col -> col.name().startsWith(TIMESTAMP_PREFIX)).collect(Collectors.toList());

        // Construct validation errors for any timestamp columns that don't have a matching variable column
        List<BrAPIImport> importRows = ctx.getImportContext().getImportRows();
        ValidationErrors validationErrors = ctx.getAppendOverwriteWorkflowContext().getValidationErrors();
        List<ValidationError> tsValErrs = observationVariableService
                .validateMatchedTimestamps(Set.copyOf(varNames), timestampCols)
                .orElse(new ArrayList<>());
        for (int i = 0; i < importRows.size(); i++) {
            int rowNum = i;
            tsValErrs.forEach(validationError -> validationErrors.addError(rowNum, validationError));
        }

        if (tsValErrs.isEmpty()) {
            //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
            Map<String, Column<?>> tsColByPheno = timestampCols
                    .stream()
                    .collect(Collectors
                            .toMap(col -> col.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY),
                                    col -> col));

            // Add the map to the context for use in processing import
            ctx.getAppendOverwriteWorkflowContext().setTimeStampColByPheno(tsColByPheno);
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
