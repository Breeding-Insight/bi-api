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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Context
public class BrAPIGermplasmDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    ProgramCache<BrAPIGermplasm> programGermplasmCache;

    @Inject
    public BrAPIGermplasmDAO(ProgramDAO programDAO, ImportDAO importDAO) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
    }

    @PostConstruct
    private void setup() {
        // Populate germplasm cache for all programs on startup
        List<UUID> programs = programDAO.getAll().stream().map(Program::getId).collect(Collectors.toList());
        programGermplasmCache = new ProgramCache<>((programId) -> fetchProgramGermplasm((UUID) programId), programs);
    }

    public List<BrAPIGermplasm> getGermplasm(UUID programId) throws ApiException {
        return programGermplasmCache.get(programId);
    }

    private List<BrAPIGermplasm> fetchProgramGermplasm(UUID programId) throws ApiException {
        GermplasmApi api = new GermplasmApi(programDAO.getCoreClient(programId));

        // Set query params and make call
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.externalReferenceIDs(Arrays.asList(programId.toString()));
        germplasmSearch.externalReferenceSources(Arrays.asList(String.format("%s/programs", referenceSource)));
        return BrAPIDAOUtil.search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        );
    }

    public List<BrAPIGermplasm> importBrAPIGermplasm(List<BrAPIGermplasm> brAPIGermplasmList, UUID programId, ImportUpload upload) throws ApiException {
        GermplasmApi api = new GermplasmApi(programDAO.getCoreClient(programId));
        try {
            Callable<List<BrAPIGermplasm>> postFunction = () -> BrAPIDAOUtil.post(brAPIGermplasmList, upload, api::germplasmPost, importDAO::update);
            List<BrAPIGermplasm> newGermplasm = programGermplasmCache.post(programId, postFunction);
            return newGermplasm;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage());
        }
    }

    public List<BrAPIGermplasm> getGermplasmByName(List<String> germplasmNames, UUID programId) throws ApiException {
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.germplasmNames(germplasmNames);
        GermplasmApi api = new GermplasmApi(programDAO.getCoreClient(programId));
        return BrAPIDAOUtil.search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        );
    }
}
