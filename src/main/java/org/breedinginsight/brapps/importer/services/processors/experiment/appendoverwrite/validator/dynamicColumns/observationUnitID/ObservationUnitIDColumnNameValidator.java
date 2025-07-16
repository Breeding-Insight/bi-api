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
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.services.exceptions.BadRequestException;

import javax.inject.Singleton;
import java.util.Arrays;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.OZEX;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.OBSERVATION_UNIT_ID_SUFFIX;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.SUB_UNIT_ID;

@Slf4j
@Singleton
public class ObservationUnitIDColumnNameValidator implements DynamicObsUnitValidator {

    public ObservationUnitIDColumnNameValidator() {}

    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        // Skip this validation if it has already been successfully completed
        if (ctx.getAppendOverwriteWorkflowContext().getObsUnitColName() != null) return;

        // Skip this validation if the observation units have already been fetched from the BrAPI service
        if (!ctx.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().isEmpty()) return;

        // Get the names of all the dynamic columns with observation unit ids
        String[] idColNames = Arrays.stream(ctx.getImportContext().getUpload().getDynamicColumnNames())
                .filter(name->name.endsWith(OBSERVATION_UNIT_ID_SUFFIX)).toArray(String[]::new);

        // throw an error if there are no obs unit id columns in the import
        int idColCount = idColNames.length;
        if (idColCount == 0) throw new BadRequestException(OZEX.getValue());

        // count the number of columns and throw an error if the count is neither 1 and unique nor 2 with 1 unique and 1 non-unique
        if (!(idColCount == 1 || idColCount == 2)) throw new BadRequestException(OZEX.getValue());

        if (idColCount == 2) {
            // if sub-entity ids in import then check for presence of sub-unit # column
            Arrays.stream(ctx.getImportContext().getUpload().getDynamicColumnNames())
                    .filter(name-> name.equals(SUB_UNIT_ID))
                    .findAny()
                    .orElseThrow(()->new BadRequestException(OZEX.getValue()));

            // check that if there is a column with unique ids, it is the right-most column
            boolean leftIsUnique = ExperimentUtilities.hasUniqueIds(ctx, idColNames[0]);
            boolean rightIsUnique = ExperimentUtilities.hasUniqueIds(ctx, idColNames[1]);
            if (leftIsUnique && !rightIsUnique) throw new BadRequestException(OZEX.getValue());

            // the right column should be the most nested level and this column is to be used for processing the import
            ctx.getAppendOverwriteWorkflowContext().setObsUnitColName(idColNames[1]);
        } else if (idColCount == 1) {

            // there is only one top level whose column is used for processing the import
            ctx.getAppendOverwriteWorkflowContext().setObsUnitColName(idColNames[0]);
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
