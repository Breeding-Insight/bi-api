package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.PLACE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "createdBy", "updatedBy", "speciesId" })
public class ProgramLocation extends PlaceEntity {

    private CountryEntity country;
    private AccessibilityOptionEntity accessibility;
    private EnvironmentTypeEntity environment;
    private TopographyOptionEntity topography;

    private User createdByUser;
    private User updatedByUser;

    public ProgramLocation(PlaceEntity placeEntity) {

        this.setId(placeEntity.getId());
        this.setCountryId(placeEntity.getCountryId());
        this.setAccessibilityId(placeEntity.getAccessibilityId());
        this.setEnvironmentTypeId(placeEntity.getEnvironmentTypeId());
        this.setTopographyId(placeEntity.getTopographyId());
        this.setName(placeEntity.getName());
        this.setAbbreviation(placeEntity.getAbbreviation());
        this.setCoordinates(placeEntity.getCoordinates());
        this.setCoordinateUncertainty(placeEntity.getCoordinateUncertainty());
        this.setCoordinateDescription(placeEntity.getCoordinateDescription());
        this.setSlope(placeEntity.getSlope());
        this.setExposure(placeEntity.getExposure());
        this.setDocumentationUrl(placeEntity.getDocumentationUrl());
        this.setCreatedAt(placeEntity.getCreatedAt());
        this.setUpdatedAt(placeEntity.getUpdatedAt());
        this.setCreatedBy(placeEntity.getCreatedBy());
        this.setUpdatedBy(placeEntity.getUpdatedBy());

    }

    public static ProgramLocation parseSQLRecord(Record record) {

        // Generate our location record
        ProgramLocation location = ProgramLocation.builder()
                .id(record.getValue(PLACE.ID))
                .name(record.getValue(PLACE.NAME))
                .abbreviation(record.getValue(PLACE.ABBREVIATION))
                .coordinates(record.getValue(PLACE.COORDINATES))
                .coordinateUncertainty(record.getValue(PLACE.COORDINATE_UNCERTAINTY))
                .coordinateDescription(record.getValue(PLACE.COORDINATE_DESCRIPTION))
                .slope(record.getValue(PLACE.SLOPE))
                .exposure(record.getValue(PLACE.EXPOSURE))
                .documentationUrl(record.getValue(PLACE.DOCUMENTATION_URL))
                .createdAt(record.getValue(PLACE.CREATED_AT))
                .updatedAt(record.getValue(PLACE.UPDATED_AT))
                .createdBy(record.getValue(PLACE.CREATED_BY))
                .updatedBy(record.getValue(PLACE.UPDATED_BY))
                .active(record.getValue(PLACE.ACTIVE))
                .build();

        return location;
    }
}
