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
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.User;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramLocationQueryMapperUnitTest {

    ProgramLocationQueryMapper programLocationQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        programLocationQueryMapper = new ProgramLocationQueryMapper();
    }
    // Getters and types map properly
    @Test
    @SneakyThrows
    public void testMappings() {

        ProgramLocation programLocation = ProgramLocation.builder()
                .name("place1")
                .slope(BigDecimal.valueOf(7.6))
                .abbreviation("pla")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdByUser(User.builder().id(UUID.randomUUID()).name("user1").build())
                .updatedByUser(User.builder().id(UUID.randomUUID()).name("user2").build())
                .build();

        checkMapping(programLocation.getName(), programLocation, "name");
        checkMapping(programLocation.getSlope(), programLocation, "slope");
        checkMapping(programLocation.getAbbreviation(), programLocation, "abbreviation");
        checkMapping(programLocation.getCreatedAt(), programLocation, "createdAt");
        checkMapping(programLocation.getUpdatedAt(), programLocation, "updatedAt");
        checkMapping(programLocation.getCreatedByUser().getName(), programLocation, "createdByUserName");
        checkMapping(programLocation.getCreatedByUser().getId(), programLocation, "createdByUserId");
        checkMapping(programLocation.getUpdatedByUser().getName(), programLocation, "updatedByUserName");
        checkMapping(programLocation.getUpdatedByUser().getId(), programLocation, "updatedByUserId");
    }

    private void checkMapping(Object field, ProgramLocation programLocation, String fieldName) {
        MapperEntry<ProgramLocation> mapperEntry = programLocationQueryMapper.getMapperEntry(fieldName);
        assertEquals(field, mapperEntry.getGetter().apply(programLocation), "Wrong getter");
        assertEquals(true, mapperEntry.getFieldType() == field.getClass(), "Wrong class");
    }
}
