package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.ProgramLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class ExpUnitContext {
    private Set<String> referenceOUIds = new HashSet<>();
    private Map<String, PendingImportObject<BrAPITrial>> pendingTrialByOUId = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIStudy>> pendingStudyByOUId = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIObservationUnit>> pendingObsUnitByOUId = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIListDetails>> pendingObsDatasetByOUId = new HashMap<>();
    private Map<String, PendingImportObject<ProgramLocation>> pendingLocationByOUId = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIGermplasm>> pendingGermplasmByOUId = new HashMap<>();

}
