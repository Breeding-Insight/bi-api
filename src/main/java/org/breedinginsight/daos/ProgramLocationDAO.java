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

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.github.filosganga.geogson.model.Feature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.LocationQueryParams;
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrApiGeoJSON;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.response.BrAPILocationListResponse;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.PlaceDao;
import org.breedinginsight.model.*;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;

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
    private Gson gson;

    @Property(name = "brapi.server.reference-source")
    protected String referenceSource;

    @Inject
    public ProgramLocationDAO(Configuration config, DSLContext dsl,
                              BrAPIProvider brAPIProvider) {
        super(config);
        this.dsl = dsl;
        this.brAPIProvider = brAPIProvider;
        this.gson = new GsonBuilder()
            .registerTypeAdapterFactory(new GeometryAdapterFactory())
            .create();
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

        BrAPIExternalReference externalReference = new BrAPIExternalReference()
                                                                         .referenceID(location.getId().toString())
                                                                         .referenceSource(referenceSource);

        BrAPILocation brApiLocation = new BrAPILocation()
                                                   .abbreviation(location.getAbbreviation())
                                                   //.additionalInfo() do not keep this in our model
                                                   .coordinateDescription(location.getCoordinateDescription())
                                                   .coordinateUncertainty(location.getCoordinateUncertainty() != null ? location.getCoordinateUncertainty().toPlainString() : null)
                                                   .coordinates(getClientGeoJson(location))
                                                   .countryCode(location.getCountry() != null ? location.getCountry().getAlpha3Code() : null)
                                                   .countryName(location.getCountry() != null ? location.getCountry().getName() : null)
                                                   .documentationURL(location.getDocumentationUrl())
                                                   .environmentType(location.getEnvironmentType() != null ? location.getEnvironmentType().getName() : null)
                                                   .exposure(location.getExposure())
                                                   .externalReferences(List.of(externalReference))
                                                   //.instituteAddress() do not keep this in our model
                                                   //.instituteName() do not keep this in our model
                                                   .locationName(location.getName())
                                                   //.locationType() do not keep this in our model
                                                   //.siteStatus() do not keep this in our model
                                                   .slope(location.getSlope() != null ? location.getSlope().toPlainString() : null)
                                                   .topography(location.getTopography() != null ? location.getTopography().getName() : null);

        // POST locations to each brapi service
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        try {
            List<LocationsApi> locationsAPIs = brAPIProvider.getAllUniqueLocationsAPI();
            for (LocationsApi locationsAPI: locationsAPIs){
                locationsAPI.locationsPost(List.of(brApiLocation));
            }
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage());
        }

    }

    public void updateProgramLocationBrAPI(ProgramLocation location) {

        LocationQueryParams searchRequest = new LocationQueryParams()
                                                            .externalReferenceID(location.getId().toString())
                                                            .externalReferenceSource(referenceSource);

        // Location goes in all of the clients
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        List<LocationsApi> locationsAPIs = brAPIProvider.getAllUniqueLocationsAPI();
        for (LocationsApi locationsAPI: locationsAPIs){

            // Get existing brapi location
            ApiResponse<BrAPILocationListResponse> brApiLocations;
            try {
                brApiLocations = locationsAPI.locationsGet(searchRequest);
            } catch (ApiException e) {
                throw new HttpServerException("Could not find location in BrAPI service.");
            }

            if (brApiLocations.getBody().getResult().getData().size() != 1){
                throw new HttpServerException("Could not find unique location in BrAPI service.");
            }

            BrAPILocation brApiLocation = brApiLocations.getBody().getResult().getData().get(0);

            //TODO: Need to add archived/not archived when available in brapi
            brApiLocation.setAbbreviation(location.getAbbreviation());
            //brApiLocation.setAdditionalInfo() do not keep this in our model
            brApiLocation.setCoordinateDescription(location.getCoordinateDescription());
            brApiLocation.setCoordinateUncertainty(location.getCoordinateUncertainty() != null ? location.getCoordinateUncertainty().toPlainString(): null);
            brApiLocation.setCoordinates(getClientGeoJson(location));
            brApiLocation.setCountryCode(location.getCountry() != null ? location.getCountry().getAlpha3Code() : null);
            brApiLocation.setCountryName(location.getCountry() != null ? location.getCountry().getName() : null);
            brApiLocation.setDocumentationURL(location.getDocumentationUrl());
            brApiLocation.setEnvironmentType(location.getEnvironmentType() != null ? location.getEnvironmentType().getName() : null);
            brApiLocation.setExposure(location.getExposure());
            //brApiLocation.setInstituteAddress(); do not keep this in our model
            //brApiLocation.setInstituteName(); do not keep this in our model
            brApiLocation.setLocationName(location.getName());
            //brApiLocation.setLocationType(); do not keep this in our model
            //brApiLocation.setSiteStatus(); do not keep this in our model
            brApiLocation.setSlope(location.getSlope() != null ? location.getSlope().toPlainString() : null);
            brApiLocation.setTopography(location.getTopography() != null ? location.getTopography().getName() : null);

            try {
                locationsAPI.locationsLocationDbIdPut(brApiLocation.getLocationDbId(), brApiLocation);
            } catch (ApiException e) {
                throw new HttpServerException("Could not find location in BrAPI service.");
            }
        }
    }

    private BrApiGeoJSON getClientGeoJson(ProgramLocation location) {
        BrApiGeoJSON geoJson = null;
        if (location.getCoordinates() != null) {
            Feature feature = gson.fromJson(location.getCoordinates().data(), Feature.class);
            geoJson = BrApiGeoJSON.builder()
                    .geometry(feature.geometry())
                    .type("Feature")
                    .build();
        }
        return geoJson;
    }


}
