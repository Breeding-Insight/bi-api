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
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SharedProgram;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class OntologyService {

    private ProgramDAO programDAO;
    private ProgramOntologyDAO programOntologyDAO;
    private TraitDAO traitDAO;

    @Inject
    public OntologyService(ProgramDAO programDAO, ProgramOntologyDAO programOntologyDAO, TraitDAO traitDAO) {
        this.programDAO = programDAO;
        this.programOntologyDAO = programOntologyDAO;
        this.traitDAO = traitDAO;
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

        List<SharedProgram> formattedPrograms = getSharedProgramsFormatted(program);
        Set<UUID> sharedProgramIds = formattedPrograms.stream().map(SharedProgram::getProgramId).collect(Collectors.toSet());

        // Add other programs
        if (!sharedOnly) {
            // TODO: Test if localhost vs localhost/brapi/v2 makes a difference
            List<Program> matchingPrograms = getMatchingPrograms(program);
            formattedPrograms.addAll(matchingPrograms.stream()
                    .filter(matchingProgram -> !sharedProgramIds.contains(matchingProgram.getId()))
                    .map(matchingProgram -> formatResponse(matchingProgram))
                    .collect(Collectors.toList()));
        }

        return formattedPrograms;
    }

    private List<SharedProgram> getSharedProgramsFormatted(Program program) {
        // Get shared ontology records
        List<ProgramSharedOntologyEntity> sharedOntologies = programOntologyDAO.getSharedOntologies(program.getId());
        // Get the programs ontology is shared with
        List<Program> sharedPrograms = programDAO.get(
                sharedOntologies.stream().map(ProgramSharedOntologyEntity::getSharedProgramId).collect(Collectors.toList()));
        // Get the programs in a lookup map
        Map<UUID, Program> sharedProgramsMap = new HashMap<>();
        sharedPrograms.stream().forEach(sharedProgram -> sharedProgramsMap.put(sharedProgram.getId(), sharedProgram));

        // Format shared programs response
        return sharedOntologies.stream().map(sharedOntology ->
                formatResponse(sharedProgramsMap.get(sharedOntology.getSharedProgramId()), sharedOntology,
                        ontologyIsEditable(sharedOntology)))
                .collect(Collectors.toList());
    }

    private List<Program> getMatchingPrograms(Program program) {
        List<Program> allPrograms = programDAO.getAll();
        List<Program> matchingPrograms = new ArrayList<>();
        for (Program candidate: allPrograms) {

            if (candidate.getSpecies().getId().equals(program.getSpecies().getId()) &&
                candidate.getBrapiUrl().equals(program.getBrapiUrl()) &&
                !candidate.getId().equals(program.getId())
            ) {

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

    private SharedProgram formatResponse(Program program, ProgramSharedOntologyEntity programSharedOntologyEntity, Boolean editable) {
        return SharedProgram.builder()
                .programId(program.getId())
                .programName(program.getName())
                .shared(true)
                .editable(editable)
                .accepted(programSharedOntologyEntity.getActive())
                .build();
    }

    private SharedProgram formatResponse(Program program) {
        return SharedProgram.builder()
                .programId(program.getId())
                .programName(program.getName())
                .shared(false)
                .build();
    }

    private Boolean ontologyIsEditable(ProgramSharedOntologyEntity sharedOntologyEntity) {

        if (sharedOntologyEntity.getActive()) {
            // Get all trait ids for the program
            List<UUID> traitIds = traitDAO.getTraitsByProgramId(sharedOntologyEntity.getSharedProgramId()).stream()
                    .map(trait -> trait.getId())
                    .collect(Collectors.toList());

            // Get all observations for the ontology
            return traitDAO.getObservationsForTraits(traitIds).isEmpty();
        } else {
            return true;
        }
    }


    /**
     * Processes share requests for a list of programs. Will return a ValidationError if there are issues
     * with any of the shared requests.
     *
     * @param programId -- Program that owns the ontology
     * @param programRequests -- List of programs to share ontology with
     * @return Lst<SharedOntologyProgram>
     */
    public List<SharedProgram> shareOntology(@NotNull UUID programId, AuthenticatedUser actingUser, List<SharedOntologyProgramRequest> programRequests) throws ValidatorException, UnprocessableEntityException {

        // Get program with that id
        Program program = getProgram(programId);

        // Don't allow to share with self
        for (SharedOntologyProgramRequest request: programRequests) {
            if (request.getProgramId().equals(program.getId())) {
                throw new UnprocessableEntityException("Program cannot share ontology with itself");
            }
        }

        // Don't allow shared if program is already subscribe to shared ontology


        // Check shareability, same brapi server, same species
        List<Program> matchingPrograms = getMatchingPrograms(program);
        Set<UUID> matchingProgramsSet = new HashSet<>();
        matchingPrograms.stream().forEach(matchingProgram -> matchingProgramsSet.add(matchingProgram.getId()));

        Set<UUID> shareProgramIdsSet = new HashSet<>();
        ValidationErrors validationErrors = new ValidationErrors();
        for (int i = 0; i < programRequests.size(); i++) {
            SharedOntologyProgramRequest programRequest = programRequests.get(i);
            if (!matchingProgramsSet.contains(programRequest.getProgramId())) {
                ValidationError error = new ValidationError("program",
                        "Program does not have same species or brapi server.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
                validationErrors.addError(i, error);
            } else {
                shareProgramIdsSet.add(programRequest.getProgramId());
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
        return getSharedProgramsFormatted(program).stream()
                .filter(sharedProgram -> shareProgramIdsSet.contains(sharedProgram.getProgramId()))
                .collect(Collectors.toList());
    }

    /**
     * Removes ontology sharing from the specific program.
     * @param programId -- Program that owns the ontology.
     * @param sharedProgramId -- Program to revoke shared ontology access from
     */
    public void revokeOntology(@NotNull UUID programId, @NotNull UUID sharedProgramId) throws UnprocessableEntityException, DoesNotExistException {
        // Check that program exists

        // Check that shared program exists
        Optional<ProgramSharedOntologyEntity> optionalSharedOntology = programOntologyDAO.getSharedOntologyById(programId, sharedProgramId);
        if (optionalSharedOntology.isEmpty()) {
            throw new DoesNotExistException("Shared program id was not found");
        }
        ProgramSharedOntologyEntity sharedOntology = optionalSharedOntology.get();

        // Check that shared program is still editable. No observations yet.
        if (!ontologyIsEditable(sharedOntology)) {
            throw new UnprocessableEntityException("Shared ontology can not be removed from this program.");
        }

        // Remove record from db
        programOntologyDAO.revokeSharedOntology(sharedOntology);
    }
}
