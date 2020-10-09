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

package org.breedinginsight.api.v1.controller.brapi;

import lombok.SneakyThrows;
import org.breedinginsight.services.brapi.BrAPIClientProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BrAPIClientProviderUnitTest {

    @Test
    @SneakyThrows
    public void getUnqiueClientsAllUnique() {
        BrAPIClientProvider brAPIClientProvider = new BrAPIClientProvider();
        brAPIClientProvider.setPhenoClient("http://test-url.org");
        brAPIClientProvider.setGenoClient("http://test-url1.org");
        brAPIClientProvider.setCoreClient("http://test-url2.org");

        assertEquals(3, brAPIClientProvider.getAllUniqueClients().size(), "Wrong number of clients returned");
    }

    @Test
    @SneakyThrows
    public void getUniqueClientsTwoUnique() {
        BrAPIClientProvider brAPIClientProvider = new BrAPIClientProvider();
        brAPIClientProvider.setPhenoClient("http://test-url.org");
        brAPIClientProvider.setGenoClient("http://test-url1.org");
        brAPIClientProvider.setCoreClient("http://test-url1.org");

        assertEquals(2, brAPIClientProvider.getAllUniqueClients().size(), "Wrong number of clients returned");
    }

    @Test
    @SneakyThrows
    public void getUniqueClientsOneUnique() {
        BrAPIClientProvider brAPIClientProvider = new BrAPIClientProvider();
        brAPIClientProvider.setPhenoClient("http://test-url.org");
        brAPIClientProvider.setGenoClient("http://test-url.org");
        brAPIClientProvider.setCoreClient("http://test-url.org");

        assertEquals(1, brAPIClientProvider.getAllUniqueClients().size(), "Wrong number of clients returned");
    }

    @Test
    @SneakyThrows
    public void getUniqueClientsNestedPaths() {

        BrAPIClientProvider brAPIClientProvider = new BrAPIClientProvider();
        brAPIClientProvider.setPhenoClient("http://test-url.org/pheno");
        brAPIClientProvider.setGenoClient("http://test-url1.org/geno");
        brAPIClientProvider.setCoreClient("http://test-url2.org/core");

        assertEquals(3, brAPIClientProvider.getAllUniqueClients().size(), "Wrong number of clients returned");
    }
}
