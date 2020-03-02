package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.dao.db.tables.records.ProgramRecord;
import org.breedinginsight.model.Program;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramDao extends org.breedinginsight.dao.db.tables.daos.ProgramDao {
    @Inject
    DSLContext dsl;
    @Inject
    public ProgramDao(Configuration config) {
        super(config);
    }

    public List<Program> getAll(UUID programId){

        Result<Record> queryResults = dsl.select()
            .from(PROGRAM)
            .join(SPECIES).on(PROGRAM.SPECIES_ID.eq(SPECIES.ID))
            .leftJoin(BI_USER).on(PROGRAM.CREATED_BY.eq(BI_USER.ID))
            .leftJoin(BI_USER).on(PROGRAM.UPDATED_BY.eq(BI_USER.ID))
            .where(PROGRAM.ID.eq(programId))
            .fetch();

        for (Record record: queryResults){

            // Generate our program record
            Program program = new Program();
            program.setId(record.getValue(PROGRAM.ID));
            program.setName(record.getValue(PROGRAM.NAME));
            program.setAbbreviation(record.getValue(PROGRAM.ABBREVIATION));
            program.setObjective(record.getValue(PROGRAM.OBJECTIVE));
            program.setDocumentationUrl(record.getValue(PROGRAM.DOCUMENTATION_URL));
            program.setCreatedAtUtc(record.getValue(PROGRAM.CREATED_AT_UTC));
            program.setUpdatedAtUtc(record.getValue(PROGRAM.UPDATED_AT_UTC));
            program.setCreatedBy(record.getValue(PROGRAM.CREATED_BY));
            program.setUpdatedBy(record.getValue(PROGRAM.UPDATED_BY));

            // Generate our species to attach to our program
            SpeciesEntity speciesEntity = new SpeciesEntity();
            speciesEntity.setId(record.getValue(SPECIES.ID));
            speciesEntity.setCommonName(record.getValue(SPECIES.COMMON_NAME));
            program.setSpecies(speciesEntity);

            // Generate our created by user to attach to our program

            // Generate our updated by user to attach to our program
        }

        return new ArrayList<>();
    }
}

