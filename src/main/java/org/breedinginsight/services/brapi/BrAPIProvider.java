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

package org.breedinginsight.services.brapi;

import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.modules.core.ProgramsAPI;
import org.brapi.client.v2.modules.phenotype.TraitsAPI;
import org.brapi.client.v2.modules.phenotype.VariablesAPI;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class BrAPIProvider {

    private Provider<BrAPIClientProvider> brAPIClientProvider;

    @Inject
    public BrAPIProvider(Provider<BrAPIClientProvider> brAPIClientProvider){
        this.brAPIClientProvider = brAPIClientProvider;
    }

    public TraitsAPI getTraitsAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new TraitsAPI(brAPIClient);
    }

    public VariablesAPI getVariablesAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new VariablesAPI(brAPIClient);
    }

    public List<VariablesAPI> getAllUniqueVariablesAPI(){
        Set<BrAPIClient> clients = brAPIClientProvider.get().getAllUniqueClients();
        List<VariablesAPI> variablesAPIS = new ArrayList<>();
        for (BrAPIClient client: clients){
            variablesAPIS.add(new VariablesAPI(client));
        }
        return variablesAPIS;
    }

    public ProgramsAPI getProgramsAPI(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new ProgramsAPI(brAPIClient);
    }

    public List<ProgramsAPI> getAllUniqueProgramsAPI(){
        Set<BrAPIClient> clients = brAPIClientProvider.get().getAllUniqueClients();
        List<ProgramsAPI> programsAPIS = new ArrayList<>();
        for (BrAPIClient client: clients){
            programsAPIS.add(new ProgramsAPI(client));
        }
        return programsAPIS;
    }
}
