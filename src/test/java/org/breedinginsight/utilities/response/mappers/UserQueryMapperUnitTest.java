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
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserQueryMapperUnitTest {

    UserQueryMapper userQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        userQueryMapper = new UserQueryMapper();
    }

    @Test
    public void testMappings() {
        User user = User.builder()
                .name("Test User")
                .email("test@user.com")
                .orcid("000000-000000-000000-00000")
                .systemRoles(List.of(SystemRole.builder().domain("admin").build()))
                .programRoles(List.of(ProgramUser.builder().program(Program.builder().name("Test program").build()).build()))
                .active(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();

        assertEquals(user.getName(), userQueryMapper.getField("name").apply(user), "Wrong getter");
        assertEquals(user.getEmail(), userQueryMapper.getField("email").apply(user), "Wrong getter");
        assertEquals(user.getOrcid(), userQueryMapper.getField("orcid").apply(user), "Wrong getter");
        assertEquals(user.getSystemRoles().stream().map(role -> role.getDomain()).collect(Collectors.toList()),
                userQueryMapper.getField("systemRoles").apply(user), "Wrong getter");
        assertEquals(user.getProgramRoles().stream().map(role -> role.getProgram().getName()).collect(Collectors.toList()),
                userQueryMapper.getField("programs").apply(user), "Wrong getter");
        assertEquals(user.getActive(), userQueryMapper.getField("active").apply(user), "Wrong getter");
        assertEquals(user.getCreatedAt(), userQueryMapper.getField("createdAt").apply(user), "Wrong getter");
        assertEquals(user.getUpdatedAt(), userQueryMapper.getField("updatedAt").apply(user), "Wrong getter");
        assertEquals(user.getCreatedBy(), userQueryMapper.getField("createdByUserId").apply(user), "Wrong getter");
        assertEquals(user.getUpdatedBy(), userQueryMapper.getField("updatedByUserId").apply(user), "Wrong getter");
    }
}
