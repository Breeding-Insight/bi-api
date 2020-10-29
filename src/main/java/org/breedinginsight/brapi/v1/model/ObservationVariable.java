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
package org.breedinginsight.brapi.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
public class ObservationVariable {
    private String date;
    private String name;
    private String observationVariableDbId;
    private String observationVariableName;
    private List<String> contextOfUse;
    private String crop;
    @JsonInclude
    private String defaultValue;
    private String documentationURL;
    private String growthStage;
    private String institution;
    private String language;
    private Method method;
    private String ontologyDbId;
    private String ontologyName;
    private OntologyReference ontologyRefernce;
    private Scale scale;
    private String scientist;
    private String status;
    private OffsetDateTime submissionTimestamp;
    @JsonInclude
    private List<String> synonyms;
    private Trait trait;
    private String xref;
}
