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

package org.breedinginsight.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.dao.db.tables.pojos.PlaceEntity;
import org.breedinginsight.daos.ProgramLocationDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.geojson.*;
import org.jooq.DSLContext;
import org.jooq.JSONB;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Singleton
public class ProgramLocationService {

    private ProgramLocationDAO programLocationDao;
    private ProgramService programService;
    private CountryService countryService;
    private EnvironmentTypeService environmentTypeService;
    private AccessibilityService accessibilityService;
    private TopographyService topographyService;
    private DSLContext dsl;

    @Inject
    public ProgramLocationService(ProgramLocationDAO programLocationDao,
                                  ProgramService programService,
                                  CountryService countryService,
                                  EnvironmentTypeService environmentTypeService,
                                  AccessibilityService accessibilityService,
                                  TopographyService topographyService,
                                  DSLContext dsl) {
        this.programLocationDao = programLocationDao;
        this.programService = programService;
        this.countryService = countryService;
        this.environmentTypeService = environmentTypeService;
        this.accessibilityService = accessibilityService;
        this.topographyService = topographyService;
        this.dsl = dsl;
    }

    public List<ProgramLocation> getByProgramId(UUID programId) throws DoesNotExistException, ApiException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program id does not exist");
        }

        return programLocationDao.getByProgramId(programId);
    }

    public Optional<ProgramLocation> getById(UUID programId, UUID locationId) throws ApiException {
        return programLocationDao.getById(programId, locationId, false);
    }

    private UUID validateCountryId(ProgramLocationRequest programLocationRequest) throws UnprocessableEntityException {
        UUID countryId = null;
        if (programLocationRequest.getCountry() != null) {
            countryId = programLocationRequest.getCountry().getId();
            if (!countryService.exists(countryId)) {
                throw new UnprocessableEntityException("Country does not exist");
            }
        }
        return countryId;
    }

    private UUID validateEnvironmentTypeId(ProgramLocationRequest programLocationRequest) throws UnprocessableEntityException {
        UUID environmentTypeId = null;
        if (programLocationRequest.getEnvironmentType() != null) {
            environmentTypeId = programLocationRequest.getEnvironmentType().getId();
            if (!environmentTypeService.exists(environmentTypeId)) {
                throw new UnprocessableEntityException("Environment type does not exist");
            }
        }
        return environmentTypeId;
    }

    private UUID validateAccessibilityId(ProgramLocationRequest programLocationRequest) throws UnprocessableEntityException {
        UUID accessibilityId = null;
        if (programLocationRequest.getAccessibility() != null) {
            accessibilityId = programLocationRequest.getAccessibility().getId();
            if (!accessibilityService.exists(accessibilityId)) {
                throw new UnprocessableEntityException("Accessibility option does not exist");
            }
        }
        return accessibilityId;
    }

    private UUID validateTopographyId(ProgramLocationRequest programLocationRequest) throws UnprocessableEntityException {
        UUID topographyId = null;
        if (programLocationRequest.getTopography() != null) {
            topographyId = programLocationRequest.getTopography().getId();
            if (!topographyService.exists(topographyId)) {
                throw new UnprocessableEntityException("Topography option does not exist");
            }
        }
        return topographyId;
    }

    private String validateCoordinates(ProgramLocationRequest programLocationRequest) throws MissingRequiredInfoException, UnprocessableEntityException {

        String coordinates = null;

        // geojson coordinate validation, not exhaustive just basic sanity checks
        if (programLocationRequest.getCoordinates() != null) {
            // convert from geoJson
            Feature feature = programLocationRequest.getCoordinates();
            GeoJsonObject geometry = feature.getGeometry();
            // only allow point and polygon
            if (!(geometry instanceof Point || geometry instanceof Polygon)) {
                throw new UnprocessableEntityException("Coordinates must be point or polygon geometry");
            }

            if (geometry instanceof Point) {
                Point point = (Point)geometry;
                LngLatAlt coords = point.getCoordinates();
                if (coords == null ) {
                    throw new MissingRequiredInfoException("Point missing coordinates");
                }
                if (!isValidLatitude(coords.getLatitude())) {
                    throw new UnprocessableEntityException("Invalid point latitude");
                }
                if (!isValidLongitude(coords.getLongitude())) {
                    throw new UnprocessableEntityException("Invalid point longitude");
                }
            } else if (geometry instanceof Polygon) {
                Polygon polygon = (Polygon)geometry;
                try {
                    List<LngLatAlt> points = polygon.getExteriorRing();
                    if (!isListPointsLatLngValid(points)) {
                        throw new UnprocessableEntityException("Invalid polygon exterior latitude or longitude");
                    }
                } catch (RuntimeException e) {
                    throw new UnprocessableEntityException("Polygon missing coordinates");
                }
            }

            ObjectMapper objMapper = new ObjectMapper();
            try {
                coordinates = objMapper.writeValueAsString(feature);
            } catch (JsonProcessingException e) {
                throw new UnprocessableEntityException("Problem parsing geojson coordinates");
            }
        }

        return coordinates;
    }

    // coordinate system
    boolean isValidLatitude(double latitude) {
        return latitude >= -90 && latitude <= 90;
    }

    boolean isValidLongitude(double longitude) {
        return longitude >= -180 && longitude <= 180;
    }

    boolean isListPointsLatLngValid(List<LngLatAlt> points) {

        for (LngLatAlt point : points) {
            if (!(isValidLatitude(point.getLatitude()) && isValidLongitude(point.getLongitude()))) {
                return false;
            }
        }

        return true;
    }

    public List<ProgramLocation> create(AuthenticatedUser actingUser, UUID programId, List<ProgramLocationRequest> newLocations) throws MissingRequiredInfoException, UnprocessableEntityException, DoesNotExistException {
        // check if programId exists
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));

        List<ProgramLocation> ret = new ArrayList<>();
        for(ProgramLocationRequest newLoc : newLocations) {
            ret.add(createLocation(actingUser, program, newLoc));
        }

        return ret;
    }

    public ProgramLocation create(AuthenticatedUser actingUser,
                                  UUID programId,
                                  ProgramLocationRequest programLocationRequest)
            throws DoesNotExistException, MissingRequiredInfoException, UnprocessableEntityException {

        // check if programId exists
        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));

        return createLocation(actingUser, program, programLocationRequest);
    }

    private ProgramLocation createLocation(AuthenticatedUser actingUser, Program program, ProgramLocationRequest programLocationRequest) throws UnprocessableEntityException, MissingRequiredInfoException, DoesNotExistException {
        // validate fields
        UUID countryId = validateCountryId(programLocationRequest);
        UUID environmentTypeId = validateEnvironmentTypeId(programLocationRequest);
        UUID accessibilityId = validateAccessibilityId(programLocationRequest);
        UUID topographyId = validateTopographyId(programLocationRequest);
        String coordinates = validateCoordinates(programLocationRequest);
        UUID programId = program.getId();

        // parse and create the program location object
        PlaceEntity placeEntity = PlaceEntity.builder()
                                             .programId(programId)
                                             .name(programLocationRequest.getName())
                                             .countryId(countryId)
                                             .environmentTypeId(environmentTypeId)
                                             .accessibilityId(accessibilityId)
                                             .topographyId(topographyId)
                                             .abbreviation(programLocationRequest.getAbbreviation())
                                             .coordinates(coordinates != null ? JSONB.valueOf(coordinates) : null)
                                             .coordinateUncertainty(programLocationRequest.getCoordinateUncertainty())
                                             .coordinateDescription(programLocationRequest.getCoordinateDescription())
                                             .slope(programLocationRequest.getSlope())
                                             .exposure(programLocationRequest.getExposure())
                                             .documentationUrl(programLocationRequest.getDocumentationUrl())
                                             .createdBy(actingUser.getId())
                                             .updatedBy(actingUser.getId())
                                             .build();


        // Insert and update
        //  This is warped in a transaction so if the BrAPI save call fails, the BI database insert is rolled back.
        return dsl.transactionResult(configuration -> {
            programLocationDao.insert(placeEntity);
            ProgramLocation progLocation = programLocationDao.getById(programId, placeEntity.getId(), false).orElseThrow(() -> new IllegalStateException("Location appears to not have been created"));

            // Add location to brapi service
            programLocationDao.createProgramLocationBrAPI(progLocation, program);

            return progLocation;
        });
    }

    public ProgramLocation update(AuthenticatedUser actingUser,
                                  UUID programId,
                                  UUID locationId,
                                  ProgramLocationRequest programLocationRequest)
            throws DoesNotExistException, MissingRequiredInfoException, UnprocessableEntityException {

        Program program = programService.getById(programId).orElseThrow(() -> new DoesNotExistException("Program id does not exist"));

        PlaceEntity placeEntity = programLocationDao.fetchOneById(locationId);
        if (placeEntity == null || (!placeEntity.getProgramId().equals(programId))){
            throw new DoesNotExistException("Program location does not exist");
        }

        // validate fields
        UUID countryId = validateCountryId(programLocationRequest);
        UUID environmentTypeId = validateEnvironmentTypeId(programLocationRequest);
        UUID accessibilityId = validateAccessibilityId(programLocationRequest);
        UUID topographyId = validateTopographyId(programLocationRequest);
        String coordinates = validateCoordinates(programLocationRequest);

        placeEntity.setName(programLocationRequest.getName());
        placeEntity.setCountryId(countryId);
        placeEntity.setEnvironmentTypeId(environmentTypeId);
        placeEntity.setAccessibilityId(accessibilityId);
        placeEntity.setTopographyId(topographyId);
        placeEntity.setAbbreviation(programLocationRequest.getAbbreviation());
        placeEntity.setCoordinates(coordinates != null ? JSONB.valueOf(coordinates) : null);
        placeEntity.setCoordinateUncertainty(programLocationRequest.getCoordinateUncertainty());
        placeEntity.setCoordinateDescription(programLocationRequest.getCoordinateDescription());
        placeEntity.setSlope(programLocationRequest.getSlope());
        placeEntity.setExposure(programLocationRequest.getExposure());
        placeEntity.setDocumentationUrl(programLocationRequest.getDocumentationUrl());
        placeEntity.setUpdatedBy(actingUser.getId());
        //  This is warped in a transaction so if the BrAPI update post fails, the BI database update is rolled back.
        ProgramLocation location = dsl.transactionResult(configuration -> {
            programLocationDao.update(placeEntity);
            ProgramLocation progLocation = programLocationDao.getById(programId, placeEntity.getId(), false).get();

            // Update location in brapi service
            programLocationDao.updateProgramLocationBrAPI(progLocation, program);
            return progLocation;
        });
        return location;
    }

    public void archive(AuthenticatedUser actingUser, UUID programId, UUID locationId) throws DoesNotExistException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        PlaceEntity placeEntity = programLocationDao.fetchOneById(locationId);
        if (placeEntity == null || (!placeEntity.getProgramId().equals(programId))){
            throw new DoesNotExistException("Program location does not exist");
        }

        placeEntity.setActive(false);
        placeEntity.setUpdatedBy(actingUser.getId());
        placeEntity.setUpdatedAt(OffsetDateTime.now());
        programLocationDao.update(placeEntity);
    }

    public void delete(UUID locationId) throws DoesNotExistException {

        PlaceEntity placeEntity = programLocationDao.fetchOneById(locationId);
        if (placeEntity == null){
            throw new DoesNotExistException("Program location does not exist");
        }

        programLocationDao.delete(placeEntity);
    }

    public List<ProgramLocation> getLocationsByDbId(Collection<String> locationDbIds, UUID programId) throws ApiException {
        return programLocationDao.getByDbIds(locationDbIds, programId);
    }

    public List<ProgramLocation> getLocationsByName(List<String> names, UUID programId) throws ApiException {
        return programLocationDao.getByNames(names, programId, true);
    }
}
