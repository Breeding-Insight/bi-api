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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.DatasetService;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.JABH;

@Slf4j
@Singleton
public class ObservationVariablePriorDatasetValidator implements DynamicObsVarValidator {
    private final String referenceSourceBase;
    private final DatasetService datasetService;

    public ObservationVariablePriorDatasetValidator(
            @Property(name = "brapi.server.reference-source") String referenceSourceBase,
            DatasetService datasetService) {
        this.referenceSourceBase = referenceSourceBase;
        this.datasetService = datasetService;
    }

    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx) throws BadRequestException, ApiException {
        // Skip the validation if the dependencies have not been fetched from the BrAPI service
        if(noMappings(ctx)) return;

        // Get the dataset id used for this import
        String datasetId = getImportDatasetId(ctx);

        // Get the ids for the other datasets in the same experiment
        Set<String> otherIds = getExperimentDatasetIds(ctx);
        otherIds.remove(datasetId);

        // Get the names of any observation variables owned by the other datasets
        Set<String> forbiddenVariables = getDatasetVariables(ctx, otherIds);

        // Check that no phenotype name used in the import already belongs to another dataset
        if (forbidden(ctx, forbiddenVariables)) {
            throw new BadRequestException(JABH.getValue());
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }

    private Set<String> getDatasetVariables(AppendOverwriteMiddlewareContext ctx, Set<String> ids) throws ApiException {
        Set<String> variables = new HashSet<>();
        String progKey = ctx.getImportContext().getProgram().getKey();
        List<BrAPIListDetails> varListDetails = datasetService
                .fetchDatasetsByIds(ids, ctx.getImportContext().getProgram()).orElse(new ArrayList<>());
        for (BrAPIListDetails brAPIListDetails : varListDetails) {
            List<String> priorVariablesNoScope = brAPIListDetails
                    .getData()
                    .stream()
                    .map((scopedVariable) -> Utilities.removeProgramKey(scopedVariable, progKey))
                    .collect(Collectors.toList());
            variables.addAll(priorVariablesNoScope);
        }

        return variables;
    }

    private boolean forbidden(AppendOverwriteMiddlewareContext ctx, Set<String> forbiddenVariables) {
        List<String> importVarNames = ctx.getAppendOverwriteWorkflowContext().getVarNames();
        for (String importVarName : importVarNames) {
            if (forbiddenVariables.contains(importVarName)) return true;
        }

        return false;
    }

    private Set<String> getExperimentDatasetIds(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        JsonArray expDatasets = ctx.getAppendOverwriteWorkflowContext()
                .getPendingTrialByOUId()
                .values()
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No pending trial found"))
                .getBrAPIObject()
                .getAdditionalInfo()
                .getAsJsonArray("datasets");
        Set<String> datasetIds = new HashSet<>();
        for (JsonElement expDataset : expDatasets) {
            String datasetId = expDataset.getAsJsonObject().get("id").getAsString();
            datasetIds.add(datasetId);
        }

        return datasetIds;
    }

    private String getImportDatasetId(AppendOverwriteMiddlewareContext ctx) throws BadRequestException {
        List<BrAPIExternalReference> refs = ctx.getAppendOverwriteWorkflowContext()
                .getPendingObsUnitByOUId()
                .values()
                .stream()
                .findFirst()
                .orElseThrow(()->new BadRequestException("No pending obs unit found"))
                .getBrAPIObject()
                .getExternalReferences();

        return Utilities
                .getExternalReference(refs, referenceSourceBase, ExternalReferenceSource.DATASET)
                .map(BrAPIExternalReference::getReferenceId)
                .orElseThrow(() -> new BadRequestException("No dataset associated with observation unit"));
    }

    private boolean noMappings(AppendOverwriteMiddlewareContext context) {
        Map<String, PendingImportObject<BrAPITrial>> trialByOUId = context
                .getAppendOverwriteWorkflowContext()
                .getPendingTrialByOUId();
        Map<String, PendingImportObject<BrAPIListDetails>> datasetByOUId = context
                .getAppendOverwriteWorkflowContext()
                .getPendingObsDatasetByOUId();

        if (trialByOUId == null) return true;
        if (trialByOUId.isEmpty()) return true;
        if (datasetByOUId == null) return true;
        return false;
    }
}
