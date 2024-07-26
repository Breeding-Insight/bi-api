package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.daos.ExperimentalCollaboratorDAO;

import javax.inject.Inject;

@Slf4j
public class ExperimentalCollaboratorService {

    private final ExperimentalCollaboratorDAO experimentalCollaboratorDAO;

    @Inject
    public ExperimentalCollaboratorService(ExperimentalCollaboratorDAO experimentalCollaboratorDAO) {
        this.experimentalCollaboratorDAO = experimentalCollaboratorDAO;
    }
}
