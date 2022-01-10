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

package org.breedinginsight.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListsListResponse;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class GermplasmService {

    private ProgramService programService;
    private UserService userService;
    private DSLContext dsl;
    private BrAPIListDAO brAPIListDAO;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Inject
    public GermplasmService(BrAPIListDAO brAPIListDAO, ProgramService programService, UserService userService, DSLContext dsl) {
        this.brAPIListDAO = brAPIListDAO;
        this.programService = programService;
        this.userService = userService;
        this.dsl = dsl;
    }

    public BrAPIListsListResponse getGermplasmListsByProgramId(UUID programId, HttpRequest<String> request) throws DoesNotExistException, ApiException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

        Optional<Program> optionalProgram = programService.getById(programId);
        if(optionalProgram.isPresent()) {
            Program program = optionalProgram.get();
            String appendedKey = String.format(" [%s-germplasm]", program.getKey());

            BrAPIListsListResponse germplasmLists = brAPIListDAO.getListByTypeAndExternalRef(BrAPIListTypes.GERMPLASM, programId, referenceSource + "/programs", programId);

            //Remove key appended to listName for brapi
            String listName;
            String newListName;
            int listLength = germplasmLists.getResult().getData().size();
            for (int i=0; i<listLength; i++) {
                listName = germplasmLists.getResult().getData().get(i).getListName();
                newListName = listName.replace(appendedKey, "");
                germplasmLists.getResult().getData().get(i).setListName(newListName);
            }

            return germplasmLists;
        }
        else {
            throw new DoesNotExistException("Program does not exist");
        }
    }
}
