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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.validator.dynamicColumns.observationUnitID;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.services.exceptions.BadRequestException;
import tech.tablesaw.columns.Column;

import javax.inject.Singleton;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.BITB;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.OZEX;

@Slf4j
@Singleton
public class ObservationUnitIDBlankValidator implements DynamicObsUnitValidator {
    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        // Skip this validation if the observation units have already been fetched from the BrAPI service
        if (!ctx.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().isEmpty()) return;

        if (ctx.getAppendOverwriteWorkflowContext().getObsUnitColName() == null) {
            throw new BadRequestException(OZEX.getValue());
        }

        ValidationErrors rowErrors = ctx.getAppendOverwriteWorkflowContext().getValidationErrors();
        String idColName = ctx.getAppendOverwriteWorkflowContext().getObsUnitColName();
        Column<?> idCol = ctx.getImportContext().getData().columns(idColName).get(0);

        for (int rowNum = 0; rowNum < ctx.getImportContext().getImportRows().size(); rowNum++) {
            Object cellValue = idCol.get(rowNum);
            String id = (cellValue != null) ? cellValue.toString() : null;
            if ( id == null || id.isBlank()) {
                // Check if ObsUnitID is blank
                ExperimentUtilities.addRowError(idColName, BITB.getValue(), rowErrors, rowNum);
            }
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
