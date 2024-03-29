package org.breedinginsight.services;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.SharedOntologyProgramRequest;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.DataTypeTranslator;
import org.breedinginsight.brapps.importer.model.imports.TermTypeTranslator;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.ProgramSharedOntologyEntity;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.ProgramOntologyDAO;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.*;
import org.breedinginsight.services.parsers.trait.TraitFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.services.writers.ExcelWriter;

import org.jooq.exception.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class OntologyService {

    private final ProgramDAO programDAO;
    private final ProgramOntologyDAO programOntologyDAO;
    private final TraitDAO traitDAO;
    private final TraitService traitService;
    private final TraitUploadService traitUploadService;

    @Inject
    public OntologyService(ProgramDAO programDAO, ProgramOntologyDAO programOntologyDAO, TraitDAO traitDAO, TraitService traitService, TraitUploadService traitUploadService) {
        this.programDAO = programDAO;
        this.programOntologyDAO = programOntologyDAO;
        this.traitDAO = traitDAO;
        this.traitService = traitService;
        this.traitUploadService = traitUploadService;
    }

    /**
     * Gets programs available to share a programs ontology with.
     * @param programId -- Program that owns the ontology
     * @param sharedOnly -- True = return only shared programs, False = get all shareable programs
     * @return List<SharedOntologyProgram>
     */
    public List<SharedOntology> getSharedOntology(@NotNull UUID programId, @NotNull Boolean sharedOnly) throws DoesNotExistException {

        // Get program with that id
        Program program = getProgram(programId);

        // Get any programs targeted for sharing by this program
        List<SharedOntology> formattedPrograms = getSharedProgramsFormatted(program);
        Set<UUID> sharedProgramIds = formattedPrograms.stream().map(SharedOntology::getProgramId).collect(Collectors.toSet());

        // Add other sharable programs
        if (!sharedOnly) {
            // TODO: Test if localhost vs localhost/brapi/v2 makes a difference
            List<Program> matchingPrograms = getMatchingPrograms(program);

            // Collect the ids of any matching programs that are either sharing-sources or
            // sharing-targets that have accepted
            Set<UUID> shareSourceIds = new HashSet<>();
            Set<UUID> shareTargetIds = new HashSet<>();
            for (Program candidate: matchingPrograms) {
                List<SharedOntology> shareTargetsFormatted = getSharedProgramsFormatted(candidate);
                if (!shareTargetsFormatted.isEmpty()) {

                    // The program is sharing its ontology, so collect its id as a source
                    shareSourceIds.add(candidate.getId());

                    // Collect the ids of target-programs that have accepted
                    shareTargetIds.addAll(shareTargetsFormatted
                            .stream()
                            .filter(SharedOntology::getAccepted)
                            .map(SharedOntology::getProgramId)
                            .collect(Collectors.toSet())
                    );
                }
            }

            Set<UUID> unsharableIds = new HashSet<>();
            unsharableIds.addAll(sharedProgramIds);
            unsharableIds.addAll(shareSourceIds);
            unsharableIds.addAll(shareTargetIds);
            formattedPrograms.addAll(matchingPrograms.stream()
                    .filter(matchingProgram -> !unsharableIds.contains(matchingProgram.getId()))
                    .map(this::formatResponse)
                    .collect(Collectors.toList()));
        }

        return formattedPrograms;
    }

    private List<SharedOntology> getSharedProgramsFormatted(Program program) {
        // Get shared ontology records
        List<ProgramSharedOntologyEntity> sharedOntologies = programOntologyDAO.getSharedOntologies(program.getId());
        // Get the programs ontology is shared with
        List<Program> sharedPrograms = programDAO.get(
                sharedOntologies.stream().map(ProgramSharedOntologyEntity::getSharedProgramId).collect(Collectors.toList()));
        // Get the programs in a lookup map
        Map<UUID, Program> sharedProgramsMap = new HashMap<>();
        sharedPrograms.forEach(sharedProgram -> sharedProgramsMap.put(sharedProgram.getId(), sharedProgram));

        // Format shared programs response
        return sharedOntologies.stream().map(sharedOntology ->
                formatResponse(sharedProgramsMap.get(sharedOntology.getSharedProgramId()), sharedOntology,
                        ontologyIsEditable(sharedOntology)))
                .collect(Collectors.toList());
    }

    private List<Program> getMatchingPrograms(Program program) {
        List<Program> allPrograms = programDAO.getActive();
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

    private SharedOntology formatResponse(Program program, ProgramSharedOntologyEntity programSharedOntologyEntity, Boolean editable) {
        return SharedOntology.builder()
                .programId(program.getId())
                .programName(program.getName())
                .shared(true)
                .editable(editable)
                .accepted(programSharedOntologyEntity.getActive())
                .build();
    }

    private SharedOntology formatResponse(Program program) {
        return SharedOntology.builder()
                .programId(program.getId())
                .programName(program.getName())
                .shared(false)
                .build();
    }

    private Boolean ontologyIsEditable(ProgramSharedOntologyEntity sharedOntologyEntity) {
        if (sharedOntologyEntity.getActive()) {
            // Get all trait ids for the program
            List<UUID> traitIds = traitService.getSubscribedOntologyTraits(sharedOntologyEntity.getSharedProgramId()).stream()
                    .map(TraitEntity::getId)
                    .collect(Collectors.toList());

            // Get the brapi program id
            List<Program> program = programDAO.get(sharedOntologyEntity.getSharedProgramId());
            if (program.size() == 0) {
                throw new InternalServerException("Missing program should have been caught by now");
            }
            BrAPIProgram brAPIProgram = programDAO.getProgramBrAPI(program.get(0));

            // Get all observations for the ontology
            return traitDAO.getObservationsForTraitsByBrAPIProgram(brAPIProgram.getProgramDbId(), program.get(0).getId(), traitIds).isEmpty();
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
    public List<SharedOntology> shareOntology(@NotNull UUID programId, AuthenticatedUser actingUser, List<SharedOntologyProgramRequest> programRequests) throws ValidatorException, UnprocessableEntityException, DoesNotExistException {

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
        matchingPrograms.forEach(matchingProgram -> matchingProgramsSet.add(matchingProgram.getId()));

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
                .filter(sharedOntology -> shareProgramIdsSet.contains(sharedOntology.getProgramId()))
                .collect(Collectors.toList());
    }

    /**
     * Removes ontology sharing from the specific program.
     * @param programId -- Program that owns the ontology.
     * @param sharedProgramId -- Program to revoke shared ontology access from
     */
    public void revokeOntology(@NotNull UUID programId, @NotNull UUID sharedProgramId) throws UnprocessableEntityException, DoesNotExistException {
        // Check that program exists
        getProgram(programId);

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

    public SubscribedOntology subscribeOntology(UUID programId, UUID sharingProgramId) throws DoesNotExistException, UnprocessableEntityException {
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
            List<SubscribedOntology> subscribedOntologyOptions = getSubscribeOntologyOptions(programId);
            for (SubscribedOntology option: subscribedOntologyOptions) {
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
        getProgram(programId);

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

    public List<Trait> getTraitsByProgramId(UUID programId, boolean getFullTrait) throws DoesNotExistException {
        return traitService.getByProgramId(getSubscribedOntologyProgramId(programId), getFullTrait);
    }

    public List<Trait> updateTraits(UUID programId, List<Trait> traits, AuthenticatedUser actingUser)
            throws DoesNotExistException, HttpStatusException, ValidatorException, BadRequestException {
        UUID lookupId = getSubscribedOntologyProgramId(programId);
        if(lookupId != programId) {
            throw new BadRequestException("Subscribed ontology terms cannot be updated");
        }

        return traitService.updateTraits(lookupId, traits, actingUser);
    }


    public DownloadFile exportOntology(UUID programId, FileType fileExtension, boolean isActive) throws IllegalArgumentException, IOException, java.io.IOException {
        List<Column> columns = TraitFileColumns.getOrderedColumns();

        //Retrieve trait list data
        List<Trait> traits = traitDAO.getTraitsFullByProgramId(programId);
        //Filter traits for Active or Archived
        traits = traits.stream().filter(trait -> trait.getActive()==isActive).collect(Collectors.toList());
        //Sort list in default (trait name) order.
        traits.sort(Comparator.comparing(trait -> trait.getObservationVariableName().toLowerCase()));

        // make file Name
        String fileName = makeFileName(programId, isActive);

        StreamedFile downloadFile;

        //Convert traits list to List<Map<String, Object>> data to pass into file writer
        List<Map<String, Object>> processedData = processData(traits);

        if (fileExtension == FileType.CSV){
            downloadFile = CSVWriter.writeToDownload(columns, processedData, fileExtension);
        } else {
            downloadFile = ExcelWriter.writeToDownload("Data", columns, processedData, fileExtension);
        }
        return new DownloadFile(fileName, downloadFile);
    }

    private String makeFileName(UUID programId, boolean isActive) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
        String timestamp = formatter.format(OffsetDateTime.now());
        Program program = null;
        try {
            program = getProgram(programId);
        } catch (DoesNotExistException e) {
            e.printStackTrace();
        }
        String activeOrArchive = isActive ? "Active" : "Archive";
        String programName = program==null? "program" : program.getName();
        return programName + "_" + activeOrArchive + "_Ontology_" + timestamp;
    }

    public List<Trait> createTraits(UUID programId, List<Trait> traits, AuthenticatedUser actingUser, Boolean throwDuplicateErrors)
            throws DoesNotExistException, ValidatorException, BadRequestException {
        UUID lookupId = getSubscribedOntologyProgramId(programId);
        if(lookupId != programId) {
            throw new BadRequestException("Subscribed ontology terms cannot be created");
        }

        return traitService.createTraits(lookupId, traits, actingUser, throwDuplicateErrors);
    }

    public ProgramUpload updateTraitUpload(UUID programId, CompletedFileUpload file, AuthenticatedUser actingUser)
            throws DoesNotExistException, UnsupportedTypeException, ValidatorException, AuthorizationException, HttpStatusException, BadRequestException {
        UUID lookupId = getSubscribedOntologyProgramId(programId);
        if(lookupId != programId) {
            throw new BadRequestException("Subscribed ontology terms cannot be imported");
        }

        return traitUploadService.updateTraitUpload(lookupId, file, actingUser);
    }

    public void confirmUpload(UUID programId, UUID traitUploadId, AuthenticatedUser actingUser)
            throws DoesNotExistException, ValidatorException, BadRequestException {
        UUID lookupId = getSubscribedOntologyProgramId(programId);
        if(lookupId != programId) {
            throw new BadRequestException("Subscribed ontology terms cannot be imported");
        }

        traitUploadService.confirmUpload(lookupId, traitUploadId, actingUser);
    }

    public UUID getSubscribedOntologyProgramId(UUID programId) throws DoesNotExistException {
        // get shared ontology programs
        List<SubscribedOntology> subscriptionOptions = getSubscribeOntologyOptions(programId);

        // check if we are subscribed to one of the programs
        SubscribedOntology subscribedOntology = null;
        for (SubscribedOntology sharingProgram : subscriptionOptions) {
            if (sharingProgram.getSubscribed()) {
                subscribedOntology = sharingProgram;
            }
        }

        UUID lookupId = subscribedOntology == null ? programId : subscribedOntology.getProgramId();

        return lookupId;
    }

    public List<SubscribedOntology> getSubscribeOntologyOptions(UUID programId) throws DoesNotExistException {
        // Check that program exists
        getProgram(programId);

        List<ProgramSharedOntologyEntity> sharedOntologies = programOntologyDAO.getSubscriptionOptions(programId);
        List<Program> programs = programDAO.get(sharedOntologies.stream().map(ProgramSharedOntologyEntity::getProgramId).collect(Collectors.toList()));
        Map<UUID, Program> programMap = new HashMap<>();
        programs.forEach(sharedProgram -> programMap.put(sharedProgram.getId(), sharedProgram));

        List<SubscribedOntology> subscriptionOptions = sharedOntologies.stream()
                .map(sharedOntology -> SubscribedOntology.builder()
                        .programId(sharedOntology.getProgramId())
                        .programName(programMap.get(sharedOntology.getProgramId()).getName())
                        .subscribed(sharedOntology.getActive())
                        .editable(sharedOntology.getActive() ? ontologyIsEditable(sharedOntology) : null)
                        .build()
                ).collect(Collectors.toList());
        return subscriptionOptions;
    }
    public List<Map<String, Object>> processData(List<Trait> traits) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        for (Trait trait : traits) {
            HashMap<String, Object> row = new HashMap<>();
            row.put(TraitFileColumns.NAME.toString(), trait.getObservationVariableName());
            row.put(TraitFileColumns.FULL_NAME.toString(), trait.getFullName());
            row.put(TraitFileColumns.TERM_TYPE.toString(), TermTypeTranslator.getDisplayNameFromTermType( trait.getTermType() ));
            row.put(TraitFileColumns.DESCRIPTION.toString(), trait.getTraitDescription());
            //SYNONYMS
            String synonymsAsStr = null;
            if(trait.getSynonyms() != null) {
                synonymsAsStr = String.join("; ", trait.getSynonyms());
            }
            row.put(TraitFileColumns.SYNONYMS.toString(), synonymsAsStr);
            //STATUS
            if(trait.getActive()) {
                row.put(TraitFileColumns.STATUS.toString(), "active");
            } else {
                row.put(TraitFileColumns.STATUS.toString(), "archived");

            }
            //TAGS
            String tagsAsStr = null;
            if(trait.getTags() != null) {
                tagsAsStr = String.join("; ", trait.getTags());
            }
            row.put(TraitFileColumns.TAGS.toString(), tagsAsStr);

            row.put(TraitFileColumns.TRAIT_ENTITY.toString(), trait.getEntity());
            row.put(TraitFileColumns.TRAIT_ATTRIBUTE.toString(), trait.getAttribute());
            Method method = trait.getMethod();
            if(method!=null) {
                row.put(TraitFileColumns.METHOD_DESCRIPTION.toString(), method.getDescription());
                row.put(TraitFileColumns.METHOD_CLASS.toString(), method.getMethodClass());
                row.put(TraitFileColumns.METHOD_FORMULA.toString(), method.getFormula());
            }
            Scale scale = trait.getScale();
            if(scale!=null) {

                row.put(TraitFileColumns.SCALE_CLASS.toString(), DataTypeTranslator.getDisplayNameFromType(scale.getDataType()));
                row.put(TraitFileColumns.UNITS.toString(), scale.getUnits());
                row.put(TraitFileColumns.SCALE_DECIMAL_PLACES.toString(), scale.getDecimalPlaces());
                row.put(TraitFileColumns.SCALE_LOWER_LIMIT.toString(), scale.getValidValueMin());
                row.put(TraitFileColumns.SCALE_UPPER_LIMIT.toString(), scale.getValidValueMax());
                //SCALE_CATEGORIES
                String categoriesAsStr = makeCategoriesString(scale);

                row.put(TraitFileColumns.SCALE_CATEGORIES.toString(), categoriesAsStr);
            }
            processedData.add(row);
        }
        return processedData;
    }

    private String makeCategoriesString(Scale scale) {
        String categoriesAsStr = null;
        if(scale.getCategories() != null &&
                (scale.getDataType()==DataType.ORDINAL || scale.getDataType() == DataType.NOMINAL) ) {
            categoriesAsStr = scale.getCategories().stream()
                    .map(cat -> makeCategoryString(cat, scale.getDataType()) )
                    .collect(Collectors.joining("; "));
        }
        return categoriesAsStr;
    }

    private String makeCategoryString(BrAPIScaleValidValuesCategories category, DataType scaleClass){
        StringBuilder stringBuilder = new StringBuilder( category.getValue() );
        if( StringUtils.isNotBlank( category.getLabel() ) && scaleClass == DataType.ORDINAL ){
            stringBuilder.append("=");
            stringBuilder.append( category.getLabel() );
        }
        return stringBuilder.toString();
    }
}
