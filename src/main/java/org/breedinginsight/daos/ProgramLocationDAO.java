package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.PlaceDao;
import org.breedinginsight.model.*;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramLocationDAO extends PlaceDao {
    private DSLContext dsl;
    @Inject
    public ProgramLocationDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<ProgramLocation> getByProgramId(UUID programId) {
        return getLocations(programId, null);
    }

    public List<ProgramLocation> getById(UUID locationId) {
        List<UUID> locations = new ArrayList<>();
        locations.add(locationId);
        return getLocations(null, locations);
    }

    private List<ProgramLocation> getLocations(UUID programId, List<UUID> locationIds) {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");
        Result<Record> queryResult;
        List<ProgramLocation> resultLocations = new ArrayList<>();

        SelectOnConditionStep<Record> query = dsl.select()
                .from(PLACE)
                .leftJoin(COUNTRY).on(PLACE.COUNTRY_ID.eq(COUNTRY.ID))
                .leftJoin(ENVIRONMENT_TYPE).on(PLACE.ENVIRONMENT_TYPE_ID.eq(ENVIRONMENT_TYPE.ID))
                .leftJoin(ACCESSIBILITY_OPTION).on(PLACE.ACCESSIBILITY_ID.eq(ACCESSIBILITY_OPTION.ID))
                .leftJoin(TOPOGRAPHY_OPTION).on(PLACE.TOPOGRAPHY_ID.eq(TOPOGRAPHY_OPTION.ID))
                .leftJoin(createdByUser).on(PLACE.CREATED_BY.eq(createdByUser.ID))
                .leftJoin(updatedByUser).on(PLACE.UPDATED_BY.eq(updatedByUser.ID));

        if (locationIds != null) {
            queryResult = query
                    .where(PLACE.ID.in(locationIds))
                    .fetch();
        } else if (programId != null) {
            queryResult = query
                    .where(PLACE.PROGRAM_ID.eq(programId))
                    .fetch();
        } else {
            queryResult = query.fetch();
        }

        // Parse the result
        for (Record record: queryResult){
            ProgramLocation location = ProgramLocation.parseSQLRecord(record);
            location.setCountry(Country.parseSQLRecord(record));
            location.setEnvironmentType(EnvironmentType.parseSQLRecord(record));
            location.setAccessibility(Accessibility.parseSQLRecord(record));
            location.setTopography(Topography.parseSQLRecord(record));
            location.setCreatedByUser(org.breedinginsight.model.User.parseSQLRecord(record, createdByUser));
            location.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
            resultLocations.add(location);
        }

        return resultLocations;
    }

}
