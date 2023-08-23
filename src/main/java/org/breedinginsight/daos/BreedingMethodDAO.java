package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;

import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Singleton
public interface BreedingMethodDAO {

    List<ProgramBreedingMethodEntity> getProgramBreedingMethods(UUID programId);

    List<ProgramBreedingMethodEntity> getSystemBreedingMethods();

    List<ProgramBreedingMethodEntity> findByNameOrAbbreviation(String nameOrAbbrev, UUID programId);

    void enableAllSystemMethods(UUID programId, UUID userId);

    void enableSystemMethods(List<UUID> systemBreedingMethods, UUID programId, UUID userId);

    ProgramBreedingMethodEntity createProgramMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID id);

    ProgramBreedingMethodEntity updateProgramMethod(ProgramBreedingMethodEntity breedingMethod, UUID programId, UUID id);

    void deleteProgramMethod(UUID programId, UUID breedingMethodId);
}
