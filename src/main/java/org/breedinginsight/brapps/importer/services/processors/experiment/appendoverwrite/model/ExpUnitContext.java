package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.AppendStatistic;
import org.breedinginsight.model.ProgramLocation;
import tech.tablesaw.columns.Column;

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

    // Processing statistics
    private AppendStatistic statistic;

    // Carry over from PendingData
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope;
    private Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope;
    private Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope;
    private Map<String, PendingImportObject<ProgramLocation>> locationByName;
    private Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName;
    private Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID;
    private Map<String, PendingImportObject<BrAPIObservation>> pendingObservationByHash;
    private Map<String, Column<?>> timeStampColByPheno;
    private Map<String, BrAPIObservation> existingObsByObsHash;
    private ValidationErrors validationErrors;
}
