package org.breedinginsight.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.dao.db.tables.pojos.PlaceEntity;
import org.breedinginsight.daos.ProgramLocationDAO;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.geojson.*;
import org.jooq.JSONB;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramLocationService {

    @Inject
    private ProgramLocationDAO programLocationDao;
    @Inject
    private ProgramService programService;
    @Inject
    private CountryService countryService;
    @Inject
    private EnvironmentTypeService environmentTypeService;
    @Inject
    private AccessibilityService accessibilityService;
    @Inject
    private TopographyService topographyService;

    public List<ProgramLocation> getProgramLocations(UUID programId) throws DoesNotExistException {
        /* Get the locations associated with a program. */
        //TODO
        return new ArrayList<>();
    }

    public ProgramLocation getProgramLocation(UUID programId, UUID locationId) throws DoesNotExistException {
        /* Get a specific location for a program. */
        //TODO
        return null;
    }

    public ProgramLocation addProgramLocation(User actingUser, UUID programId, ProgramLocationRequest programLocationRequest) throws DoesNotExistException, AlreadyExistsException, UnprocessableEntityException {

        UUID countryId = null;
        UUID environmentTypeId = null;
        UUID accessibilityId = null;
        UUID topographyId = null;
        String coordinates = null;

        // check if programId exists
        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program id does not exist");
        }

        // check if ids for optional fields exist in system
        if (programLocationRequest.getCountry() != null) {
            countryId = programLocationRequest.getCountry().getId();
            if (!countryService.exists(countryId)) {
                throw new UnprocessableEntityException("Country does not exist");
            }
        }

        if (programLocationRequest.getEnvironmentType() != null) {
            environmentTypeId = programLocationRequest.getEnvironmentType().getId();
            if (!environmentTypeService.exists(environmentTypeId)) {
                throw new UnprocessableEntityException("Environment type does not exist");
            }
        }

        if (programLocationRequest.getAccessibility() != null) {
            accessibilityId = programLocationRequest.getAccessibility().getId();
            if (!accessibilityService.exists(accessibilityId)) {
                throw new UnprocessableEntityException("Accessibility option does not exist");
            }
        }

        if (programLocationRequest.getTopography() != null) {
            topographyId = programLocationRequest.getTopography().getId();
            if (!topographyService.exists(topographyId)) {
                throw new UnprocessableEntityException("Topography option does not exist");
            }
        }

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
                if (!isValidLatitude(coords.getLatitude())) {
                    throw new UnprocessableEntityException("Invalid point latitude");
                }
                if (!isValidLongitude(coords.getLongitude())) {
                    throw new UnprocessableEntityException("Invalid point longitude");
                }
            }

            if (geometry instanceof Polygon) {
                Polygon polygon = (Polygon)geometry;
                List<LngLatAlt> points = polygon.getExteriorRing();
                if (!isListPointsLatLngValid(points)) {
                    throw new UnprocessableEntityException("Invalid polygon exterior latitude or longitude");
                }
            }

            ObjectMapper objMapper = new ObjectMapper();
            try {
                coordinates = objMapper.writeValueAsString(feature);
            } catch (JsonProcessingException e) {
                throw new UnprocessableEntityException("Problem parsing geojson coordinates");
            }
        }

        // parse and create the program location object
        PlaceEntity placeEntity = PlaceEntity.builder()
                .programId(programId)
                .name(programLocationRequest.getName())
                .countryId(countryId)
                .environmentTypeId(environmentTypeId)
                .accessibilityId(accessibilityId)
                .topographyId(topographyId)
                .abbreviation(programLocationRequest.getAbbreviation())
                .coordinates(JSONB.valueOf(coordinates))
                .coordinateUncertainty(programLocationRequest.getCoordinateUncertainty())
                .coordinateDescription(programLocationRequest.getCoordinateDescription())
                .slope(programLocationRequest.getSlope())
                .exposure(programLocationRequest.getExposure())
                .documentationUrl(programLocationRequest.getDocumentationUrl())
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();

        // Insert and update
        programLocationDao.insert(placeEntity);
        ProgramLocation location = programLocationDao.get(placeEntity.getId()).get(0);

        return location;
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

    public void delete(UUID locationId) throws DoesNotExistException {
        /* Deletes an existing program */

        PlaceEntity placeEntity = programLocationDao.fetchOneById(locationId);
        if (placeEntity == null){
            throw new DoesNotExistException("Program location does not exist");
        }

        programLocationDao.delete(placeEntity);
    }
}
