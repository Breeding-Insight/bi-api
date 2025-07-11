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
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.services.exceptions.BadRequestException;
import tech.tablesaw.columns.Column;
import java.util.regex.Pattern;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.BITB;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.OZEX;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;

import javax.inject.Singleton;

@Slf4j
@Singleton
public class ObservationUnitIDFormatValidator implements DynamicObsUnitValidator {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        // Skip this validation if the observation units have already been fetched from the BrAPI service
        if (!ctx.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().isEmpty()) return;

        if (ctx.getAppendOverwriteWorkflowContext().getObsUnitColName() == null) {
            throw new BadRequestException(OZEX.getValue());
        }

        ValidationErrors rowErrors = ctx.getAppendOverwriteWorkflowContext().getValidationErrors();
        String idColName = ctx.getAppendOverwriteWorkflowContext().getObsUnitColName();
        Column<?> idCol = ctx.getImportContext().getData().column(idColName);

        for (int rowNum = 0; rowNum < ctx.getImportContext().getImportRows().size(); rowNum++) {
            Object value = idCol.get(rowNum);
            String id = value != null ? value.toString() : null;

            // Validate UUID format
            if (id == null || !UUID_PATTERN.matcher(id).matches()) {
                if (!rowErrors.hasErrorAtCell(rowNum + 2, idColName)) { // take header row into account
                    // don't add another error for format if it already has an error for being blank
                    ExperimentUtilities.addRowError(idColName, BITB.getValue(), rowErrors, rowNum);
                }
            }
        }
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
