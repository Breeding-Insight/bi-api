package org.breedinginsight.model;

import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.modules.phenotype.TraitsAPI;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class BrAPIProvider {

    @Inject
    Provider<BrAPIClientProvider> brAPIClientProvider;

    public TraitsAPI getTraitsAPI(BrAPiClientType clientType){
        BrAPIClient brAPIClient = brAPIClientProvider.get().getClient(clientType);
        return new TraitsAPI(brAPIClient);
    }
}
