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

import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.columns.Column;

import javax.inject.Singleton;

import java.util.List;
import java.util.Map;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.OZEX;
import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.PJZH;

@Slf4j
@Singleton
public class ObservationUnitSingleDatasetValidator implements DynamicObsUnitValidator {
    private final String referenceSourceBase;

    public ObservationUnitSingleDatasetValidator(@Property(name = "brapi.server.reference-source") String referenceSourceBase) {
        this.referenceSourceBase = referenceSourceBase;
    }


    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        // Skip this validation if the observation units have not been fetched from the BrAPI service
        if (ctx.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId().isEmpty()) return;

        if (ctx.getAppendOverwriteWorkflowContext().getObsUnitColName() == null) {
            throw new BadRequestException(OZEX.getValue());
        }

        String idColName = ctx.getAppendOverwriteWorkflowContext().getObsUnitColName();
        Column<?> idCol = ctx.getImportContext().getData().columns(idColName).get(0);
        Map<String, PendingImportObject<BrAPIObservationUnit>> unitPioCache = ctx.getAppendOverwriteWorkflowContext().getPendingObsUnitByOUId();
        String singleDatasetId = null;
        for (int rowNum = 0; rowNum < ctx.getImportContext().getImportRows().size(); rowNum++) {
            // Get the external references for the  BrAPI Observation Unit stored with the given id
            List<BrAPIExternalReference> refs = unitPioCache
                    .get(idCol.get(rowNum).toString())
                    .getBrAPIObject()
                    .getExternalReferences();

            // Find the id of the dataset that owns the given observation unit
            String datasetId = Utilities
                    .getExternalReference(refs, referenceSourceBase, ExternalReferenceSource.DATASET)
                    .map(BrAPIExternalReference::getReferenceId)
                    .orElseThrow(() -> new BadRequestException(PJZH.getValue()));

            // Make sure there is only a single unique dataset used in the import
            if (singleDatasetId == null) {
                singleDatasetId = datasetId;
            } else if (!singleDatasetId.equals(datasetId)) {
                throw new BadRequestException(PJZH.getValue());
            }
        }

    }

    @Override
    public int getOrder() {
        return 5;
    }
}
