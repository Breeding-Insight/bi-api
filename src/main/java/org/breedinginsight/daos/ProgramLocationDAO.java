/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
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

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.APIException;
import org.brapi.client.v2.model.exceptions.HttpException;
import org.brapi.client.v2.modules.core.LocationsAPI;
import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.core.model.BrApiLocation;
import org.brapi.v2.core.model.request.LocationsRequest;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.PlaceDao;
import org.breedinginsight.model.*;
import org.breedinginsight.model.User;
import org.breedinginsight.services.brapi.BrAPIProvider;
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
    private BrAPIProvider brAPIProvider;

    @Property(name = "brapi.server.reference-source")
    protected String referenceSource;

    @Inject
    public ProgramLocationDAO(Configuration config, DSLContext dsl,
                              BrAPIProvider brAPIProvider) {
        super(config);
        this.dsl = dsl;
        this.brAPIProvider = brAPIProvider;
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

    public void createProgramLocationBrAPI(ProgramLocation location) {

        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(location.getId().toString())
                .referenceSource(referenceSource)
                .build();

        BrApiLocation brApiLocation = BrApiLocation.builder()
                .abbreviation(location.getAbbreviation())
                //.additionalInfo()
                .coordinateDescription(location.getCoordinateDescription())
                .coordinateUncertainty(location.getCoordinateUncertainty().toPlainString())
                //.coordinates(location.getCoordinates())
                .countryCode(location.getCountry().getAlpha3Code())
                .countryName(location.getCountry().getName())
                .documentationURL(location.getDocumentationUrl())
                .environmentType(location.getEnvironmentType().getName())
                .exposure(location.getExposure())
                .externalReferences(List.of(externalReference))
                //.instituteAddress()
                //.instituteName()
                .locationName(location.getName())
                //.locationType()
                //.siteStatus()
                .slope(location.getSlope().toPlainString())
                .topography(location.getTopography().getName())
                .build();

        // POST locations to each brapi service
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        try {
            List<LocationsAPI> locationsAPIs = brAPIProvider.getAllUniqueLocationsAPI();
            for (LocationsAPI locationsAPI: locationsAPIs){
                locationsAPI.createLocation(brApiLocation);
            }
        } catch (HttpException | APIException e) {
            throw new InternalServerException(e.getMessage());
        }

    }

    public void updateProgramLocationBrAPI(ProgramLocation location) {

        LocationsRequest searchRequest = LocationsRequest.builder()
                .externalReferenceID(location.getId().toString())
                .externalReferenceSource(referenceSource)
                .build();

        // Location goes in all of the clients
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        List<LocationsAPI> locationsAPIs = brAPIProvider.getAllUniqueLocationsAPI();
        for (LocationsAPI locationsAPI: locationsAPIs){

            // Get existing brapi location
            List<BrApiLocation> brApiLocations;
            try {
                brApiLocations = locationsAPI.getLocations(searchRequest);
            } catch (HttpException | APIException e) {
                throw new HttpServerException("Could not find location in BrAPI service.");
            }

            if (brApiLocations.size() != 1){
                throw new HttpServerException("Could not find unique location in BrAPI service.");
            }

            BrApiLocation brApiLocation = brApiLocations.get(0);

            //TODO: Need to add archived/not archived when available in brapi
            brApiLocation.setAbbreviation(location.getAbbreviation());
            //brApiLocation.setAdditionalInfo()
            brApiLocation.setCoordinateDescription(location.getCoordinateDescription());
            brApiLocation.setCoordinateUncertainty(location.getCoordinateUncertainty().toPlainString());
            //brApiLocation.getCoordinates()
            brApiLocation.setCountryCode(location.getCountry().getAlpha3Code());
            brApiLocation.setCountryName(location.getCountry().getName());
            brApiLocation.setDocumentationURL(location.getDocumentationUrl());
            brApiLocation.setEnvironmentType(location.getEnvironmentType().getName());
            brApiLocation.setExposure(location.getExposure());
            //brApiLocation.setInstituteAddress();
            //brApiLocation.setInstituteName();
            brApiLocation.setLocationName(location.getName());
            //brApiLocation.setLocationType();
            //brApiLocation.setSiteStatus();
            brApiLocation.setSlope(location.getSlope().toPlainString());
            brApiLocation.setTopography(location.getTopography().getName());

            try {
                locationsAPI.updateLocation(brApiLocation);
            } catch (HttpException | APIException e) {
                throw new HttpServerException("Could not find location in BrAPI service.");
            }
        }
    }


}
