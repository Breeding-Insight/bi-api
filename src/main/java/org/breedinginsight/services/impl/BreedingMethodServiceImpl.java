package org.breedinginsight.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.services.BreedingMethodService;
import org.breedinginsight.services.exceptions.BadRequestException;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class BreedingMethodServiceImpl implements BreedingMethodService {

    private final BreedingMethodDAO breedingMethodDAO;
    private final BrAPIGermplasmService germplasmService;

    public BreedingMethodServiceImpl(BreedingMethodDAO breedingMethodDAO, BrAPIGermplasmService germplasmService) {
        this.breedingMethodDAO = breedingMethodDAO;
        this.germplasmService = germplasmService;
    }

    @Override
    public List<ProgramBreedingMethodEntity> getBreedingMethods(UUID programId) {
        return breedingMethodDAO.getProgramBreedingMethods(programId);
    }

    @Override
    public List<ProgramBreedingMethodEntity> getSystemBreedingMethods() {
        return breedingMethodDAO.getSystemBreedingMethods();
    }

    @Override
    public List<ProgramBreedingMethodEntity> fetchBreedingMethodsInUse(UUID programId) throws ApiException {
        Map<UUID, ProgramBreedingMethodEntity> inUse = new HashMap<>();
        Map<UUID, ProgramBreedingMethodEntity> programMethods = breedingMethodDAO.getProgramBreedingMethods(programId)
                                                                                 .stream()
                                                                                 .collect(Collectors.toMap(ProgramBreedingMethodEntity::getId, o -> o));

        //TODO retest with new germplasm after updating the DAO to return the correct ID for a method
        germplasmService.getGermplasm(programId).forEach(germplasm -> {
            UUID id = UUID.fromString(germplasm.getBreedingMethodDbId());
            if(!inUse.containsKey(id)) {
                inUse.put(id, programMethods.get(id));
            }
        });

        return new ArrayList<>(inUse.values());
    }

    @Override
    public ProgramBreedingMethodEntity createBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException, ApiException {
        if(!validateBreedingMethod(breedingMethod)) {
            throw new BadRequestException("Missing required data");
        }

        return breedingMethodDAO.createProgramMethod(breedingMethod, programId, userId);
    }

    @Override
    public ProgramBreedingMethodEntity updateBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException, ApiException {
        if(!validateBreedingMethod(breedingMethod)) {
            throw new BadRequestException("Missing required data");
        }

        List<ProgramBreedingMethodEntity> inUseMethods = fetchBreedingMethodsInUse(programId);
        if(inUseMethods.stream().anyMatch(method -> method.getId().equals(breedingMethod.getId()))) {
            throw new BadRequestException("Breeding method is not allowed to be edited");
        }

        return breedingMethodDAO.updateProgramMethod(breedingMethod, programId, userId);
    }

    @Override
    public void enableSystemMethods(List<UUID> systemBreedingMethods, UUID programId, UUID userId) throws ApiException {
        List<ProgramBreedingMethodEntity> inUseMethods = fetchBreedingMethodsInUse(programId);

        Set<UUID> uniqueSystemIds = new HashSet<>(systemBreedingMethods);

        //ensure that a program cannot deactivate a system method if one or more germplasm are using that method
        inUseMethods.forEach(method -> {
            if(method.getProgramId() == null) {
                uniqueSystemIds.add(method.getId());
            }
        });

        breedingMethodDAO.enableSystemMethods(new ArrayList<>(uniqueSystemIds), programId, userId);
    }

    private boolean validateBreedingMethod(ProgramBreedingMethodEntity method) {
        return StringUtils.isNotBlank(method.getName())
                && StringUtils.isNotBlank(method.getAbbreviation())
                && StringUtils.isNotBlank(method.getCategory())
                && StringUtils.isNotBlank(method.getGeneticDiversity());
    }
}
