package org.breedinginsight.brapps.importer.services.processors.experiment.create.model;

import lombok.*;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.ProgramLocation;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class PendingData {
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope;
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope;
    private Map<String, PendingImportObject<ProgramLocation>> locationByName;
    private Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName;
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID;
}
