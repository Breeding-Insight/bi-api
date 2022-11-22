package org.breedinginsight.services;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.services.exceptions.BadRequestException;

import java.util.List;
import java.util.UUID;

public interface BreedingMethodService {

    List<ProgramBreedingMethodEntity> getBreedingMethods(UUID programId);

    List<ProgramBreedingMethodEntity> getSystemBreedingMethods();

    List<ProgramBreedingMethodEntity> fetchBreedingMethodsInUse(UUID programId) throws ApiException;

    ProgramBreedingMethodEntity createBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException, ApiException;

    ProgramBreedingMethodEntity updateBreedingMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID userId) throws BadRequestException, ApiException;

    void enableSystemMethods(List<UUID> systemBreedingMethods, UUID programId, UUID userId) throws ApiException;
}
