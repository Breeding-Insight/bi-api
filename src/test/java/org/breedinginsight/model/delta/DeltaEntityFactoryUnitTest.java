package org.breedinginsight.model.delta;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.*;
import org.brapi.v2.model.core.*;
import org.brapi.v2.model.germ.*;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.utilities.DatasetUtil.gson;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DeltaEntityFactoryUnitTest extends DatabaseTest {

    @Inject
    private DeltaEntityFactory entityFactory;

    @Test
    @SneakyThrows
    void deltaGermplasmTest() {
        // Create BrAPIGermplasm
        BrAPIGermplasm brAPIGermplasm = createBrAPIGermplasm();

        // Use the factory to create a DeltaGermplasm from the BrAPIGermplasm
        DeltaGermplasm deltaGermplasm = entityFactory.makeDeltaGermplasmBean(brAPIGermplasm);
        assertNotNull(deltaGermplasm);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(DeltaGermplasm.class);
        // Check that clone makes a correct copy
        BrAPIGermplasm clonedBrAPIGermplasm = deltaGermplasm.cloneEntity();
        assertNotNull(clonedBrAPIGermplasm);
        assertEquals(clonedBrAPIGermplasm, brAPIGermplasm);
    }

    @Test
    @SneakyThrows
    void deltaLocationTest() {
        // Create BrAPILocation
       ProgramLocation programLocation = createProgramLocation();

        // Use the factory to create a DeltaLocation from the BrAPILocation
        DeltaLocation deltaLocation = entityFactory.makeDeltaLocationBean(programLocation);
        assertNotNull(deltaLocation);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(DeltaLocation.class);
        // Check that clone makes a correct copy
        ProgramLocation clonedProgramLocation = deltaLocation.cloneEntity();
        assertNotNull(clonedProgramLocation);
        assertEquals(clonedProgramLocation, programLocation);
    }

    @Test
    @SneakyThrows
    void deltaObservationTest() {
        // Create BrAPIObservation
        BrAPIObservation brAPIObservation = createBrAPIObservation();

        // Use the factory to create a DeltaObservation from the BrAPIObservation
        DeltaObservation deltaObservation = entityFactory.makeDeltaObservationBean(brAPIObservation);
        assertNotNull(deltaObservation);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(DeltaObservation.class);
        // Check that clone makes a correct copy
        BrAPIObservation clonedBrAPIObservation = deltaObservation.cloneEntity();
        assertNotNull(clonedBrAPIObservation);
        assertEquals(clonedBrAPIObservation, brAPIObservation);
    }

    @Test
    @SneakyThrows
    void deltaObservationUnitTest() {
        // Create BrAPIObservationUnit
        BrAPIObservationUnit brAPIObservationUnit = createBrAPIObservationUnit();

        // Use the factory to create a DeltaObservationUnit from the BrAPIObservationUnit
        DeltaObservationUnit deltaObservationUnit = entityFactory.makeDeltaObservationUnitBean(brAPIObservationUnit);
        assertNotNull(deltaObservationUnit);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(DeltaObservationUnit.class);
        
        // Check that clone makes a correct copy
        BrAPIObservationUnit clonedBrAPIObservationUnit = deltaObservationUnit.cloneEntity();
        assertNotNull(clonedBrAPIObservationUnit);
        assertEquals(clonedBrAPIObservationUnit, brAPIObservationUnit);
    }

    @Test
    @SneakyThrows
    void deltaObservationVariableTest() {
        // Create BrAPIObservationVariable
        BrAPIObservationVariable brAPIObservationVariable = createBrAPIObservationVariable();

        // Use the factory to create a DeltaObservationVariable from the BrAPIObservationVariable
        DeltaObservationVariable deltaObservationVariable = entityFactory.makeDeltaObservationVariableBean(brAPIObservationVariable);
        assertNotNull(deltaObservationVariable);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(DeltaObservationVariable.class);
        // Check that clone makes a correct copy
        BrAPIObservationVariable clonedBrAPIObservationVariable = deltaObservationVariable.cloneEntity();
        assertNotNull(clonedBrAPIObservationVariable);
        assertEquals(clonedBrAPIObservationVariable, brAPIObservationVariable);
    }

    @Test
    @SneakyThrows
    void deltaEnvironmentTest() {
        // Create BrAPIStudy
        BrAPIStudy brAPIStudy = createBrAPIStudy();

        // Use the factory to create a DeltaEnvironment from the BrAPIStudy
        Environment environment = entityFactory.makeEnvironmentBean(brAPIStudy);
        assertNotNull(environment);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(Environment.class);
        // Check that clone makes a correct copy
        BrAPIStudy clonedBrAPIStudy = environment.cloneEntity();
        assertNotNull(clonedBrAPIStudy);
        assertEquals(clonedBrAPIStudy, brAPIStudy);
    }

    @Test
    @SneakyThrows
    void deltaExperimentTest() {
        // Create BrAPITrial
        BrAPITrial brAPITrial = createBrAPITrial();

        // Use the factory to create a DeltaExperiment from the BrAPITrial
        Experiment experiment = entityFactory.makeExperimentBean(brAPITrial);
        assertNotNull(experiment);

        // Assert that the DeltaEntity constructor cannot be called to directly instantiate
        testConstructorNotAccessible(Experiment.class);
        // Check that clone makes a correct copy
        BrAPITrial clonedBrAPITrial = experiment.cloneEntity();
        assertNotNull(clonedBrAPITrial);
        assertEquals(clonedBrAPITrial, brAPITrial);
    }

    private void testConstructorNotAccessible(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            constructor.setAccessible(true);
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];

            try {
                // Attempt to create default values for each parameter
                for (int i = 0; i < parameterTypes.length; i++) {
                    args[i] = getDefaultValue(parameterTypes[i]);
                }

                // Attempt to create an instance
                Object instance = constructor.newInstance(args);
                fail("Expected constructor to be inaccessible, but was able to create an instance: " + instance);
            } catch (IllegalAccessException e) {
                // This is the expected exception for inaccessible constructor
                return;
            } catch (InstantiationException e) {
                // This can happen if the class is abstract or an interface
                log.error("Cannot instantiate " + clazz.getSimpleName() + ": " + e.getMessage());
                return;
            } catch (InvocationTargetException e) {
                // This can happen if the constructor throws an exception
                log.error("Constructor threw an exception: " + e.getCause().getMessage());
                return;
            } catch (IllegalArgumentException e) {
                // This is likely due to argument type mismatch
                log.error("Argument type mismatch for " + clazz.getSimpleName() + ": " + e.getMessage());
                log.error("Expected types: " + Arrays.toString(parameterTypes));
                log.error("Provided args: " + Arrays.toString(args));
                return;
            }
        }
        fail("No constructors found or all constructors are accessible for " + clazz.getSimpleName());
    }

    private Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == char.class) return '\u0000';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
        }
        return null; // For reference types
    }
    private BrAPIGermplasm createBrAPIGermplasm() {
        String additionalInfoString = "{\"additionalInfo\":{\"createdBy\":{\"userId\":\"101e7314-ba2c-466b-a1e0-f02409ab0d3d\",\"userName\":\"BI-DEV Admin\"},\"createdDate\":\"14/06/2024 19:17:40\",\"femaleParentUUID\":\"2927a4a5-c204-4255-850b-a1eb2c291263\",\"listEntryNumbers\":{\"fa0f1715-84b8-4ca7-8abc-ff191f221048\":\"38356\"},\"importEntryNumber\":\"38356\",\"maleParentUnknown\":false}}";

        List<BrAPIGermplasmDonors> donors = new ArrayList<>();
        donors.add(new BrAPIGermplasmDonors().donorAccessionNumber("abc").donorInstituteCode("institution"));
        donors.add(new BrAPIGermplasmDonors().donorAccessionNumber("xyz").donorInstituteCode("institution"));

        List<BrAPIGermplasmSynonyms> synonyms = new ArrayList<>();
        synonyms.add(new BrAPIGermplasmSynonyms().synonym("germA").type("synonym"));
        synonyms.add(new BrAPIGermplasmSynonyms().synonym("germB").type("synonym"));

        List<BrAPITaxonID> taxonIds = new ArrayList<>();
        taxonIds.add(new BrAPITaxonID().taxonId("testTaxon1").sourceName("test taxon source"));
        taxonIds.add(new BrAPITaxonID().taxonId("testTaxon2").sourceName("test taxon source"));

        var coordinates = BrApiGeoJSON.builder().geometry(null).type("Feature").build();
        var origin = new BrAPIGermplasmOrigin().coordinates(coordinates).coordinateUncertainty("+/- 10");
        var storageType = new BrAPIGermplasmStorageTypes(BrAPIGermplasmStorageTypesEnum._10);
        storageType.setDescription("storage type description");

        return new BrAPIGermplasm()
                .germplasmDbId("0d14b3ee-980c-41e3-ad7e-f9fb597e2eb6")
                .accessionNumber("1")
                .acquisitionDate(LocalDate.of(2020, Month.APRIL, 1))
                .additionalInfo(toJsonObject(additionalInfoString))
                .biologicalStatusOfAccessionCode(BrAPIBiologicalStatusOfAccessionCode._100)
                .biologicalStatusOfAccessionDescription("biological status")
                .breedingMethodDbId("50ce8bcd-5c24-4e89-b7ad-5af127a8d99b")
                .breedingMethodName("biparental")
                .collection("collection")
                .commonCropName("Snail")
                .countryOfOriginCode("USA")
                .defaultDisplayName("germ 1")
                .documentationURL("http://localhost")
                .donors(donors)
                .externalReferences(createExternalReferences())
                .genus("Helix")
                .germplasmName("germ1")
                .germplasmOrigin(List.of(origin))
                .germplasmPUI("germ1PUI")
                .germplasmPreprocessing("germ1Preprocessing")
                .instituteCode("Corn")
                .instituteCode("Cornell")
                .pedigree("mommy snail/daddy snail")
                .seedSource("snail farm")
                .seedSourceDescription("it's a snail farm")
                .species("snail")
                .speciesAuthority("authority")
                .storageTypes(List.of(storageType))
                .subtaxa("subtaxa")
                .subtaxaAuthority("subtaxa authority")
                .synonyms(synonyms)
                .taxonIds(taxonIds);
    }


    private ProgramLocation createProgramLocation() {
        return ProgramLocation.builder()
                .locationDbId("522bcd9c-2b75-4c88-89d3-5059d5ac713b")
                .country(Country.builder().id(UUID.fromString("970303d1-90be-41e8-a605-7b65d29076cb")).name("USA").build())
                .accessibility(Accessibility.builder().id(UUID.fromString("1921ab25-1596-4058-8a7a-77b4c932b67f")).name("private").build())
                .environmentType(EnvironmentType.builder().id(UUID.fromString("e8dff19f-d3bc-45af-b218-a68199e39feb")).name("forest").build())
                .topography(Topography.builder().id(UUID.fromString("3841a576-0765-455d-954a-07ab350983ff")).name("hill").build())
                .build();
    }

    private BrAPILocation createBrAPILocation() {
        return new BrAPILocation()
                .locationDbId("522bcd9c-2b75-4c88-89d3-5059d5ac713b")
                .abbreviation("f1")
                .additionalInfo(toJsonObject("{\"key\":\"value\"}"))
                .coordinateDescription("description")
                .coordinateUncertainty("12")
                .coordinates(BrApiGeoJSON.builder().geometry(null).type("Feature").build())
                .countryCode("US")
                .countryName("United States")
                .documentationURL("http://localhost")
                .environmentType("env type")
                .exposure("S")
                .externalReferences(createExternalReferences())
                .instituteAddress("119 CALS Surge Facility 525 Tower Road Ithaca, NY 14853-2703")
                .instituteName("Breeding Insight")
                .locationName("Field 1")
                .locationType("field")
                .parentLocationDbId("31fd8d8c-0a6f-486a-825c-4aff4249770e")
                .parentLocationName("parent field")
                .siteStatus("active")
                .slope("3.1")
                .topography("topo");
    }

    private BrAPIObservationUnit createBrAPIObservationUnit() {
        String positionJson = "{\"entryType\":\"TEST\",\"geoCoordinates\":null,\"observationLevel\":{\"levelName\":\"plot\",\"levelOrder\":0,\"levelCode\":\"1186 [SKTEST-2]\"},\"observationLevelRelationships\":[{\"levelName\":\"rep\",\"levelOrder\":null,\"levelCode\":\"3\",\"observationUnitDbId\":\"a677de20-a1cd-4982-ac71-1bc17ef08424\"},{\"levelName\":\"block\",\"levelOrder\":null,\"levelCode\":\"1\",\"observationUnitDbId\":\"a677de20-a1cd-4982-ac71-1bc17ef08424\"}],\"positionCoordinateX\":null,\"positionCoordinateXType\":null,\"positionCoordinateY\":null,\"positionCoordinateYType\":null}";
        BrAPIObservationUnitPosition position = gson.fromJson(positionJson, BrAPIObservationUnitPosition.class);

        List<BrAPIObservationTreatment> treatments = new ArrayList<>();
        treatments.add(new BrAPIObservationTreatment().factor("factor1").modality("modality1"));
        treatments.add(new BrAPIObservationTreatment().factor("factor2").modality("modality2"));

        return new BrAPIObservationUnit()
                .observationUnitDbId("0d12951f-cc68-436b-8493-060611383ef2")
                .additionalInfo(toJsonObject("{\"gid\":\"94\", \"observationLevel\":\"Plot\"}"))
                .externalReferences(createExternalReferences())
                .germplasmDbId("8cbbbc0f-f3e2-4f24-82e6-5315d7bd9c8c")
                .germplasmName("lucky")
                .locationDbId("862d3950-0184-4f2e-a729-fc2688877482")
                .locationName("location")
                .observationUnitName("plot 1")
                .observationUnitPUI("abcdefg")
                .observationUnitPosition(position)
                .programDbId("65182b12-0771-4deb-b5c5-2f8d87efebbd")
                .programName("Bigger Snails")
                .seedLotDbId("38569c7b-140b-4ae3-8a1e-7a503d18d854")
                .seedLotName("seedlot")
                .studyDbId("212a90c5-bef9-40b7-9a5b-5b90749388a7")
                .studyName("snail size")
                .treatments(treatments)
                .trialDbId("ecad3e67-3e2c-4b73-b84a-663cdda6cb2f")
                .trialName("snail trial")
                .observations(List.of(createBrAPIObservation()))
                .crossName("mix")
                .crossDbId("f1a3b726-d9e3-4e12-b59c-9d03cc3c671a");
    }

    private BrAPIObservationVariable createBrAPIObservationVariable() {
        return new BrAPIObservationVariable()
                .observationVariableDbId("9f362177-a30f-42e1-993f-ba905767f481")
                .observationVariableName("Height")
                .observationVariablePUI("xyz")
                .additionalInfo(toJsonObject("{\"fullname\":\"Snail Height\"}"))
                .commonCropName("Snail")
                .contextOfUse(List.of("first", "second"))
                .defaultValue("default")
                .documentationURL("http://localhost")
                .externalReferences(createExternalReferences())
                .growthStage("stage")
                .institution("Cornell")
                .language("en-us")
                .method(createBrAPIMethod())
                .ontologyReference(createBrAPIOntologyReference())
                .scale(createBrAPIScale())
                .scientist("Scientist")
                .status("active")
                .submissionTimestamp(OffsetDateTime.now())
                .synonyms(List.of("n1", "n2", "n3"))
                .trait(createBrAPITrait());
    }

    private BrAPIStudy createBrAPIStudy() {
        return new BrAPIStudy()
                .studyDbId("9d73d864-d0b4-45bd-a994-e4d2daf437fc")
                .active(true)
                .additionalInfo(toJsonObject("{\"environmentNumber\": \"2\"}"))
                .commonCropName("Snail")
                .contacts(List.of(createBrAPIContact()))
                .culturalPractices("practices")
                .dataLinks(List.of(createBrAPIDataLink()))
                .documentationURL("http://localhost")
                .endDate(OffsetDateTime.now())
                .environmentParameters(List.of(creatBrAPIEnvironmentParameter()))
                .experimentalDesign(createBrAPIStudyExperimentalDesign())
                .externalReferences(createExternalReferences())
                .growthFacility(createBrAPIStudyGrowthFacility())
                .lastUpdate(createBrAPIStudyLastUpdate())
                .license("license")
                .locationDbId("4abea286-a93a-44ca-812f-5420106a71c3")
                .locationName("location")
                .observationLevels(List.of(createBrAPIObservationUnitHierarchyLevel()))
                .observationUnitsDescription("units description")
                .observationVariableDbIds(List.of("67f6449b-b79f-4126-91bf-1b3903571a3f", "0684c99e-a01a-4f59-8c14-bbe3e550c8c5"))
                .seasons(List.of("2021Fall", "2022Fall"))
                .startDate(OffsetDateTime.now())
                .studyCode("study code")
                .studyDescription("description")
                .studyName("Study name")
                .studyPUI("d5295e32-da72-433f-b3bd-a40cf5b93bbb")
                .studyType("phenotyping trial")
                .trialDbId("703cb8d8-9167-457c-8934-6d065415d312")
                .trialName("Snail Trial");
    }

    private BrAPITrial createBrAPITrial() {
        String additionalInfoString = "{\"datasets\":[{\"id\":\"0d9f03bf-4b0c-40e8-95b9-9ca0a107f30f\",\"name\":\"Plot\",\"level\":\"0\"}],\"createdBy\":{\"userId\":\"e3f92938-fae4-4d57-93c6-11970a8128a6\",\"userName\":\"BI-DEV Admin\"},\"createdDate\":\"2024-07-12\",\"experimentType\":\"Disease resistance screening\",\"experimentNumber\":\"1\",\"defaultObservationLevel\":\"Plot\"}";

        return new BrAPITrial()
                .trialDbId("429ff1cd-1a4a-4be7-bd6c-d10047d03230")
                .active(true)
                .additionalInfo(toJsonObject(additionalInfoString))
                .commonCropName("Snail")
                .contacts(List.of(createBrAPIContact()))
                .datasetAuthorships(List.of(createBrAPITrialDatasetAuthorship()))
                .documentationURL("http://localhost")
                .endDate(LocalDate.now())
                .externalReferences(createExternalReferences())
                .programDbId("401ab961-4c37-47f0-910b-0ea39648f547")
                .programName("Large Program")
                .publications(List.of(createBrAPITrialPublication()))
                .startDate(LocalDate.now())
                .trialDescription("the description")
                .trialName("Snail Trial")
                .trialPUI("9bfa3dba-5195-4a08-a891-cd2b105116fa");

    }

    private BrAPIObservation createBrAPIObservation() {
        String additionalInfoString = "{\"createdBy\":{\"userId\":\"e3f92938-fae4-4d57-93c6-11970a8128a6\",\"userName\":\"BI-DEV Admin\"},\"studyName\":\"Salinas, CA 2022\",\"createdDate\":\"2024-07-12T09:44:23.34292-04:00\"}";

        return new BrAPIObservation()
            .observationDbId("192161b2-3f89-499b-8f04-7298128e9f1a")
            .additionalInfo(toJsonObject(additionalInfoString))
            .collector("intern")
            .externalReferences(createExternalReferences())
//            .geoCoordinates(BrApiGeoJSON.builder().geometry(null).type("Feature").build())
            .germplasmDbId("2bb19ef2-fcc5-406d-b9c3-4517c665b699")
            .germplasmName("lucky")
            .observationTimeStamp(OffsetDateTime.now())
            .observationUnitDbId("f067b8d4-a9ae-4fed-91a0-beb240e0c17d")
            .observationUnitName("plot1")
            .observationVariableDbId("a5d03d72-8bab-4ff1-b991-57c8b563f8a4")
            .observationVariableName("height")
            .season(new BrAPISeason().seasonDbId("704b36bc-7477-41f2-b59f-2a878e279fb8").seasonName("fall 2020").year(2020))
            .studyDbId("301caddb-a853-4f8b-9c80-7483d0444767")
            .uploadedBy("uploader")
            .value("45");
    }

    private BrAPITrait createBrAPITrait() {
        String jsonString = "{\"additionalInfo\":null,\"externalReferences\":[{\"referenceID\":\"89c76477-51fc-401d-9ef0-aa1a8676db67\",\"referenceId\":\"89c76477-51fc-401d-9ef0-aa1a8676db67\",\"referenceSource\":\"breedinginsight.org\"}],\"alternativeAbbreviations\":[],\"attribute\":\"INSV severity mean\",\"attributePUI\":null,\"entity\":\"Plot\",\"entityPUI\":null,\"mainAbbreviation\":null,\"ontologyReference\":null,\"status\":\"active\",\"synonyms\":[\"INSVSEVW7AVE\",\"Week 7 INSV severity mean\"],\"traitClass\":null,\"traitDescription\":\"Mean INSV severity of 10 plants per plot at Week 7\",\"traitName\":\"Plot INSV severity mean [SKTEST]\",\"traitPUI\":null,\"traitDbId\":\"5d6132dd-5298-4563-a9e1-d80136347338\"}";
        return gson.fromJson(jsonString, BrAPITrait.class);
    }

    private BrAPIMethod createBrAPIMethod() {
        String jsonString = "{\"additionalInfo\":null,\"externalReferences\":[{\"referenceID\":\"2edb0f01-2968-4ec2-93d6-f8ed2b31f262\",\"referenceId\":\"2edb0f01-2968-4ec2-93d6-f8ed2b31f262\",\"referenceSource\":\"breedinginsight.org\"}],\"bibliographicalReference\":null,\"description\":null,\"formula\":\"Mean INSVSEVW7\",\"methodClass\":\"Computation\",\"methodName\":\"Computation [SKTEST]\",\"methodPUI\":null,\"ontologyReference\":null,\"methodDbId\":\"260ff729-74c5-4893-9b0c-097a756da882\"}";
        return gson.fromJson(jsonString, BrAPIMethod.class);
    }

    private BrAPIScale createBrAPIScale() {
        String jsonString = "{\"additionalInfo\":null,\"externalReferences\":[{\"referenceID\":\"a23c9b8a-6206-42e6-95ba-6c4616d3fd5b\",\"referenceId\":\"a23c9b8a-6206-42e6-95ba-6c4616d3fd5b\",\"referenceSource\":\"breedinginsight.org\"}],\"dataType\":\"Numerical\",\"decimalPlaces\":null,\"units\":\"index\",\"ontologyReference\":null,\"scaleName\":\"index [SKTEST]\",\"scalePUI\":null,\"validValues\":{\"categories\":[{\"label\":\"No visible symptoms\",\"value\":\"0\"},{\"label\":\"Slight yellowing\",\"value\":\"1\"},{\"label\":\"Necrotic spots showing\",\"value\":\"2\"},{\"label\":\"Necrotic spots on majority of leaves\",\"value\":\"3\"},{\"label\":\"Plant nearly dead from INSV, few green leaves remaining\",\"value\":\"4\"},{\"label\":\"Plant dead from INSV\",\"value\":\"5\"}],\"max\":5,\"min\":0,\"maximumValue\":\"5\",\"minimumValue\":\"0\"},\"scaleDbId\":\"cc3bcf16-f22a-4ee8-a2ff-2e2f210f5233\"}";
        return gson.fromJson(jsonString, BrAPIScale.class);
    }

    private BrAPIOntologyReference createBrAPIOntologyReference() {
        return new BrAPIOntologyReference()
            .ontologyDbId("776000c4-45d0-46fc-b527-5d6abe67eb99")
            .ontologyName("nameabc")
            .version("v4")
            .addDocumentationLinksItem(
                new BrAPIOntologyReferenceDocumentationLinks()
                    .type(BrAPIOntologyReferenceTypeEnum.WEBPAGE)
                    .URL("http://breedinginsight.org")
            );
    }

    private BrAPIDataLink createBrAPIDataLink() {
        return new BrAPIDataLink()
            .dataFormat("table")
            .description("desc")
            .fileFormat("CSV")
            .name("DataName")
            .provenance("provenance")
            .scientificType("scitype")
            .url("http://localhost:8080")
            .version("v7.5.2");
    }

    private BrAPIContact createBrAPIContact() {
        return new BrAPIContact()
                .contactDbId("4bc0b024-3734-41a8-a90b-78873fcaa512")
                .type("contact type")
                .instituteName("Example")
                .name("contact")
                .email("contact@example.com")
                .orcid("0000-1234-5678-9101");
    }

    private BrAPITrialDatasetAuthorships createBrAPITrialDatasetAuthorship() {
        return new BrAPITrialDatasetAuthorships()
                .datasetPUI("5a2661e9-bd26-48b6-8fa4-79e15d242286")
                .license("dataset license")
                .publicReleaseDate(LocalDate.now())
                .submissionDate(LocalDate.now());
    }

    private BrAPITrialPublications createBrAPITrialPublication() {
        return new BrAPITrialPublications()
                .publicationPUI("f8f745e7-efad-4837-b77b-c34e7392918a")
                .publicationReference("breedinginsight.org");
    }

    private BrAPIEnvironmentParameter creatBrAPIEnvironmentParameter() {
        return new BrAPIEnvironmentParameter()
                .parameterName("parameter")
                .parameterPUI("845ea09e-cd01-4b97-b7cb-40c53d2011ef")
                .description("env param")
                .unit("unit")
                .unitPUI("490ebd7e-e99e-47be-96ec-af264cb273ac")
                .value("44")
                .valuePUI("b718b07f-242e-4121-8794-e65edade4a8a");
    }

    private BrAPIStudyExperimentalDesign createBrAPIStudyExperimentalDesign() {
        return new BrAPIStudyExperimentalDesign()
                .description("the experiment design")
                .PUI("3944e242-a927-42a0-ae80-d6a666d380e1");
    }

    private BrAPIStudyGrowthFacility createBrAPIStudyGrowthFacility() {
        return new BrAPIStudyGrowthFacility()
                .description("the growth facility")
                .PUI("9bcbea88-4d68-4f7f-93a3-c75a3fba031b");
    }

    private BrAPIStudyLastUpdate createBrAPIStudyLastUpdate() {
        return new BrAPIStudyLastUpdate()
                .version("4.32.5")
                .timestamp(OffsetDateTime.now());
    }

    private BrAPIObservationUnitHierarchyLevel createBrAPIObservationUnitHierarchyLevel() {
        return new BrAPIObservationUnitHierarchyLevel()
                .levelName("top level")
                .levelOrder(0);
    }

    private List<BrAPIExternalReference> createExternalReferences() {
        List<BrAPIExternalReference> xrefs = new ArrayList<>();
        xrefs.add(new BrAPIExternalReference().referenceSource("breedinginsight.org").referenceId("e2d530b5-184a-4ef3-8b3a-1acd80a07a00"));
        xrefs.add(new BrAPIExternalReference().referenceSource("breedinginsight.org/programs").referenceId("e1d21849-6107-4be9-984f-195b029c14e0"));
        return xrefs;
    }

    private JsonObject toJsonObject(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

}
