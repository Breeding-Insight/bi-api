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

package org.breedinginsight.utilities.response.mappers;

import lombok.SneakyThrows;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.model.*;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitQueryMapperUnitTest {

    TraitQueryMapper traitQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        traitQueryMapper = new TraitQueryMapper();
    }

    @Test
    public void testMappings() {
        Trait trait = Trait.builder()
                .observationVariableName("Test trait")
                .mainAbbreviation("test")
                .synonyms(List.of("t1", "t2"))
                .programObservationLevel(ProgramObservationLevel.builder().name("Plant").build())
                .active(true)
                .method(
                        Method.builder()
                        .description("A method for real")
                        .methodClass("Estimation")
                        .formula("a + b = c")
                        .build()
                )
                .scale(
                        Scale.builder()
                        .scaleName("scale test")
                        .decimalPlaces(3)
                        .validValueMin(0)
                        .validValueMax(999)
                        .categories(List.of(new BrAPIScaleValidValuesCategories().label("1").value("green")))
                        .build()
                )
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdByUser(User.builder().name("User1").id(UUID.randomUUID()).build())
                .updatedByUser(User.builder().name("User2").id(UUID.randomUUID()).build())
                .build();

        assertEquals(trait.getObservationVariableName(), traitQueryMapper.getField("name").apply(trait), "Wrong getter");
        assertEquals(trait.getMainAbbreviation(), traitQueryMapper.getField("mainAbbreviation").apply(trait), "Wrong getter");
        assertEquals(trait.getSynonyms(), traitQueryMapper.getField("synonyms").apply(trait), "Wrong getter");
        assertEquals(trait.getProgramObservationLevel().getName(), traitQueryMapper.getField("level").apply(trait), "Wrong getter");
        assertEquals(trait.getActive(), traitQueryMapper.getField("status").apply(trait), "Wrong getter");
        assertEquals(trait.getMethod().getDescription(), traitQueryMapper.getField("methodDescription").apply(trait), "Wrong getter");
        assertEquals(trait.getMethod().getMethodClass(), traitQueryMapper.getField("methodClass").apply(trait), "Wrong getter");
        assertEquals(trait.getMethod().getFormula(), traitQueryMapper.getField("methodFormula").apply(trait), "Wrong getter");
        assertEquals(trait.getScale().getScaleName(), traitQueryMapper.getField("scaleName").apply(trait), "Wrong getter");
        assertEquals(trait.getScale().getDecimalPlaces(), traitQueryMapper.getField("scaleDecimalPlaces").apply(trait), "Wrong getter");
        assertEquals(trait.getScale().getValidValueMin(), traitQueryMapper.getField("scaleLowerLimit").apply(trait), "Wrong getter");
        assertEquals(trait.getScale().getValidValueMax(), traitQueryMapper.getField("scaleUpperLimit").apply(trait), "Wrong getter");
        assertEquals(trait.getScale().getCategories().stream().map(category -> category.getLabel() + "=" + category.getValue()).collect(Collectors.toList()),
                traitQueryMapper.getField("scaleCategories").apply(trait), "Wrong getter");
        assertEquals(trait.getCreatedAt(), traitQueryMapper.getField("createdAt").apply(trait), "Wrong getter");
        assertEquals(trait.getUpdatedAt(), traitQueryMapper.getField("updatedAt").apply(trait), "Wrong getter");
        assertEquals(trait.getCreatedByUser().getId(), traitQueryMapper.getField("createdByUserId").apply(trait), "Wrong getter");
        assertEquals(trait.getCreatedByUser().getName(), traitQueryMapper.getField("createdByUserName").apply(trait), "Wrong getter");
        assertEquals(trait.getUpdatedByUser().getId(), traitQueryMapper.getField("updatedByUserId").apply(trait), "Wrong getter");
        assertEquals(trait.getUpdatedByUser().getName(), traitQueryMapper.getField("updatedByUserName").apply(trait), "Wrong getter");
    }
}
