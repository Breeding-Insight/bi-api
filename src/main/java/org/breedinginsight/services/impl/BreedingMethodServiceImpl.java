package org.breedinginsight.services.impl;

import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.services.BreedingMethodService;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.jooq.DSLContext;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Singleton
public class BreedingMethodServiceImpl implements BreedingMethodService {

    private final BreedingMethodDAO breedingMethodDAO;
    private final BrAPIGermplasmService germplasmService;

    private final DSLContext dsl;

    public BreedingMethodServiceImpl(BreedingMethodDAO breedingMethodDAO, BrAPIGermplasmService germplasmService, DSLContext dsl) {
        this.breedingMethodDAO = breedingMethodDAO;
        this.germplasmService = germplasmService;
        this.dsl = dsl;
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
            if(programMethods.containsKey(id)) {
                inUse.put(id, programMethods.get(id));
            } else {
                throw new IllegalStateException("Could not find breeding method by id: " + id);
            }
        });

        return new ArrayList<>(inUse.values());
    }

    @Override
    public ProgramBreedingMethodEntity createBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException {
        validate(breedingMethod, programId);

        return dsl.transactionResult(() -> breedingMethodDAO.createProgramMethod(breedingMethod, programId, userId));
    }

    private boolean methodAlreadyExist(ProgramBreedingMethodEntity breedingMethod, UUID programId) {
        List<ProgramBreedingMethodEntity> programMethods = getBreedingMethods(programId);
        List<ProgramBreedingMethodEntity> systemMethods = getSystemBreedingMethods();
        return isDuplicateMethodFoundAnywhere(breedingMethod, systemMethods, programMethods);
    }

    @Override
    public ProgramBreedingMethodEntity updateBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException, ApiException {
        validate(breedingMethod, programId);

        List<ProgramBreedingMethodEntity> inUseMethods = fetchBreedingMethodsInUse(programId);
        if(inUseMethods.stream().anyMatch(method -> method.getId().equals(breedingMethod.getId()))) {
            throw new BadRequestException("Breeding method is not allowed to be edited");
        }

        return dsl.transactionResult(() -> breedingMethodDAO.updateProgramMethod(breedingMethod, programId, userId));
    }

    private void validate(ProgramBreedingMethodEntity breedingMethod, UUID programId) throws BadRequestException {
        if (isMissingRequiredFields(breedingMethod)) {
            throw new BadRequestException("Missing required data");
        }
        if (methodAlreadyExist(breedingMethod, programId)) {
            throw new BadRequestException(format("A method with name:'%s' or abbreviation:'%s already exist", breedingMethod.getName(), breedingMethod.getAbbreviation()));
        }
    }


    @Override
    public void enableSystemMethods(List<UUID> systemBreedingMethods, UUID programId, UUID userId) throws ApiException, BadRequestException {
        List<ProgramBreedingMethodEntity> inUseMethods = fetchBreedingMethodsInUse(programId);
        if(!systemBreedingMethods.containsAll(inUseMethods.stream().filter(method -> method.getProgramId() == null).map(method -> method.getId()).collect(Collectors.toList()))) {
            throw new BadRequestException("Breeding method is not allowed to be edited");
        }

        Set<UUID> uniqueSystemIds = new HashSet<>(systemBreedingMethods);

        //ensure that a program cannot deactivate a system method if one or more germplasm are using that method
        inUseMethods.forEach(method -> {
            if(method.getProgramId() == null) {
                uniqueSystemIds.add(method.getId());
            }
        });

        dsl.transaction(() -> breedingMethodDAO.enableSystemMethods(new ArrayList<>(uniqueSystemIds), programId, userId));
    }

    @Override
    public void deleteBreedingMethod(UUID programId, UUID breedingMethodId) throws ApiException, BadRequestException {
        List<ProgramBreedingMethodEntity> inUseMethods = fetchBreedingMethodsInUse(programId);
        if(inUseMethods.stream().anyMatch(method -> method.getId().equals(breedingMethodId))) {
            throw new BadRequestException("Breeding method is not allowed to be deleted");
        }

        dsl.transaction(() -> breedingMethodDAO.deleteProgramMethod(programId, breedingMethodId));
    }

     public boolean isMissingRequiredFields(ProgramBreedingMethodEntity method) {
        return StringUtils.isBlank(method.getName())
                || StringUtils.isBlank(method.getAbbreviation())
                || StringUtils.isBlank(method.getCategory())
                || StringUtils.isBlank(method.getGeneticDiversity());
    }

    public boolean isDuplicateMethodFoundAnywhere(ProgramBreedingMethodEntity testMethod, List<ProgramBreedingMethodEntity> systemBreedingMethodEntityList, List<ProgramBreedingMethodEntity> programBreedingMethodEntityList) {
        boolean foundDup = isDuplicateMethodFoundOnList(testMethod, systemBreedingMethodEntityList);
        if (!foundDup && programBreedingMethodEntityList!=null){
            foundDup = isDuplicateMethodFoundOnList(testMethod, programBreedingMethodEntityList);
        }
        return foundDup;
    }

    private boolean isDuplicateMethodFoundOnList(ProgramBreedingMethodEntity testMethod, List<ProgramBreedingMethodEntity> programBreedingMethodEntityList) {
        boolean foundDup = false;
        for (ProgramBreedingMethodEntity method: programBreedingMethodEntityList) {
            if(areMethodsDuplicate(testMethod, method)){
                foundDup = true;
                break;
            }
        }
        return foundDup;
    }


    public boolean areMethodsDuplicate(ProgramBreedingMethodEntity testMethod, ProgramBreedingMethodEntity method) {
        boolean isDup = false;

        if(testMethod.getName()!= null && testMethod.getName().equals(method.getName())){
            isDup = true;
        }
        else if(testMethod.getAbbreviation()!= null && testMethod.getAbbreviation().equals(method.getAbbreviation())){
            isDup = true;
        }
        else if(testMethod.getName()==null && method.getName()==null ||
                testMethod.getAbbreviation()==null && method.getAbbreviation()==null){
            isDup = true;
        }

        return isDup;
    }

}
