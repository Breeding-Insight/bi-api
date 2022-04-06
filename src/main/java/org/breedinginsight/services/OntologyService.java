package org.breedinginsight.services;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.v2.model.core.BrAPIProgram;
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
import org.breedinginsight.model.SubscribedProgram;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class OntologyService {

    private ProgramDAO programDAO;
    private ProgramOntologyDAO programOntologyDAO;
    private TraitDAO traitDAO;
    private TraitService traitService;

    @Inject
    public OntologyService(ProgramDAO programDAO, ProgramOntologyDAO programOntologyDAO, TraitDAO traitDAO, TraitService traitService) {
        this.programDAO = programDAO;
        this.programOntologyDAO = programOntologyDAO;
        this.traitDAO = traitDAO;
        this.traitService = traitService;
    }

    /**
     * Gets programs available to share a programs ontology with.
     * @param programId -- Program that owns the ontology
     * @param sharedOnly -- True = return only shared programs, False = get all shareable programs
     * @return List<SharedOntologyProgram>
     */
    public List getSharedOntology(@NotNull UUID programId, @NotNull Boolean sharedOnly) throws DoesNotExistException {

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

    private Program getProgram(UUID programId) throws DoesNotExistException {
        List<Program> programs = programDAO.get(programId);
        if (programs.size() == 0) {
            throw new DoesNotExistException("Program with that id does not exist");
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
            List<UUID> traitIds = traitService.getSubscribedOntologyTraits(sharedOntologyEntity.getSharedProgramId()).stream()
                    .map(trait -> trait.getId())
                    .collect(Collectors.toList());

            // Get the brapi program id
            List<Program> program = programDAO.get(sharedOntologyEntity.getSharedProgramId());
            if (program.size() == 0) {
                throw new InternalServerException("Missing program should have been caught by now");
            }
            BrAPIProgram brAPIProgram = programDAO.getProgramBrAPI(program.get(0));

            // Get all observations for the ontology
            return traitDAO.getObservationsForTraitsByBrAPIProgram(brAPIProgram.getProgramDbId(), traitIds).isEmpty();
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
     * @return List<SharedOntologyProgram>
     */
    public List<SharedProgram> shareOntology(@NotNull UUID programId, AuthenticatedUser actingUser, List<SharedOntologyProgramRequest> programRequests) throws ValidatorException, UnprocessableEntityException, DoesNotExistException {

        // Get program with that id
        Program program = getProgram(programId);

        // Don't allow to share with self
        for (SharedOntologyProgramRequest request: programRequests) {
            if (request.getProgramId().equals(program.getId())) {
                throw new UnprocessableEntityException("Program cannot share ontology with itself");
            }
        }

        // Don't allow shared if program is already subscribe to shared ontology
        if (programOntologyDAO.getSubscribedSharedOntology(programId).isPresent()) {
            throw new UnprocessableEntityException("Program is subscribed to a shared ontology and cannot share its own.");
        }

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
                        String.format("Program %s does not have same species or brapi server.", programRequest.getProgramName()),
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
        Program program = getProgram(programId);

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

    public SubscribedProgram subscribeOntology(UUID programId, UUID sharingProgramId) throws DoesNotExistException, UnprocessableEntityException {
        // Check that program exists
        Program program = getProgram(programId);

        // Check that shared program exists
        Optional<ProgramSharedOntologyEntity> optionalSharedOntology = programOntologyDAO.getSharedOntologyById(sharingProgramId, programId);
        if (optionalSharedOntology.isEmpty()) {
            throw new DoesNotExistException("Shared ontology between specified programs was not found.");
        }
        ProgramSharedOntologyEntity sharedOntology = optionalSharedOntology.get();

        // Check that program does not have any traits of its own
        List<Trait> traits = traitDAO.getTraitsByProgramId(programId);
        if (traits.size() > 0) {
            throw new UnprocessableEntityException("Program already has traits, cannot subscribe to a shared ontology");
        }

        // Check that program does not have any current shares of its own ontology
        List<ProgramSharedOntologyEntity> sharedOntologies = programOntologyDAO.getSharedOntologies(program.getId());
        if (!sharedOntologies.isEmpty()) {
            throw new UnprocessableEntityException("Program has shared its ontology with other programs, cannot subscribe to another ontology.");
        }


        // Subscribe
        programOntologyDAO.acceptSharedOntology(sharedOntology);

        // Get the subscription record
        try {
            List<SubscribedProgram> subscribedProgramOptions = getSubscribeOntologyOptions(programId);
            for (SubscribedProgram option: subscribedProgramOptions) {
                if (option.getProgramId().equals(sharingProgramId)) {
                    return option;
                }
            }
        } catch (DoesNotExistException e) {
            throw new InternalServerException("Recently subscribed program cannot be found.");
        }

        throw new InternalServerException("Recently subscribed program cannot be found.");
    }

    public void unsubscribeOntology(UUID programId, UUID sharingProgramId) throws DoesNotExistException, UnprocessableEntityException {
        // Check that program exists
        Program program = getProgram(programId);

        // Check that shared program exists
        Optional<ProgramSharedOntologyEntity> optionalSharedOntology = programOntologyDAO.getSharedOntologyById(sharingProgramId, programId);
        if (optionalSharedOntology.isEmpty()) {
            throw new DoesNotExistException("Shared ontology between specified programs was not found.");
        }
        ProgramSharedOntologyEntity sharedOntology = optionalSharedOntology.get();

        if (!ontologyIsEditable(sharedOntology)) {
            throw new UnprocessableEntityException("Shared program has recorded observations on shared traits and cannot unsubscribe.");
        }

        // Subscribe
        programOntologyDAO.denySharedOntology(sharedOntology);
    }

    public List<SubscribedProgram> getSubscribeOntologyOptions(UUID programId) throws DoesNotExistException {

        Program program = getProgram(programId);

        List<ProgramSharedOntologyEntity> sharedOntologies = programOntologyDAO.getSubscriptionOptions(programId);
        List<Program> programs = programDAO.get(sharedOntologies.stream().map(ProgramSharedOntologyEntity::getProgramId).collect(Collectors.toList()));
        Map<UUID, Program> programMap = new HashMap<>();
        programs.forEach(sharedProgram -> programMap.put(sharedProgram.getId(), sharedProgram));

        List<SubscribedProgram> subscriptionOptions = sharedOntologies.stream()
                .map(sharedOntology -> SubscribedProgram.builder()
                        .programId(sharedOntology.getProgramId())
                        .programName(programMap.get(sharedOntology.getProgramId()).getName())
                        .subscribed(sharedOntology.getActive())
                        .editable(sharedOntology.getActive() ? ontologyIsEditable(sharedOntology) : null)
                        .build()
                ).collect(Collectors.toList());
        return subscriptionOptions;
    }

}
