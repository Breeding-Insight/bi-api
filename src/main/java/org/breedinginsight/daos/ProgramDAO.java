package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramDAO extends ProgramDao {

    private DSLContext dsl;
    @Inject
    public ProgramDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<Program> get(List<UUID> programIds){
        return getPrograms(programIds);
    }

    public List<Program> get(UUID programId) {
        List<UUID> programList = new ArrayList<>();
        programList.add(programId);
        return getPrograms(programList);
    }

    public List<Program> getFromEntity(List<ProgramEntity> programEntities){
        List<UUID> programList = new ArrayList<>();
        for (ProgramEntity programEntity: programEntities){
            programList.add(programEntity.getId());
        }
        return getPrograms(programList);
    }

    public List<Program> getAll()
    {
        return getPrograms(null);
    }

    private List<Program> getPrograms(List<UUID> programIds){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");
        Result<Record> queryResult;
        List<Program> resultPrograms = new ArrayList<>();

        SelectOnConditionStep<Record> query = dsl.select()
                .from(PROGRAM)
                .join(SPECIES).on(PROGRAM.SPECIES_ID.eq(SPECIES.ID))
                .join(createdByUser).on(PROGRAM.CREATED_BY.eq(createdByUser.ID))
                .join(updatedByUser).on(PROGRAM.UPDATED_BY.eq(updatedByUser.ID));

        if (programIds != null){
            queryResult = query
                    .where(PROGRAM.ID.in(programIds))
                    .fetch();
        } else {
            queryResult = query.fetch();
        }

        // Parse the result
        for (Record record: queryResult){
            Program program = Program.parseSQLRecord(record);
            program.setSpecies(Species.parseSQLRecord(record));
            program.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
            program.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
            resultPrograms.add(program);
        }

        return resultPrograms;
    }
}

