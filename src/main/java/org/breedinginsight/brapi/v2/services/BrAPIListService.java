package org.breedinginsight.brapi.v2.services;

import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class BrAPIListService {
    private final String referenceSource;
    private final BrAPITrialDAO trialDAO;
    private final BrAPIObservationDAO observationDAO;
    private final BrAPIListDAO listDAO;
    private final BrAPIObservationVariableDAO obsVarDAO;
    private final BrAPIStudyDAO studyDAO;
    private final BrAPISeasonDAO seasonDAO;
    private final BrAPIObservationUnitDAO ouDAO;
    private final BrAPIGermplasmDAO germplasmDAO;

    @Inject
    public BrAPIListService(@Property(name = "brapi.server.reference-source") String referenceSource,
                            BrAPITrialDAO trialDAO,
                            BrAPIObservationDAO observationDAO,
                            BrAPIListDAO listDAO,
                            BrAPIObservationVariableDAO obsVarDAO,
                            BrAPIStudyDAO studyDAO,
                            BrAPISeasonDAO seasonDAO,
                            BrAPIObservationUnitDAO ouDAO,
                            BrAPIGermplasmDAO germplasmDAO) {

        this.referenceSource = referenceSource;
        this.trialDAO = trialDAO;
        this.observationDAO = observationDAO;
        this.listDAO = listDAO;
        this.obsVarDAO = obsVarDAO;
        this.studyDAO = studyDAO;
        this.seasonDAO = seasonDAO;
        this.ouDAO = ouDAO;
        this.germplasmDAO = germplasmDAO;
    }

    public List<BrAPIListSummary> getListSummariesByTypeAndXref(
            BrAPIListTypes type,
            ExternalReferenceSource xrefSource,
            UUID xrefId,
            Program program) throws ApiException, DoesNotExistException, ClassNotFoundException {
        List<BrAPIListSummary> lists = listDAO.getListByTypeAndExternalRef(
                type,
                program.getId(),
                String.format("%s/%s", referenceSource, xrefSource.getName()),
                xrefId);
        if (lists == null || lists.isEmpty()) {
            throw new DoesNotExistException("list not returned from BrAPI service");
        }

        for (BrAPIListSummary list: lists) {

            // remove the program key from the list name
            list.setListName(Utilities.removeProgramKeyAndUnknownAdditionalData(list.getListName(), program.getKey()));

            // set the owner of the list items as the list owner
            BrAPIListsSingleResponse listDetails = listDAO.getListById(list.getListDbId(), program.getId());
            List<String> listItemNames = listDetails.getResult().getData();
            switch (type) {
                case OBSERVATIONVARIABLES:
                    break;
                case GERMPLASM:
                default:
                    String createdBy = germplasmDAO.getGermplasmByRawName(listItemNames, program.getId()).get(0)
                            .getAdditionalInfo()
                            .getAsJsonObject("createdBy")
                            .get("userName")
                            .getAsString();
                    list.setListOwnerName(createdBy);
            }

        }

        return lists;
    }
}
