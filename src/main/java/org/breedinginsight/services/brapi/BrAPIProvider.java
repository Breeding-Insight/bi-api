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
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.ServerInfoApi;
import org.brapi.client.v2.modules.germplasm.CrossesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributeValuesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.client.v2.modules.phenotype.TraitsApi;

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

    public TraitsApi getTraitsAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new TraitsApi(brAPIClient);
    }

    public ObservationVariablesApi getVariablesAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new ObservationVariablesApi(brAPIClient);
    }

    public ObservationsApi getObservationsAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new ObservationsApi(brAPIClient);
    }

    public ServerInfoApi getServerInfoAPI(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new ServerInfoApi(brAPIClient);
    }

    public GermplasmApi getGermplasmApi(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new GermplasmApi(brAPIClient);
    }

    public GermplasmAttributesApi getGermplasmAttributesApi(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new GermplasmAttributesApi(brAPIClient);
    }

    public GermplasmAttributeValuesApi getGermplasmAttributeValuesApi(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new GermplasmAttributeValuesApi(brAPIClient);
    }

    public ObservationUnitsApi getObservationUnitApi(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new ObservationUnitsApi(brAPIClient);
    }

    public LocationsApi getLocationsApi(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new LocationsApi(brAPIClient);
    }

    public CrossesApi getCrossesApi(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new CrossesApi(brAPIClient);
    }

    public List<ObservationVariablesApi> getAllUniqueVariablesAPI(){
        Set<BrAPIClient> clients = brAPIClientProvider.get().getAllUniqueClients();
        List<ObservationVariablesApi> variablesAPIS = new ArrayList<>();
        for (BrAPIClient client: clients){
            variablesAPIS.add(new ObservationVariablesApi(client));
        }
        return variablesAPIS;
    }

    public ProgramsApi getProgramsAPI(BrAPIClientType clientType) {
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new ProgramsApi(brAPIClient);
    }

    public List<ProgramsApi> getAllUniqueProgramsAPI(){
        Set<BrAPIClient> clients = brAPIClientProvider.get().getAllUniqueClients();
        List<ProgramsApi> programsAPIS = new ArrayList<>();
        for (BrAPIClient client: clients){
            programsAPIS.add(new ProgramsApi(client));
        }
        return programsAPIS;
    }

    public List<LocationsApi> getAllUniqueLocationsAPI(){
        Set<BrAPIClient> clients = brAPIClientProvider.get().getAllUniqueClients();
        List<LocationsApi> locationsAPIS = new ArrayList<>();
        for (BrAPIClient client: clients){
            locationsAPIS.add(new LocationsApi(client));
        }
        return locationsAPIS;
    }
}
