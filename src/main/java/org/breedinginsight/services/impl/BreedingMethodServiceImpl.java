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


    @Override
    public ProgramBreedingMethodEntity updateBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException, ApiException {
        List<ProgramBreedingMethodEntity> inUseMethods = fetchBreedingMethodsInUse(programId);
        if(inUseMethods.stream().anyMatch(method -> method.getId().equals(breedingMethod.getId()))) {
            throw new BadRequestException("Breeding method is not allowed to be edited");
        }
        validate(breedingMethod, programId);

        return dsl.transactionResult(() -> breedingMethodDAO.updateProgramMethod(breedingMethod, programId, userId));
    }

    private void validate(ProgramBreedingMethodEntity breedingMethod, UUID programId) throws BadRequestException {
        if (isMissingRequiredFields(breedingMethod)) {
            throw new BadRequestException("Missing required data");
        }

        List<ProgramBreedingMethodEntity> programAndSystemMethods = getBreedingMethods(programId);
        if( isDuplicateMethodNameFoundOnList(breedingMethod, programAndSystemMethods)){
            throw new BadRequestException(format("'%s' is already defined in the system.", breedingMethod.getName()));
        }
        if( isDuplicateMethodAbbreviationFoundOnList(breedingMethod, programAndSystemMethods)){
            throw new BadRequestException(format("'%s' is already defined in the system.", breedingMethod.getAbbreviation()));
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

     boolean isMissingRequiredFields(ProgramBreedingMethodEntity method) {
        return StringUtils.isBlank(method.getName())
                || StringUtils.isBlank(method.getAbbreviation())
                || StringUtils.isBlank(method.getCategory())
                || StringUtils.isBlank(method.getGeneticDiversity());
    }
    boolean isDuplicateMethodNameFoundOnList(ProgramBreedingMethodEntity method, List<ProgramBreedingMethodEntity> programBreedingMethodEntityList) {
        boolean foundDup = false;
        for (ProgramBreedingMethodEntity testMethod: programBreedingMethodEntityList) {
            if(isDuplicateName(testMethod, method)){
                foundDup = true;
                break;
            }
        }
        return foundDup;
    }

    boolean isDuplicateMethodAbbreviationFoundOnList(ProgramBreedingMethodEntity method, List<ProgramBreedingMethodEntity> programBreedingMethodEntityList) {
        boolean foundDup = false;
        for (ProgramBreedingMethodEntity testMethod: programBreedingMethodEntityList) {
            if(isDuplicateAbbreviation(testMethod, method)){
                foundDup = true;
                break;
            }
        }
        return foundDup;
    }

    boolean isDuplicateName(ProgramBreedingMethodEntity testMethod, ProgramBreedingMethodEntity method) {
        // SPECIAL CASE: If the two methods are the same method, then they are not duplicates
        if( (testMethod.getId()!=null) && testMethod.getId().equals(method.getId()) ){
            return false;
        }

        boolean isDup = false;
        if(testMethod.getName()!= null && testMethod.getName().equalsIgnoreCase(method.getName())){
            isDup = true;
        }
        else if(testMethod.getName()==null && method.getName()==null ){
            isDup = true;
        }
        return isDup;
    }

    boolean isDuplicateAbbreviation(ProgramBreedingMethodEntity testMethod, ProgramBreedingMethodEntity method) {
        // SPECIAL CASE: If the two methods are the same method, then they are not duplicates
        if( (testMethod.getId()!=null) && testMethod.getId().equals(method.getId()) ){
            return false;
        }

        boolean isDup = false;
        if(testMethod.getAbbreviation()!= null && testMethod.getAbbreviation().equalsIgnoreCase(method.getAbbreviation())){
            isDup = true;
        }
        else if(testMethod.getAbbreviation()==null && method.getAbbreviation()==null ){
            isDup = true;
        }
        return isDup;
    }

}
