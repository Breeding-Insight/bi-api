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
package org.breedinginsight.brapps.importer.services.processors.experiment.create.model;

import lombok.*;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.ProgramLocation;
import tech.tablesaw.columns.Column;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class PendingData {
    //NOTE: populated in populate existing and new steps depending on import
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope;
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope;
    private Map<String, PendingImportObject<ProgramLocation>> locationByName;
    private Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName;
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID;

    private Map<String, PendingImportObject<BrAPIObservation>> pendingObservationByHash;
    private Map<String, Column<?>> timeStampColByPheno;
    private ValidationErrors validationErrors;


    // TODO: see if we can change this to match PendingImport<>
    private Map<String, BrAPIObservation> existingObsByObsHash;

    // NOTE: populated in populate new step
    private Map<String, PendingImportObject<BrAPIObservation>> observationByHash;
}

