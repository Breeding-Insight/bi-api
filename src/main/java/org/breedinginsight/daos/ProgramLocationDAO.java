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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrApiGeoJSON;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.request.BrAPILocationSearchRequest;
import org.brapi.v2.model.core.response.BrAPILocationListResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.PlaceDao;
import org.breedinginsight.model.*;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
@Slf4j
public class ProgramLocationDAO extends PlaceDao {
    private final DSLContext dsl;
    private final Gson gson;

    protected final String referenceSource;
    private final BrAPIDAOUtil brAPIDAOUtil;

    private final ProgramDAO programDAO;

    @Inject
    public ProgramLocationDAO(Configuration config, DSLContext dsl, @Property(name = "brapi.server.reference-source") String referenceSource, BrAPIDAOUtil brAPIDAOUtil, ProgramDAO programDAO) {
        super(config);
        this.dsl = dsl;
        this.gson = new GsonBuilder()
            .registerTypeAdapterFactory(new GeometryAdapterFactory())
            .create();
        this.referenceSource = referenceSource;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programDAO = programDAO;
    }

    // get all active locations by program id
    public List<ProgramLocation> getByProgramId(UUID programId) throws ApiException {

        List<Record> records = getProgramLocationsQuery()
                .where(PLACE.PROGRAM_ID.eq(programId).and(PLACE.ACTIVE.eq(true)))
                .fetch();

        return parseRecords(records, true);

    }

    // get specified program location regardless of active status
    // path programId must match programId in location
    public Optional<ProgramLocation> getById(UUID programId, UUID locationId, boolean full) throws ApiException {
        List<ProgramLocation> locations = getByIds(programId, List.of(locationId), full);

        return Utilities.getSingleOptional(locations);
    }



    public List<ProgramLocation> getByIds(UUID programId, Collection<UUID> locationIds, boolean full) throws ApiException {
        List<Record> records = getProgramLocationsQuery()
                .where(PLACE.ID.in(locationIds).and(PLACE.PROGRAM_ID.eq(programId)))
                .fetch();

        return parseRecords(records, full);
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

    private List<ProgramLocation> parseRecords(List<Record> records, boolean fetchBrAPIObject) throws ApiException {

        Map<UUID, ProgramLocation> resultLocations = new HashMap<>();
        List<UUID> locationIds = new ArrayList<>();
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
            resultLocations.put(location.getId(), location);
            locationIds.add(location.getId());
        }

        if(fetchBrAPIObject && !resultLocations.isEmpty()) {
            List<BrAPILocation> brAPILocations = getBrapiLocations(locationIds, resultLocations.values().stream().findFirst().get().getProgramId());

            if (brAPILocations.size() != resultLocations.size()) {
                throw new IllegalStateException("Did not find BrAPI Location objects for each location");
            }

            brAPILocations.forEach(brapiLocation -> {
                BrAPIExternalReference externalReference = Utilities.getExternalReference(brapiLocation.getExternalReferences(), referenceSource)
                                                                    .orElseThrow(() -> new IllegalStateException("No externalReference for BrAPI Location: " + brapiLocation.getLocationDbId()));

                ProgramLocation location = resultLocations.get(UUID.fromString(externalReference.getReferenceID()));
                if(location == null) {
                    throw new IllegalStateException("Did not find BrAPI Location for location: " + location.getId());
                }
                location.setLocationDbId(brapiLocation.getLocationDbId());
            });
        }

        return new ArrayList<>(resultLocations.values());
    }

