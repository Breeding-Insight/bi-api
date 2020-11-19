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
package org.breedinginsight.brapi.v1.model.response.mappers;

import org.breedinginsight.brapi.v1.model.ObservationVariable;
import org.breedinginsight.brapi.v1.model.Trait;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ObservationVariableQueryMapperUnitTest {

    private ObservationVariableQueryMapper variableQueryMapper;

    @BeforeAll
    public void setup() {
        variableQueryMapper = new ObservationVariableQueryMapper();
    }

    @Test
    public void testMappings() {

        Trait trait = Trait.builder()
                .propertyClass("testClass")
                .build();

        ObservationVariable variable = ObservationVariable.builder()
                .ontologyDbId("testId")
                .trait(trait)
                .build();

        assertEquals(variable.getObservationVariableDbId(),
                variableQueryMapper.getField("observationVariableDbId").apply(variable),
                "Wrong getter");
        assertEquals(variable.getTrait().getPropertyClass(),
                variableQueryMapper.getField("traitClass").apply(variable),
                "Wrong getter");

    }

    @Test
    public void testNullMappings() {

        ObservationVariable variable = ObservationVariable.builder()
                .ontologyDbId("testdbId")
                .build();

        assertEquals(null,
                variableQueryMapper.getField("traitClass").apply(variable),
                "Wrong getter");
    }

}
