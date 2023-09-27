package org.breedinginsight.services.impl;

import lombok.SneakyThrows;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BreedingMethodServiceImplTest {
    BreedingMethodServiceImpl breedingMethodService;
    BreedingMethodDAO breedingMethodDAO = null;
    BrAPIGermplasmService germplasmService = null;
    DSLContext dsl = null;

    @BeforeAll
    public void setup() {
        breedingMethodService = new BreedingMethodServiceImpl(breedingMethodDAO, germplasmService, dsl);
    }

    @Test
    public void hasRequiredFields(){
        ProgramBreedingMethodEntity method= new ProgramBreedingMethodEntity();
        assertTrue(breedingMethodService.isMissingRequiredFields(method),"the method has blanks");

        method.setName("Dave");
        method.setAbbreviation("DRP");
        method.setCategory("human");
        method.setGeneticDiversity("none");
        assertFalse(breedingMethodService.isMissingRequiredFields(method),"the method has all required fields");
    }

    @Test
    @SneakyThrows
    public void isDuplicateMethodNameFoundOnList() {
        List<ProgramBreedingMethodEntity> programBreedingMethodEntityList = new ArrayList<>();
        programBreedingMethodEntityList.add(makeMethod("1", null));
        programBreedingMethodEntityList.add(makeMethod("2", null));

        boolean isDup = breedingMethodService.isDuplicateMethodNameFoundOnList(makeMethod("1", null), programBreedingMethodEntityList);
        assertTrue(isDup, "Duplicate name found in  list.");
    }

    @Test
    @SneakyThrows
    public void isDuplicateMethodAbbreviationFoundOnList() {
        List<ProgramBreedingMethodEntity> programBreedingMethodEntityList = new ArrayList<>();
        programBreedingMethodEntityList.add(makeMethod(null, "1"));
        programBreedingMethodEntityList.add(makeMethod(null, "2"));

        boolean isDup = breedingMethodService.isDuplicateMethodAbbreviationFoundOnList(makeMethod(null, "1"), programBreedingMethodEntityList);
        assertTrue(isDup, "Duplicate abbreviation found in  list.");
        isDup = breedingMethodService.isDuplicateMethodAbbreviationFoundOnList(makeMethod(null, "unique"), programBreedingMethodEntityList);
        assertFalse(isDup, "Duplicate abbreviation NOT found in  list.");
    }



    @Test
    @SneakyThrows
    public void isDuplicateName() {
        ProgramBreedingMethodEntity method = makeMethod("ABC", null);
        ProgramBreedingMethodEntity testMethod = makeMethod("ABC", null);
        ProgramBreedingMethodEntity testMethodLowerCase = makeMethod("abc", null);



        assertTrue(
                breedingMethodService.isDuplicateName(testMethod, method),
                "Method Names are Equal"
        );
        assertTrue(
                breedingMethodService.isDuplicateName(method, testMethod),
                "Method Names are Equal (switched order)"
        );
        assertTrue(
                breedingMethodService.isDuplicateName(method, testMethodLowerCase),
                "Method Names are Equal (one is LowerCase)"
        );
        testMethod.setId(method.getId());
        assertFalse(
                breedingMethodService.isDuplicateName(testMethod, method),
                "The methods are the same method (not duplicate data)"
        );


        testMethod.setName("misatch Name");
        testMethod.setId(UUID.randomUUID());
        assertFalse(
                breedingMethodService.isDuplicateName(testMethod, method),
                "Method Names are not Equal"
        );
        testMethod.setName(null);
        assertFalse(
                breedingMethodService.isDuplicateName(testMethod, method),
                "Method Names are not Equal (one is null)"
        );

        testMethod.setName("name");
        method.setName(null);
        assertFalse(
                breedingMethodService.isDuplicateName(testMethod, method),
                "Method Names are not Equal (the other is null)"
        );
    }

    @Test
    @SneakyThrows
    public void isDuplicateAbbreviation() {
        ProgramBreedingMethodEntity method = makeMethod("ABC", "ABC");
        ProgramBreedingMethodEntity testMethod = makeMethod("mismatch_name", "ABC");
        ProgramBreedingMethodEntity testMethodLowerCase = makeMethod("mismatch_name", "abc");
        assertTrue(
                breedingMethodService.isDuplicateAbbreviation(testMethod, method),
                "Method Abbreviations are Equal"
        );
        assertTrue(
                breedingMethodService.isDuplicateAbbreviation(method, testMethod),
                "Method Abbreviations are Equal (switched order)"
        );
        assertTrue(
                breedingMethodService.isDuplicateAbbreviation(testMethodLowerCase, method),
                "Method Abbreviations are Equal (one is lower case)"
        );
        testMethod.setAbbreviation("misatch_abbreviation");
        assertFalse(
                breedingMethodService.isDuplicateAbbreviation(method, testMethod),
                "Method Abbreviations are not equal."
        );
        testMethod.setAbbreviation(null);
        assertFalse(
                breedingMethodService.isDuplicateAbbreviation(method, testMethod),
                "Method Abbreviations are not equal (one is null)."
        );
    }

    // Helper Methods //
    private ProgramBreedingMethodEntity makeMethod(String nameSuffix, String abbrevSuffix){
        ProgramBreedingMethodEntity method = new ProgramBreedingMethodEntity();

        method.setName("Name" + nameSuffix!=null? nameSuffix : "junk");
        method.setAbbreviation("Abbreviation" + abbrevSuffix!=null? abbrevSuffix : "junk");
        method.setId(UUID.randomUUID());
        return method;
    }

}
