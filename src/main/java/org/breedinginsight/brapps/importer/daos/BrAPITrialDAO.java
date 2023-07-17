package org.breedinginsight.brapps.importer.daos;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrAPITrialDAO {
    List<BrAPITrial> getTrialsByName(List<String> trialNames, Program program) throws ApiException;

    List<BrAPITrial> createBrAPITrials(List<BrAPITrial> brAPITrialList, UUID programId, ImportUpload upload)
            throws ApiException;

    BrAPITrial updateBrAPITrial(String trialDbId, BrAPITrial trial, UUID programId) throws ApiException;

    List<BrAPITrial> getTrials(UUID programId) throws ApiException;

    Optional<BrAPITrial> getTrialById(UUID programId, UUID trialId) throws ApiException, DoesNotExistException;

    Optional<BrAPITrial> getTrialByDbId(String trialDbId, Program program) throws ApiException;

    List<BrAPITrial> getTrialsByDbIds(Collection<String> trialDbIds, Program program) throws ApiException;

    List<BrAPITrial> getTrialsByExperimentIds(Collection<UUID> experimentIds, Program program) throws ApiException;
}
