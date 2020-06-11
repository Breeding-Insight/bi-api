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

import io.micronaut.runtime.http.scope.RequestScope;
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

    public BrAPIClient getClient(BrAPIClientType clientType){
        if (clientType == BrAPIClientType.CORE){ return coreClient; }
        else if (clientType == BrAPIClientType.PHENO){ return phenoClient; }
        else { return genoClient; }
    }

}
