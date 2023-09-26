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
    public void isDupOfProgramMethod() {
        List<ProgramBreedingMethodEntity> programBreedingMethodEntityList = new ArrayList<>();
        programBreedingMethodEntityList.add(makeMethod("1"));
        programBreedingMethodEntityList.add(makeMethod("2"));

        boolean isDup = breedingMethodService.isDuplicateMethodFoundOnList(makeMethod("1"), programBreedingMethodEntityList);
        assertTrue(isDup, "Duplicate found in  list.");
    }

    @Test
    @SneakyThrows
    public void isNotADupMethod() {
        List<ProgramBreedingMethodEntity> programBreedingMethodEntityList = new ArrayList<>();
        programBreedingMethodEntityList.add(makeMethod("1"));
        programBreedingMethodEntityList.add(makeMethod("2"));


        ProgramBreedingMethodEntity methodUnique = makeMethod("unique");;
        boolean isDup = breedingMethodService.isDuplicateMethodFoundOnList(methodUnique, programBreedingMethodEntityList);
        assertFalse(isDup, "No duplicates method found.");

    }
    @Test
    @SneakyThrows
    public void methodNameEqual() {
        ProgramBreedingMethodEntity method = makeMethod("ABC");
        ProgramBreedingMethodEntity testMethod = makeMethod("ABC");
        testMethod.setAbbreviation("misatch Abbreviation");
        assertTrue(
                breedingMethodService.areMethodsDuplicate(testMethod, method),
                "Method Names are Equal"
        );
        assertTrue(
                breedingMethodService.areMethodsDuplicate(method, testMethod),
                "Method Names are Equal (switched order)"
        );

        testMethod.setName("misatch Name");
        assertFalse(
                breedingMethodService.areMethodsDuplicate(testMethod, method),
                "Method Names are not Equal"
        );
        testMethod.setName(null);
        assertFalse(
                breedingMethodService.areMethodsDuplicate(testMethod, method),
                "Method Names are not Equal (one is null)"
        );

        testMethod.setName("name");
        method.setName(null);
        assertFalse(
                breedingMethodService.areMethodsDuplicate(testMethod, method),
                "Method Names are not Equal (the other is null)"
        );

    }

    @Test
    @SneakyThrows
    public void methodAbbreviationEqual() {
        ProgramBreedingMethodEntity method = makeMethod("ABC");
        ProgramBreedingMethodEntity testMethod = makeMethod("ABC");
        testMethod.setName("misatch Name");
        assertTrue(
                breedingMethodService.areMethodsDuplicate(testMethod, method),
                "Method Abbreviations are Equal"
        );
        assertTrue(
                breedingMethodService.areMethodsDuplicate(method, testMethod),
                "Method Abbreviations are Equal (switched order)"
        );

        testMethod.setAbbreviation("misatch Abbreviation");
        assertFalse(
                breedingMethodService.areMethodsDuplicate(method, testMethod),
                "Method Abbreviations are not equal."
        );
        testMethod.setAbbreviation(null);
        assertFalse(
                breedingMethodService.areMethodsDuplicate(method, testMethod),
                "Method Abbreviations are not equal (one is null)."
        );
    }

    // Helper Methods //
    private ProgramBreedingMethodEntity makeMethod(String suffix){
        ProgramBreedingMethodEntity method = new ProgramBreedingMethodEntity();
        method.setName("Name" + suffix);
        method.setAbbreviation("Abbreviation" + suffix);
        return method;
    }

}
