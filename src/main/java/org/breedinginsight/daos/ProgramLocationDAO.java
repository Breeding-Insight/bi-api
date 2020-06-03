/*
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.Optional;
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

    // get all active locations by program id
    public List<ProgramLocation> getByProgramId(UUID programId) {

        List<Record> records = getProgramLocationsQuery()
                .where(PLACE.PROGRAM_ID.eq(programId).and(PLACE.ACTIVE.eq(true)))
                .fetch();

        return parseRecords(records);

    }

    // get specified program location regardless of active status
    // path programId must match programId in location
    public Optional<ProgramLocation> getById(UUID programId, UUID locationId) {

        List<Record> records = getProgramLocationsQuery()
                .where(PLACE.ID.eq(locationId).and(PLACE.PROGRAM_ID.eq(programId)))
                .fetch();

        List<ProgramLocation> locations = parseRecords(records);

        if (locations.size() > 0){
            return Optional.of(locations.get(0));
        } else {
            return Optional.empty();
        }

    }

    private SelectOnConditionStep<Record> getProgramLocationsQuery(){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        return dsl.select()
                .from(PLACE)
                .leftJoin(COUNTRY).on(PLACE.COUNTRY_ID.eq(COUNTRY.ID))
                .leftJoin(ENVIRONMENT_TYPE).on(PLACE.ENVIRONMENT_TYPE_ID.eq(ENVIRONMENT_TYPE.ID))
                .leftJoin(ACCESSIBILITY_OPTION).on(PLACE.ACCESSIBILITY_ID.eq(ACCESSIBILITY_OPTION.ID))
                .leftJoin(TOPOGRAPHY_OPTION).on(PLACE.TOPOGRAPHY_ID.eq(TOPOGRAPHY_OPTION.ID))
                .leftJoin(createdByUser).on(PLACE.CREATED_BY.eq(createdByUser.ID))
                .leftJoin(updatedByUser).on(PLACE.UPDATED_BY.eq(updatedByUser.ID));
    }

    private List<ProgramLocation> parseRecords(List<Record> records) {

        List<ProgramLocation> resultLocations = new ArrayList<>();
        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // Parse the result
        for (Record record : records) {
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
