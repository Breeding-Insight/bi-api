package org.breedinginsight.services;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.SharedOntologyProgramRequest;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.tables.pojos.ProgramSharedOntologyEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.ProgramOntologyDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SharedProgram;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class OntologyService {

    private ProgramDAO programDAO;
    private ProgramOntologyDAO programOntologyDAO;

    public OntologyService(ProgramDAO programDAO, ProgramOntologyDAO programOntologyDAO) {
        this.programDAO = programDAO;
        this.programOntologyDAO = programOntologyDAO;
    }

    /**
     * Gets programs available to share a programs ontology with.
     * @param programId -- Program that owns the ontology
     * @param sharedOnly -- True = return only shared programs, False = get all shareable programs
     * @return List<SharedOntologyProgram>
     */
    public List getSharedOntology(@NotNull UUID programId, @NotNull Boolean sharedOnly) {

        // Get program with that id
        Program program = getProgram(programId);

        // Get programs of same species and brapi server from db
        // TODO: Test if localhost vs localhost/brapi/v2 makes a difference
        List<Program> matchingPrograms = getMatchingPrograms(program);

        // Get shared ontology programs for db
        List<Program> sharedPrograms = programOntologyDAO.getSharedPrograms(program.getId());

        // Format response
        // Store shared programs in map for lookup
        Map<UUID, Program> sharedProgramsMap = new HashMap<>();
        sharedPrograms.stream().forEach(sharedProgram -> sharedProgramsMap.put(sharedProgram.getId(), sharedProgram));

        // Loop through or matching programs, formatting and labeling shared ones.
        // TODO: Need to check if these programs have observations
        List<SharedProgram> formattedPrograms = new ArrayList<>();
        for (Program matchingProgram: matchingPrograms) {
            SharedProgram formattedProgram = formatResponse(matchingProgram,
                    sharedProgramsMap.containsKey(matchingProgram.getId()), false);

            formattedPrograms.add(formattedProgram);
        }


        return formattedPrograms;
    }

    private List<Program> getMatchingPrograms(Program program) {
        List<Program> allPrograms = programDAO.getAll();
        List<Program> matchingPrograms = new ArrayList<>();
        for (Program candidate: allPrograms) {

            if (candidate.getSpecies().getId().equals(program.getSpecies().getId()) &&
                candidate.getBrapiUrl().equals(program.getBrapiUrl())) {

                matchingPrograms.add(candidate);
            }
        }
        return matchingPrograms;
    }

    private Program getProgram(UUID programId) {
        List<Program> programs = programDAO.get(programId);
        if (programs.size() == 0) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Program with that id does not exist");
        }
        return programs.get(0);
    }

    private SharedProgram formatResponse(Program program, Boolean shared, Boolean editable) {
        return SharedProgram.builder()
                .program_id(program.getId())
                .program_name(program.getName())
                .shared(shared)
                .build();
    }


    /**
     * Processes share requests for a list of programs. Will return a ValidationError if there are issues
     * with any of the shared requests.
     *
     * @param programId -- Program that owns the ontology
     * @param programRequests -- List of programs to share ontology with
     * @return Lst<SharedOntologyProgram>
     */
    public List shareOntology(@NotNull UUID programId, AuthenticatedUser actingUser, List<SharedOntologyProgramRequest> programRequests) throws ValidatorException {
        // Get program with that id
        Program program = getProgram(programId);

        // Check shareability, same brapi server, same species
        List<Program> matchingPrograms = getMatchingPrograms(program);
        Set<UUID> matchingProgramsSet = new HashSet<>();
        matchingPrograms.stream().forEach(matchingProgram -> matchingProgramsSet.add(matchingProgram.getId()));

        Set<UUID> shareProgramIdsSet = new HashSet<>();
        ValidationErrors validationErrors = new ValidationErrors();
        for (int i = 0; i < programRequests.size(); i++) {
            SharedOntologyProgramRequest programRequest = programRequests.get(i);
            if (!matchingProgramsSet.contains(programRequest.getId())) {
                ValidationError error = new ValidationError("program",
                        "Program does not have same species or brapi server.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
                validationErrors.addError(i, error);
            } else {
                shareProgramIdsSet.add(programRequest.getId());
            }
        }

        if (validationErrors.hasErrors()) {
            throw new ValidatorException(validationErrors);
        }

        // Add shared record to DB
        List<ProgramSharedOntologyEntity> shareRecords = new ArrayList<>();
        for (UUID shareProgramId: shareProgramIdsSet){
            ProgramSharedOntologyEntity programSharedOntologyEntity = ProgramSharedOntologyEntity.builder()
                    .programId(programId)
                    .sharedProgramId(shareProgramId)
                    .updatedBy(actingUser.getId())
                    .createdBy(actingUser.getId())
                    .build();
            shareRecords.add(programSharedOntologyEntity);
        }
        programOntologyDAO.createSharedOntologies(shareRecords);

        // Query return data
        List<Program> allSharedPrograms = programOntologyDAO.getSharedPrograms(programId);
        List<Program> newSharedPrograms = allSharedPrograms.stream()
                .filter(sharedProgram -> shareProgramIdsSet.contains(sharedProgram.getId()))
                .collect(Collectors.toList());
        List<SharedProgram> sharedPrograms = newSharedPrograms.stream().map(
                newSharedProgram -> formatResponse(newSharedProgram, false, true)
        ).collect(Collectors.toList());

        return sharedPrograms;
    }

    /**
     * Removes ontology sharing from the specific program.
     * @param programId -- Program that owns the ontology.
     * @param sharedProgramId -- Program to revoke shared ontology access from
     */
    public void revokeOntology(@NotNull UUID programId, @NotNull UUID sharedProgramId) {
        // TODO: Check that shared program is still unshareable. No observations yet.

        // TODO: Remove record from db

    }
}
