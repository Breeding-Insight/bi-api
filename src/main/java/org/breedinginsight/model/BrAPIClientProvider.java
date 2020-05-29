package org.breedinginsight.model;

import io.micronaut.runtime.http.scope.RequestScope;
import lombok.Getter;
import org.brapi.client.v2.BrAPIClient;

@RequestScope
public class BrAPIClientProvider {

    private BrAPIClient coreClient;
    private BrAPIClient phenoClient;
    private BrAPIClient genoClient;

    public void setCoreClient(String url){
        this.coreClient = new BrAPIClient(url);
    }

    public void setPhenoClient(String url){
        this.phenoClient = new BrAPIClient(url);
    }

    public void setGenoClient(String url){
        this.genoClient = new BrAPIClient(url);
    }

    public BrAPIClient getClient(BrAPiClientType clientType){
        if (clientType == BrAPiClientType.CORE){ return coreClient; }
        else if (clientType == BrAPiClientType.PHENO){ return phenoClient; }
        else { return genoClient; }
    }

}
