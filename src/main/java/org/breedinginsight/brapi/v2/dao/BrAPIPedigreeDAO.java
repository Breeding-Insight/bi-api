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

package org.breedinginsight.brapi.v2.dao;

import io.micronaut.context.annotation.Property;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.PedigreeApi;
import org.brapi.v2.model.germ.BrAPIPedigreeNode;
import org.brapi.v2.model.germ.request.BrAPIPedigreeSearchRequest;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class BrAPIPedigreeDAO {

    private ProgramDAO programDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final String referenceSource;

    @Inject
    public BrAPIPedigreeDAO(ProgramDAO programDAO, BrAPIDAOUtil brAPIDAOUtil,
                            BrAPIEndpointProvider brAPIEndpointProvider,
                            @Property(name = "brapi.server.reference-source") String referenceSource) {
        this.programDAO = programDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
    }

    public List<BrAPIPedigreeNode> getPedigree(Program program) throws ApiException {
        // TODO: maybe use get instead of search

        BrAPIPedigreeSearchRequest pedigreeSearchRequest = new BrAPIPedigreeSearchRequest();
        // TODO: Issue with BrAPI server programDbId filtering, use external refs instead for now
        //pedigreeSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));

        String extRefId = program.getId().toString();
        String extRefSrc = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS);
        pedigreeSearchRequest.addExternalReferenceIdsItem(extRefId);
        pedigreeSearchRequest.addExternalReferenceSourcesItem(extRefSrc);

        // TODO: add pagination support
        // .page(page)
        // .pageSize(pageSize);
        // TODO: other parameters

        PedigreeApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), PedigreeApi.class);

        List<BrAPIPedigreeNode> pedigreeNodes = brAPIDAOUtil.search(
                api::searchPedigreePost,
                api::searchPedigreeSearchResultsDbIdGet,
                pedigreeSearchRequest
        );

        // search on external references is id OR source, need to filter for id AND source
        pedigreeNodes = pedigreeNodes.stream()
                .filter(node -> node.getExternalReferences().stream()
                        .anyMatch(ref -> ref.getReferenceSource().equals(extRefSrc) && ref.getReferenceId().equals(extRefId)))
                .collect(Collectors.toList());

        return pedigreeNodes;
    }

}