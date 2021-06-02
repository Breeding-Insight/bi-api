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

import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.http.scope.RequestScope;
import org.brapi.client.v2.BrAPIClient;

import javax.inject.Inject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

@RequestScope
public class BrAPIClientProvider {

    private final Duration requestTimeout;

    private BrAPIClient coreClient;
    private BrAPIClient phenoClient;
    private BrAPIClient genoClient;
    private BrAPIClient brapiClient;

    @Inject
    public BrAPIClientProvider(@Value(value = "${brapi.read-timeout:5m}") Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public void setCoreClient(String url){
        this.coreClient = new BrAPIClient(url);
        initializeHttpClient(this.coreClient);
    }

    public void setPhenoClient(String url){
        this.phenoClient = new BrAPIClient(url);
        initializeHttpClient(this.phenoClient);
    }

    public void setGenoClient(String url){
        this.genoClient = new BrAPIClient(url);
        initializeHttpClient(this.genoClient);
    }

    public void setBrapiClient(String url){
        this.brapiClient = new BrAPIClient(url);
        initializeHttpClient(this.brapiClient);
    }

    public BrAPIClient getClient(BrAPIClientType clientType){
        if (clientType == BrAPIClientType.CORE){ return coreClient; }
        else if (clientType == BrAPIClientType.PHENO){ return phenoClient; }
        else if (clientType == BrAPIClientType.GENO) { return genoClient; }
        else { return brapiClient; }
    }

    public Set<BrAPIClient> getAllUniqueClients(){

        Set<BrAPIClient> clients = new TreeSet<>(Comparator.comparing(BrAPIClient::getBasePath));
        clients.add(coreClient);
        clients.add(phenoClient);
        clients.add(genoClient);

        return clients;
    }

    private void initializeHttpClient(BrAPIClient brapiClient) {
        brapiClient.setHttpClient(brapiClient.getHttpClient()
                                             .newBuilder()
                                             .readTimeout(getRequestTimeout())
                                             .build());
    }

    //TODO figure out why BrAPIServiceFilterIntegrationTest fails when requestTimeout is set in the constructor
    private Duration getRequestTimeout() {
        if(requestTimeout != null) {
            return requestTimeout;
        }

        return Duration.of(5, ChronoUnit.MINUTES);
    }

}
