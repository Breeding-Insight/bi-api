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
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.germplasm.PedigreeQueryParams;
import org.brapi.client.v2.modules.germplasm.PedigreeApi;
import org.brapi.v2.model.germ.BrAPIPedigreeNode;
import org.brapi.v2.model.germ.request.BrAPIPedigreeSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIPedigreeListResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
@Slf4j
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

    /**
     * Retrieves the pedigree of a given program and optional filters. Used by Helium. TODO: Add rest of parameters
     *
     * @param program              The program for which the pedigree is requested.
     * @param includeParents       Flag to indicate whether to include parent nodes in the pedigree. (optional)
     * @param includeSiblings      Flag to indicate whether to include sibling nodes in the pedigree. (optional)
     * @param includeProgeny       Flag to indicate whether to include progeny nodes in the pedigree. (optional)
     * @param includeFullTree      Flag to indicate whether to include the full pedigree tree or only immediate ancestors and descendants. (optional)
     * @param pedigreeDepth        The maximum depth of ancestors and descendants to include in the pedigree. (optional)
     * @param progenyDepth         The maximum depth of progeny to include in the pedigree. (optional)
     * @param germplasmName        The name of the germplasm to which the pedigree is limited. (optional)
     * @return A list of pedigree nodes representing the pedigree of the program.
     * @throws ApiException        If an error occurs while making the BrAPI call.
     */
    public List<BrAPIPedigreeNode> getPedigree(
            Program program,
            Boolean includeParents,
            Boolean includeSiblings,
            Boolean includeProgeny,
            Boolean includeFullTree,
            Integer pedigreeDepth,
            Integer progenyDepth,
            String germplasmName,
            String accessionNumber
    ) throws ApiException {

        PedigreeQueryParams pedigreeRequest = new PedigreeQueryParams();

        // TODO: Issue with BrAPI server programDbId filtering, think germplasm are linked to program through observation
        // units and doesn't work if don't have any loaded
        // use external refs instead for now
        //pedigreeSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));

        String extRefId = program.getId().toString();
        String extRefSrc = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS);
        pedigreeRequest.externalReferenceId(extRefId);
        pedigreeRequest.externalReferenceSource(extRefSrc);

        if (includeParents != null) pedigreeRequest.includeParents(includeParents);
        if (includeSiblings != null) pedigreeRequest.includeSiblings(includeSiblings);
        if (includeProgeny != null) pedigreeRequest.includeProgeny(includeProgeny);
        if (includeFullTree != null) pedigreeRequest.includeFullTree(includeFullTree);
        if (pedigreeDepth != null) pedigreeRequest.pedigreeDepth(pedigreeDepth);
        if (progenyDepth != null) pedigreeRequest.progenyDepth(progenyDepth);
        if (germplasmName != null) pedigreeRequest.germplasmName(germplasmName);
        if (accessionNumber != null) pedigreeRequest.accessionNumber(accessionNumber);
        // TODO: other parameters

        // TODO: write utility to do paging instead of hardcoding
        pedigreeRequest.pageSize(100000);

        ApiResponse<BrAPIPedigreeListResponse> brapiPedigree;
        try {
            brapiPedigree = brAPIEndpointProvider
                    .get(programDAO.getCoreClient(program.getId()), PedigreeApi.class)
                    .pedigreeGet(pedigreeRequest);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        List<BrAPIPedigreeNode> pedigreeNodes = brapiPedigree.getBody().getResult().getData();
        // TODO: once Helium is constructing nodes from DbId we can strip program keys but won't in the mean time
        //stripProgramKeys(pedigreeNodes, program.getKey());
        return pedigreeNodes;
    }

    /**
     * Searches for pedigree nodes based on the given parameters. Not used by Helium but keeping commented out for
     * now in case we want to implement the search endpoints in the future, work here has already been started to
     * support that.
     *
     * TODO: Add rest of parameters
     *
     * @param program           The program to search for pedigree nodes.
     * @param includeParents    Optional boolean to include parents in the search.
     * @param includeSiblings   Optional boolean to include siblings in the search.
     * @param includeProgeny    Optional boolean to include progeny in the search.
     * @param includeFullTree   Optional boolean to include the full pedigree tree in the search.
     * @param pedigreeDepth     Optional integer for the maximum depth of the pedigree tree.
     * @param progenyDepth      Optional integer for the maximum depth of the progeny tree.
     * @param germplasmName     Optional String to filter the search by germplasm name.
     * @return A List of BrAPIPedigreeNode objects that match the search criteria.
     * @throws ApiException     If an error occurs while searching for pedigree nodes.
     */
    /*
    public List<BrAPIPedigreeNode> searchPedigree(Program program,
                                                  Optional<Boolean> includeParents,
                                                  Optional<Boolean> includeSiblings,
                                                  Optional<Boolean> includeProgeny,
                                                  Optional<Boolean> includeFullTree,
                                                  Optional<Integer> pedigreeDepth,
                                                  Optional<Integer> progenyDepth,
                                                  Optional<String> germplasmName
    ) throws ApiException {

        BrAPIPedigreeSearchRequest pedigreeSearchRequest = new BrAPIPedigreeSearchRequest();
        // TODO: Issue with BrAPI server programDbId filtering, think germplasm are linked to program through observation
        // units and doesn't work if don't have any loaded
        // use external refs instead for now
        //pedigreeSearchRequest.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));

        // Just use program UUID, shouldn't have any collisions and don't want to get all the germplasm if we were also
        // using source because search is OR rather than AND
        String extRefId = program.getId().toString();
        pedigreeSearchRequest.addExternalReferenceIdsItem(extRefId);

        includeParents.ifPresent(pedigreeSearchRequest::includeParents);
        includeSiblings.ifPresent(pedigreeSearchRequest::includeSiblings);
        includeProgeny.ifPresent(pedigreeSearchRequest::includeProgeny);
        includeFullTree.ifPresent(pedigreeSearchRequest::includeFullTree);
        pedigreeDepth.ifPresent(pedigreeSearchRequest::setPedigreeDepth);
        progenyDepth.ifPresent(pedigreeSearchRequest::setProgenyDepth);
        germplasmName.ifPresent(pedigreeSearchRequest::addGermplasmNamesItem);
        // TODO: other parameters

        PedigreeApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), PedigreeApi.class);

        List<BrAPIPedigreeNode> pedigreeNodes = brAPIDAOUtil.search(
                api::searchPedigreePost,
                api::searchPedigreeSearchResultsDbIdGet,
                pedigreeSearchRequest
        );

        // TODO: once Helium is constructing nodes from DbId we can strip program keys but won't in the mean time
        //stripProgramKeys(pedigreeNodes, program.getKey());

        return pedigreeNodes;
    }
     */

    /**
     * Removes the program key from the germplasm names in the list of pedigree nodes. Not used currently but will be in
     * future if we decide to strip the program keys when Helium has been updated to use the germplasmDbId for uniqueness
     * rather than germplasmName.
     *
     * @param pedigreeNodes The list of pedigree nodes.
     * @param programKey The program key to be removed.
     */
    private void stripProgramKeys(List<BrAPIPedigreeNode> pedigreeNodes, String programKey) {
        pedigreeNodes.forEach(node -> {
            node.setGermplasmName(Utilities.removeProgramKeyAnyAccession(node.getGermplasmName(), programKey));
            // TODO: pedigree stripping not working right
            //node.setPedigreeString(Utilities.removeProgramKeyAnyAccession(node.getPedigreeString(), programKey));
            if (node.getParents() != null) {
                node.getParents().forEach(parent -> {
                    parent.setGermplasmName(Utilities.removeProgramKeyAnyAccession(parent.getGermplasmName(), programKey));
                });
            }
            if (node.getProgeny() != null) {
                node.getProgeny().forEach(progeny -> {
                    progeny.setGermplasmName(Utilities.removeProgramKeyAnyAccession(progeny.getGermplasmName(), programKey));
                });
            }
        });
    }

}