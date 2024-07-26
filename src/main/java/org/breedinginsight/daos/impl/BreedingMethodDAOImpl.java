package org.breedinginsight.daos.impl;

import org.breedinginsight.dao.db.tables.daos.BreedingMethodDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.PROGRAM_BREEDING_METHOD;
import static org.breedinginsight.dao.db.Tables.PROGRAM_ENABLED_BREEDING_METHODS;
import static org.breedinginsight.dao.db.tables.BreedingMethodTable.BREEDING_METHOD;

@Singleton
public class BreedingMethodDAOImpl extends BreedingMethodDao implements BreedingMethodDAO {

    private DSLContext dsl;

    @Inject
    public BreedingMethodDAOImpl(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    @Override
    public List<ProgramBreedingMethodEntity> getProgramBreedingMethods(UUID programId) {
        List<ProgramBreedingMethodEntity> breedingMethodEntities = systemMethodBase(programId)
                                                                      .fetchInto(ProgramBreedingMethodEntity.class);

        List<ProgramBreedingMethodEntity> programBreedingMethodEntities = programMethodBase(programId)
                                                                             .fetchInto(ProgramBreedingMethodEntity.class);

        List<ProgramBreedingMethodEntity> ret = new ArrayList<>();
        if(!breedingMethodEntities.isEmpty()) {
            ret.addAll(breedingMethodEntities);
        }
        if(!programBreedingMethodEntities.isEmpty()) {
            ret.addAll(programBreedingMethodEntities);
        }
        return ret;
    }

    @Override
    public List<ProgramBreedingMethodEntity> getSystemBreedingMethods() {
        return dsl.select(BREEDING_METHOD.fields())
                  .from(BREEDING_METHOD)
                  .fetchInto(ProgramBreedingMethodEntity.class);
    }

    public List<ProgramBreedingMethodEntity> findByNameOrAbbreviation(String nameOrAbbrev, UUID programId) {
        List<ProgramBreedingMethodEntity> breedingMethodEntities = systemMethodBase(programId)
                                                                      .and(BREEDING_METHOD.ABBREVIATION.equalIgnoreCase(nameOrAbbrev)
                                                                                                       .or(BREEDING_METHOD.NAME.equalIgnoreCase(nameOrAbbrev)))
                                                                      .fetchInto(ProgramBreedingMethodEntity.class);

        List<ProgramBreedingMethodEntity> programBreedingMethodEntities = programMethodBase(programId)
                                                                             .and(PROGRAM_BREEDING_METHOD.ABBREVIATION.equalIgnoreCase(nameOrAbbrev)
                                                                                                                      .or(PROGRAM_BREEDING_METHOD.NAME.equalIgnoreCase(nameOrAbbrev)))
                                                                             .fetchInto(ProgramBreedingMethodEntity.class);

        List<ProgramBreedingMethodEntity> ret = new ArrayList<>();
        if(!breedingMethodEntities.isEmpty()) {
            ret.addAll(breedingMethodEntities);
        }
        if(!programBreedingMethodEntities.isEmpty()) {
            ret.addAll(programBreedingMethodEntities);
        }
        return ret;
    }

    @Override
    public void enableAllSystemMethods(UUID programId, UUID userId) {
        dsl.execute("insert into program_enabled_breeding_methods(breeding_method_id, program_id, created_by, created_at, updated_by, updated_at)\n" +
                            "select breeding_method.id, ?, ?, now(), ?, now() from breeding_method", programId, userId, userId);
    }

    @Override
    public void enableSystemMethods(List<UUID> systemBreedingMethods, UUID programId, UUID userId) {
        dsl.transaction(() -> {
            dsl.deleteFrom(PROGRAM_ENABLED_BREEDING_METHODS).where(PROGRAM_ENABLED_BREEDING_METHODS.PROGRAM_ID.eq(programId)).execute();

            for(UUID methodId : systemBreedingMethods) {
                dsl.insertInto(PROGRAM_ENABLED_BREEDING_METHODS)
                   .columns(PROGRAM_ENABLED_BREEDING_METHODS.BREEDING_METHOD_ID,
                            PROGRAM_ENABLED_BREEDING_METHODS.PROGRAM_ID,
                            PROGRAM_ENABLED_BREEDING_METHODS.CREATED_BY,
                            PROGRAM_ENABLED_BREEDING_METHODS.CREATED_AT,
                            PROGRAM_ENABLED_BREEDING_METHODS.UPDATED_BY,
                            PROGRAM_ENABLED_BREEDING_METHODS.UPDATED_AT)
                   .values(methodId, programId, userId, OffsetDateTime.now(), userId, OffsetDateTime.now())
                   .execute();
            }
        });
    }

    @Override
    public ProgramBreedingMethodEntity createProgramMethod(ProgramBreedingMethodEntity method, UUID programId, UUID userId) {
        return dsl.insertInto(PROGRAM_BREEDING_METHOD)
           .columns(PROGRAM_BREEDING_METHOD.NAME,
                    PROGRAM_BREEDING_METHOD.ABBREVIATION,
                    PROGRAM_BREEDING_METHOD.DESCRIPTION,
                    PROGRAM_BREEDING_METHOD.CATEGORY,
                    PROGRAM_BREEDING_METHOD.GENETIC_DIVERSITY,
                    PROGRAM_BREEDING_METHOD.PROGRAM_ID,
                    PROGRAM_BREEDING_METHOD.CREATED_BY,
                    PROGRAM_BREEDING_METHOD.CREATED_AT,
                    PROGRAM_BREEDING_METHOD.UPDATED_BY,
                    PROGRAM_BREEDING_METHOD.UPDATED_AT)
           .values(method.getName(), method.getAbbreviation(), method.getDescription(), method.getCategory(), method.getGeneticDiversity(), programId, userId, OffsetDateTime.now(), userId, OffsetDateTime.now())
                .returning(PROGRAM_BREEDING_METHOD.fields())
                .fetchOneInto(ProgramBreedingMethodEntity.class);
    }

    @Override
    public ProgramBreedingMethodEntity updateProgramMethod(ProgramBreedingMethodEntity method, UUID programId, UUID userId) {
        return dsl.update(PROGRAM_BREEDING_METHOD)
                  .set(PROGRAM_BREEDING_METHOD.NAME, method.getName())
                  .set(PROGRAM_BREEDING_METHOD.ABBREVIATION, method.getAbbreviation())
                  .set(PROGRAM_BREEDING_METHOD.DESCRIPTION, method.getDescription())
                  .set(PROGRAM_BREEDING_METHOD.CATEGORY, method.getCategory())
                  .set(PROGRAM_BREEDING_METHOD.GENETIC_DIVERSITY, method.getGeneticDiversity())
                  .set(PROGRAM_BREEDING_METHOD.UPDATED_BY, userId)
                  .set(PROGRAM_BREEDING_METHOD.UPDATED_AT, OffsetDateTime.now())
                  .where(PROGRAM_BREEDING_METHOD.ID.eq(method.getId()))
                  .returning(PROGRAM_BREEDING_METHOD.fields())
                  .fetchOneInto(ProgramBreedingMethodEntity.class);
    }

    @Override
    public void deleteProgramMethod(UUID programId, UUID breedingMethodId) {
        dsl.deleteFrom(PROGRAM_BREEDING_METHOD)
           .where(PROGRAM_BREEDING_METHOD.ID.eq(breedingMethodId))
           .and(PROGRAM_BREEDING_METHOD.PROGRAM_ID.eq(programId)).execute();
    }

    private SelectConditionStep<Record> systemMethodBase(UUID programId) {
        return dsl.select(BREEDING_METHOD.fields())
                  .from(BREEDING_METHOD)
                  .join(PROGRAM_ENABLED_BREEDING_METHODS)
                  .on(PROGRAM_ENABLED_BREEDING_METHODS.BREEDING_METHOD_ID.eq(BREEDING_METHOD.ID))
                  .where(PROGRAM_ENABLED_BREEDING_METHODS.PROGRAM_ID.eq(programId));
    }

    private SelectConditionStep<Record> programMethodBase(UUID programId) {
        return dsl.select(PROGRAM_BREEDING_METHOD.fields())
                  .from(PROGRAM_BREEDING_METHOD)
                  .where(PROGRAM_BREEDING_METHOD.PROGRAM_ID.eq(programId));
    }
}
