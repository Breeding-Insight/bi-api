package org.breedinginsight.services.brapi;

import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.modules.phenotype.TraitsAPI;
import org.brapi.client.v2.modules.phenotype.VariablesAPI;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class BrAPIProvider {

    @Inject
    Provider<BrAPIClientProvider> brAPIClientProvider;

    public TraitsAPI getTraitsAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new TraitsAPI(brAPIClient);
    }

    public VariablesAPI getVariablesAPI(BrAPIClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new VariablesAPI(brAPIClient);
    }

}
