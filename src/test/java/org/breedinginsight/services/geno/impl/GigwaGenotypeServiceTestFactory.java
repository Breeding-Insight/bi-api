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

package org.breedinginsight.services.geno.impl;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import org.breedinginsight.brapps.importer.daos.BrAPISampleDAO;
import org.breedinginsight.daos.SampleSubmissionDAO;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;

import javax.inject.Singleton;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@Factory
@Requires(property = "test.gigwa-genotype-service", value = "true")
public class GigwaGenotypeServiceTestFactory {

    @Singleton
    @Replaces(BrAPIEndpointProvider.class)
    BrAPIEndpointProvider brAPIEndpointProvider() {
        return spy(new BrAPIEndpointProvider());
    }

    @Singleton
    @Replaces(SampleSubmissionDAO.class)
    SampleSubmissionDAO sampleSubmissionDAO() {
        return mock(SampleSubmissionDAO.class);
    }

    @Singleton
    @Replaces(BrAPISampleDAO.class)
    BrAPISampleDAO sampleDAO() {
        return mock(BrAPISampleDAO.class);
    }
}
