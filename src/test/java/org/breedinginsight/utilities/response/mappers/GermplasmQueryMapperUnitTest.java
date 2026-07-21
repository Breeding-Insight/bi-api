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
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GermplasmQueryMapperUnitTest {

    GermplasmQueryMapper germplasmQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        germplasmQueryMapper = new GermplasmQueryMapper();
    }

    @Test
    public void testExternalUidMappingUsesCanonicalReferenceSource() {
        BrAPIGermplasm germplasm = new BrAPIGermplasm();
        germplasm.setSeedSource("USDA");

        List<BrAPIExternalReference> externalReferences = new ArrayList<>();

        BrAPIExternalReference sourceReference = new BrAPIExternalReference();
        sourceReference.setReferenceSource("USDA");
        sourceReference.setReferenceID("OLD-UID");
        externalReferences.add(sourceReference);

        BrAPIExternalReference externalUidReference = new BrAPIExternalReference();
        externalUidReference.setReferenceSource("External UID");
        externalUidReference.setReferenceID("ABC-123");
        externalReferences.add(externalUidReference);

        germplasm.setExternalReferences(externalReferences);

        assertEquals("ABC-123", germplasmQueryMapper.getField("externalUID").apply(germplasm), "Wrong getter");
    }
}