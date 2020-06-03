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
