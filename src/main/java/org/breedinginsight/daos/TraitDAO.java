package org.breedinginsight.daos;

import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.dao.db.tables.records.TraitRecord;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import org.jooq.DAO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TraitDAO extends DAO<TraitRecord, TraitEntity, UUID> {
    List<Trait> getTraitsFullByProgramId(UUID programId);

    List<Trait> getTraitsFullByProgramIds(List<UUID> programIds);

    List<Trait> getTraitsByProgramId(UUID programId);

    List<Trait> getTraitsByProgramIds(UUID... programIds);

    List<Trait> getTraitsById(UUID... traitIds);

    Optional<Trait> getTraitFull(UUID programId, UUID traitId);

    // could be more efficient to do a single get instead of search in saved search case but less code this way
    // and search stuff is working in breedbase

    List<BrAPIObservation> getObservationsForTrait(UUID traitId, UUID programId);

    List<BrAPIObservation> getObservationsForTraits(List<UUID> traitIds, UUID programId);

    List<BrAPIObservation> getObservationsForTraitsByBrAPIProgram(String brapiProgramId, UUID programId, List<UUID> traitIds);

    List<BrAPIObservationVariable> searchVariables(List<String> variableIds, UUID programId);

    Optional<Trait> getTrait(UUID programId, UUID traitId);

    List<Trait> createTraitsBrAPI(List<Trait> traits, User actingUser, Program program);

    Trait updateTraitBrAPI(Trait trait, Program program);

    List<Trait> getTraitsByTraitName(UUID programId, List<Trait> traits);

    List<Trait> getTraitsByAbbreviation(UUID programId, List<String> abbreviations);

    TraitEntity fetchOneById(UUID id);

    List<TraitEntity> fetchById(UUID traitId);
}
