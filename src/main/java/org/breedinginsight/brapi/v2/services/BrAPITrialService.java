package org.breedinginsight.brapi.v2.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class BrAPITrialService {

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    private final BrAPITrialDAO trialDAO;
    private final BrAPIObservationUnitDAO ouDAO;
    private final ProgramService programService;

    @Inject
    public BrAPITrialService(ProgramService programService, BrAPITrialDAO trialDAO,BrAPIObservationUnitDAO ouDAO) {
        this.programService = programService;
        this.trialDAO = trialDAO;
        this.ouDAO = ouDAO;
    }

    public List<BrAPITrial> getExperiments(UUID programId) throws ApiException, DoesNotExistException {
        return trialDAO.getTrials(programId);
    }

    public BrAPITrial getTrialByUUID(UUID programId, UUID trialId, boolean stats) throws DoesNotExistException {
        try {
            return trialDAO.getTrialByDbId(programId,trialId).get();
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    public HashMap<String, Object> getTrialDataByUUID(UUID programId, UUID trialId, boolean stats) throws DoesNotExistException {
        HashMap<String, Object> trialData = new HashMap<>(3);
        try {
            trialData.put("trialData", trialDAO.getTrialByDbId(programId,trialId).get());
            if( stats ){
                int environmentsCount = 1; // For now this is hardcoded to 1, because we are only supporting one environment per experiment
                //TODO this should not be hardcoded
                long germplasmCount = countGermplasms(programId, trialId);
                trialData.put("environmentsCount", environmentsCount);
                trialData.put("germplasmCount", germplasmCount);
            }
            return trialData;
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    private long countGermplasms(UUID programId, UUID trialId) throws ApiException, DoesNotExistException{
//        trialId= UUID.fromString("1bfbf7c3-8394-45ab-9021-21f9affadaeb");
        BrAPITrial brAPItrial = trialDAO.getTrialByDbId(programId,trialId).get();
        List<BrAPIObservationUnit> obUnits = ouDAO.getObservationUnitsForTrialDbId(programId, brAPItrial.getTrialDbId());
log.info(",.,.,,.,.,.trait ID,.,.,.,.,.,,,");
        log.info(brAPItrial.getTrialDbId());
        log.info(".......gems.......");
        obUnits.stream().map(ou->ou.getGermplasmDbId()).forEach(s->log.info(s));

        return obUnits.stream().map(ou->ou.getGermplasmDbId()).distinct().count();
    }
}
