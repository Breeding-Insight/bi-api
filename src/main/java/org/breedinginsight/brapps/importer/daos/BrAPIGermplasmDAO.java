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

package org.breedinginsight.brapps.importer.daos;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributesApi;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIGermplasmAttribute;
import org.brapi.v2.model.germ.request.BrAPIGermplasmAttributeSearchRequest;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class BrAPIGermplasmDAO extends BrAPIDAO {

    private BrAPIProvider brAPIProvider;

    @Inject
    public BrAPIGermplasmDAO(BrAPIProvider brAPIProvider) {
        this.brAPIProvider = brAPIProvider;
    }

    public List<BrAPIGermplasm> getGermplasmByName(List<String> germplasmNames, BrAPIProgram brAPIProgram) throws ApiException {
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.germplasmNames(germplasmNames);
        // TODO: Should be have a 'Pedigree' study to connect the germplasm to the program? Makes it a little less flexible
        // Germplasm doesn't have program attached. Do species as next best thing
        germplasmSearch.setCommonCropNames(List.of(brAPIProgram.getCommonCropName()));
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);
        return this.search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        );
    }

    public List<BrAPIGermplasmAttribute> getGermplasmAttributesByName(List<String> germplasmAttributeNames, BrAPIProgram program) throws ApiException {

        BrAPIGermplasmAttributeSearchRequest germplasmAttributeSearch = new BrAPIGermplasmAttributeSearchRequest();
        germplasmAttributeSearch.setAttributeNames(new ArrayList<>(germplasmAttributeNames));
        GermplasmAttributesApi api = brAPIProvider.getGermplasmAttributesApi(BrAPIClientType.CORE);
        return this.search(
                api::searchAttributesPost,
                api::searchAttributesSearchResultsDbIdGet,
                germplasmAttributeSearch
        );
    }

    public List<BrAPIGermplasm> createBrAPIGermplasm(List<BrAPIGermplasm> brAPIGermplasmList) throws ApiException {
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);
        return this.post(brAPIGermplasmList, api::germplasmPost);
    }
}
