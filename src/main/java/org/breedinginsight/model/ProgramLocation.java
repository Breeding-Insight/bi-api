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

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.geojson.Feature;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.PLACE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "createdBy", "updatedBy"})
public class ProgramLocation extends PlaceEntity {

    private Country country;
    private Accessibility accessibility;
    private EnvironmentType environmentType;
    private Topography topography;

    private User createdByUser;
    private User updatedByUser;

    // JSONB not working with jackson
    @JsonProperty("coordinates")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Feature getCoordinatesJson() throws JsonProcessingException {
        ObjectMapper objMapper = new ObjectMapper();
        return objMapper.readValue(super.getCoordinates().data(), Feature.class);
    }

    public ProgramLocation(PlaceEntity placeEntity) {

        this.setId(placeEntity.getId());
        this.setProgramId(placeEntity.getProgramId());
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
                .programId(record.getValue(PLACE.PROGRAM_ID))
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