    public void createProgramLocationBrAPI(ProgramLocation location, Program program) {

        BrAPIExternalReference locationIdRef = new BrAPIExternalReference()
                                                                         .referenceID(location.getId().toString())
                                                                         .referenceSource(referenceSource);
        BrAPIExternalReference programIdRef = new BrAPIExternalReference()
                .referenceID(location.getProgramId().toString())
                .referenceSource(String.format("%s/%s", referenceSource, ExternalReferenceSource.PROGRAMS.getName()));

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
                                                   .externalReferences(List.of(locationIdRef, programIdRef))
                                                   //.instituteAddress() do not keep this in our model
                                                   //.instituteName() do not keep this in our model
                                                   .locationName(Utilities.appendProgramKey(location.getName(), program.getKey()))
                                                   //.locationType() do not keep this in our model
                                                   //.siteStatus() do not keep this in our model
                                                   .slope(location.getSlope() != null ? location.getSlope().toPlainString() : null)
                                                   .topography(location.getTopography() != null ? location.getTopography().getName() : null);

        // POST locations to each brapi service
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        try {
            LocationsApi locationsAPI = new LocationsApi(programDAO.getCoreClient(location.getProgramId()));
            ApiResponse<BrAPILocationListResponse> brapiResponse = locationsAPI.locationsPost(List.of(brApiLocation));
            if(brapiResponse.getBody().getResult().getData().size() == 1) {
                location.setLocationDbId(brapiResponse.getBody().getResult().getData().get(0).getLocationDbId());
            }
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

    }

    public void updateProgramLocationBrAPI(ProgramLocation location, Program program) {

        try {
            List<BrAPILocation> brApiLocations = getBrapiLocations(List.of(location.getId()), location.getProgramId());

            if (brApiLocations.size() != 1){
                throw new HttpServerException("Could not find unique location in BrAPI service.");
            }

            BrAPILocation brApiLocation = brApiLocations.get(0);

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
            brApiLocation.setLocationName(Utilities.appendProgramKey(location.getName(), program.getKey()));
            //brApiLocation.setLocationType(); do not keep this in our model
            //brApiLocation.setSiteStatus(); do not keep this in our model
            brApiLocation.setSlope(location.getSlope() != null ? location.getSlope().toPlainString() : null);
            brApiLocation.setTopography(location.getTopography() != null ? location.getTopography().getName() : null);

            LocationsApi locationsAPI = new LocationsApi(programDAO.getCoreClient(location.getProgramId()));
            locationsAPI.locationsLocationDbIdPut(brApiLocation.getLocationDbId(), brApiLocation);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new HttpServerException("Could not find location in BrAPI service.");
        }
    }

    private List<BrAPILocation> getBrapiLocations(List<UUID> locationIds, UUID programId) throws ApiException {
        BrAPILocationSearchRequest searchRequest = new BrAPILocationSearchRequest()
                .externalReferenceIDs(locationIds.stream().map(UUID::toString).collect(Collectors.toList()))
                .externalReferenceSources(List.of(referenceSource));

        // Location goes in all of the clients
        // TODO: If there is a failure, roll back all before the failure.
        LocationsApi locationsAPI = new LocationsApi(programDAO.getCoreClient(programId));

        // Get existing brapi location
        return brAPIDAOUtil.search(locationsAPI::searchLocationsPost, locationsAPI::searchLocationsSearchResultsDbIdGet, searchRequest);
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


    public List<ProgramLocation> getByDbIds(Collection<String> locationDbIds, UUID programId) throws ApiException {
        BrAPILocationSearchRequest searchRequest = new BrAPILocationSearchRequest()
                .locationDbIds(new ArrayList<>(locationDbIds))
                .externalReferenceIDs(List.of(programId.toString()))
                .externalReferenceSources(List.of(String.format("%s/%s", referenceSource, ExternalReferenceSource.PROGRAMS.getName())));

        return getProgramLocationsByBrAPISearch(programId, searchRequest);
    }

    public List<ProgramLocation> getByNames(List<String> names, UUID programId) throws ApiException {
        BrAPILocationSearchRequest searchRequest = new BrAPILocationSearchRequest()
                .locationNames(new ArrayList<>(names))
                .externalReferenceIDs(List.of(programId.toString()))
                .externalReferenceSources(List.of(String.format("%s/%s", referenceSource, ExternalReferenceSource.PROGRAMS.getName())));

        return getProgramLocationsByBrAPISearch(programId, searchRequest);
    }

    @NotNull
    private List<ProgramLocation> getProgramLocationsByBrAPISearch(UUID programId, BrAPILocationSearchRequest searchRequest) throws ApiException {
        LocationsApi locationsAPI = new LocationsApi(programDAO.getCoreClient(programId));
        List<BrAPILocation> searchResult = brAPIDAOUtil.search(locationsAPI::searchLocationsPost, locationsAPI::searchLocationsSearchResultsDbIdGet, searchRequest);

        Map<UUID, BrAPILocation> brapiLocationById = new HashMap<>();
        searchResult.forEach(brAPILocation -> {
            BrAPIExternalReference xref = Utilities.getExternalReference(brAPILocation.getExternalReferences(), referenceSource)
                                                   .orElseThrow(() -> new IllegalStateException(String.format("Location (by dbid): %s does not have any external references", brAPILocation.getLocationDbId())));
            brapiLocationById.put(UUID.fromString(xref.getReferenceID()), brAPILocation);
        });

        List<Record> records = getProgramLocationsQuery()
                .where(PLACE.ID.in(brapiLocationById.keySet()).and(PLACE.PROGRAM_ID.eq(programId)))
                .fetch();
        List<ProgramLocation> programLocations = parseRecords(records, false);
        if(programLocations.size() != brapiLocationById.size()) {
            throw new IllegalStateException("Didn't find all locations by id");
        }

        programLocations.forEach(location -> {
            BrAPILocation brAPILocation = brapiLocationById.get(location.getId());
            location.setLocationDbId(brAPILocation.getLocationDbId());
        });

        return programLocations;
    }
}
