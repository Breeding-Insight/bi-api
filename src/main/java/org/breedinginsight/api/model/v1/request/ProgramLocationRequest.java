package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import org.geojson.Feature;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class ProgramLocationRequest {

    private CountryRequest country;
    private NameIdRequest environmentType;
    private NameIdRequest accessibility;
    private NameIdRequest topography;

    @NotBlank
    private String name;
    private String abbreviation;
    private Feature coordinates;
    private BigDecimal coordinateUncertainty;
    private String coordinateDescription;
    private BigDecimal slope;
    private String exposure;
    private String documentationUrl;
}
