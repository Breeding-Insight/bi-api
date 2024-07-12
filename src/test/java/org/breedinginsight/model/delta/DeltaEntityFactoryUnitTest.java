package org.breedinginsight.model.delta;

import com.github.filosganga.geogson.model.Point;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrApiGeoJSON;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.germ.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DeltaEntityFactoryUnitTest {

    @Inject
    private DeltaEntityFactory entityFactory;

    @Test
    @SneakyThrows
    void deltaGermplasmTest() {

        // Create BrAPIGermplasm

        String additionalInfoString = "{\"additionalInfo\":{\"createdBy\":{\"userId\":\"101e7314-ba2c-466b-a1e0-f02409ab0d3d\",\"userName\":\"BI-DEV Admin\"},\"createdDate\":\"14/06/2024 19:17:40\",\"femaleParentUUID\":\"2927a4a5-c204-4255-850b-a1eb2c291263\",\"listEntryNumbers\":{\"fa0f1715-84b8-4ca7-8abc-ff191f221048\":\"38356\"},\"importEntryNumber\":\"38356\",\"maleParentUnknown\":false}}";
        JsonObject additionalInfo = JsonParser.parseString(additionalInfoString).getAsJsonObject();

        List<BrAPIExternalReference> xrefs = new ArrayList<>();
        xrefs.add(new BrAPIExternalReference().referenceSource("breedinginsight.org").referenceId("e2d530b5-184a-4ef3-8b3a-1acd80a07a00"));
        xrefs.add(new BrAPIExternalReference().referenceSource("breedinginsight.org/programs").referenceId("e1d21849-6107-4be9-984f-195b029c14e0"));

        List<BrAPIGermplasmDonors> donors = new ArrayList<>();
        donors.add(new BrAPIGermplasmDonors().donorAccessionNumber("abc").donorInstituteCode("institution"));
        donors.add(new BrAPIGermplasmDonors().donorAccessionNumber("xyz").donorInstituteCode("institution"));

        List<BrAPIGermplasmSynonyms> synonyms = new ArrayList<>();
        synonyms.add(new BrAPIGermplasmSynonyms().synonym("germA").type("synonym"));
        synonyms.add(new BrAPIGermplasmSynonyms().synonym("germB").type("synonym"));

        List<BrAPITaxonID> taxonIds = new ArrayList<>();
        taxonIds.add(new BrAPITaxonID().taxonId("testTaxon1").sourceName("test taxon source"));
        taxonIds.add(new BrAPITaxonID().taxonId("testTaxon2").sourceName("test taxon source"));

        var coordinates = BrApiGeoJSON.builder().geometry(Point.from(34.24, 43.23)).type("Feature").build();
        var origin = new BrAPIGermplasmOrigin().coordinates(coordinates).coordinateUncertainty("+/- 10");
        var storageType = new BrAPIGermplasmStorageTypes(BrAPIGermplasmStorageTypesEnum._10);
        storageType.setDescription("storage type description");

        BrAPIGermplasm brAPIGermplasm = new BrAPIGermplasm()
                .germplasmDbId("0d14b3ee-980c-41e3-ad7e-f9fb597e2eb6")
                .accessionNumber("1")
                .acquisitionDate(LocalDate.of(2020, Month.APRIL, 1))
                .additionalInfo(additionalInfo)
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
                .externalReferences(xrefs)
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

        // Use the factory to create a DeltaGermplasm from the BrAPIGermplasm
        DeltaGermplasm deltaGermplasm = entityFactory.makeDeltaGermplasmBean(brAPIGermplasm);

        // Check that clone makes a correct copy
        BrAPIGermplasm clonedBrAPIGermplasm = deltaGermplasm.cloneBrAPIObject();

        assertNotNull(clonedBrAPIGermplasm);
        assertEquals(clonedBrAPIGermplasm, brAPIGermplasm);
    }

    @Test
    @SneakyThrows
    void deltaLocationTest() {

        // Create BrAPILocation

        List<BrAPIExternalReference> xrefs = new ArrayList<>();
        xrefs.add(new BrAPIExternalReference().referenceSource("breedinginsight.org").referenceId("e2d530b5-184a-4ef3-8b3a-1acd80a07a00"));
        xrefs.add(new BrAPIExternalReference().referenceSource("breedinginsight.org/programs").referenceId("e1d21849-6107-4be9-984f-195b029c14e0"));

        BrAPILocation brAPILocation = new BrAPILocation()
                .locationDbId("522bcd9c-2b75-4c88-89d3-5059d5ac713b")
                .abbreviation("f1")
                .additionalInfo(JsonParser.parseString("{\"key\":\"value\"}").getAsJsonObject())
                .coordinateDescription("description")
                .coordinateUncertainty("12")
                .coordinates(BrApiGeoJSON.builder().geometry(Point.from(34.24, 43.23)).type("Feature").build())
                .countryCode("US")
                .countryName("United States")
                .documentationURL("http://localhost")
                .environmentType("env type")
                .exposure("S")
                .externalReferences(xrefs)
                .instituteAddress("119 CALS Surge Facility 525 Tower Road Ithaca, NY 14853-2703")
                .instituteName("Breeding Insight")
                .locationName("Field 1")
                .locationType("field")
                .parentLocationDbId("31fd8d8c-0a6f-486a-825c-4aff4249770e")
                .parentLocationName("parent field")
                .siteStatus("active")
                .slope("3.1")
                .topography("topo");

        // Use the factory to create a DeltaLocation from the BrAPILocation
        DeltaLocation deltaLocation = entityFactory.makeDeltaLocationBean(brAPILocation);

        // Check that clone makes a correct copy
        BrAPILocation clonedBrAPILocation = deltaLocation.cloneBrAPIObject();

        assertNotNull(clonedBrAPILocation);
        assertEquals(clonedBrAPILocation, brAPILocation);
    }
}
