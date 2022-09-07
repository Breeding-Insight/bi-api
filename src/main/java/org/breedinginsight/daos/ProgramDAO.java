package org.breedinginsight.daos;

import org.brapi.client.v2.BrAPIClient;
import org.brapi.v2.model.core.BrAPIProgram;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.records.ProgramRecord;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.jooq.DAO;

import java.util.List;
import java.util.UUID;

public interface ProgramDAO extends DAO<ProgramRecord, ProgramEntity, UUID> {
    List<Program> get(List<UUID> programIds);

    List<Program> get(UUID programId);

    List<Program> getFromEntity(List<ProgramEntity> programEntities);

    List<Program> getAll();

    List<Program> getActive();

    List<Program> getProgramByName(String name, boolean caseInsensitive);

    List<Program> getProgramByKey(String key);

    int getNumProgramUsers(UUID programId);

    ProgramBrAPIEndpoints getProgramBrAPIEndpoints(UUID programId);

    ProgramEntity fetchOneById(UUID programId);

    List<ProgramEntity> fetchById(UUID... values);

    boolean brapiUrlSupported(String brapiUrl);

    BrAPIProgram getProgramBrAPI(Program program);

    void createProgramBrAPI(Program program);

    void updateProgramBrAPI(Program program);

    BrAPIClient getCoreClient(UUID programId);

    BrAPIClient getPhenoClient(UUID programId);
}
