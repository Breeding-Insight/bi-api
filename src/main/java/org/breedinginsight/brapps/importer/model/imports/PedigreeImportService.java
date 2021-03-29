/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.brapps.importer.model.imports;

import com.ibm.icu.text.ArabicShaping;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.CrossesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributeValuesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.germ.*;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.breedinginsight.api.deserializer.ArrayOfStringDeserializer;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.services.BrAPIFileImportService;
import org.breedinginsight.brapps.importer.services.BrAPIQueryService;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class PedigreeImportService extends BrAPIImportService {

    private String ID = "PedigreeImport";

    private BrAPIQueryService brAPIQueryService;
    private BrAPIProvider brAPIProvider;

    @Inject
    public PedigreeImportService(BrAPIQueryService brAPIQueryService, BrAPIProvider brAPIProvider)
    {
        this.brAPIQueryService = brAPIQueryService;
        this.brAPIProvider = brAPIProvider;
    }

    @Override
    public PedigreeImport getImportClass() {
        return new PedigreeImport();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void process(List<BrAPIImport> brAPIImports, Table data) throws UnprocessableEntityException {

        // TODO: Need to have a discussion on whether we only create new things, or if we update too. Example: Germplasm attributes
        // TODO: Get the species
        // TODO: How do we handle duplicates? Some can be ignore. What if germplasm have the same name, but different attributes? Throw error for now.
        // TODO: Need to make searches program specific. All uniqueness constraints are assumed within program.
        
        List<PedigreeImport> pedigreeImports = (List<PedigreeImport>)(List<?>) brAPIImports;

        // Get all of our objects specified by their unique attributes
        // Pair are used for objects where we need to reference back to the original import object
        Set<String> germplasmNames = new HashSet<>();
        Set<String> germplasmAttributeNames = new HashSet<>();
        //TODO: See if sets work for pairs
        Set<Pair<String, String>> ouNameByStudy = new HashSet<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);

            // Germplasm names and germplasm attributes
            if (pedigreeImport.getGermplasm() != null) {
                germplasmNames.add(pedigreeImport.getGermplasm().getGermplasmName());
                if (pedigreeImport.getGermplasm().getGermplasmAttributes() != null) {
                    for (GermplasmAttribute attribute: pedigreeImport.getGermplasm().getGermplasmAttributes()) {
                        germplasmAttributeNames.add(attribute.getAttributeName());
                    }
                }
            }

            // Observation units
            if (pedigreeImport.getObservationUnit() != null) {
                ouNameByStudy.add(new MutablePair<>(pedigreeImport.getObservationUnit().getObservationUnitName(), null));
            }
        }

        // Check existing objects
        Map<String, BrAPIGermplasm> existingGermplasmMap = new HashMap<>();
        Map<String, BrAPIGermplasmAttribute> existingGermplasmAttributeMap = new HashMap<>();
        Map<String, BrAPIObservationUnit> existingOUMap = new HashMap<>();
        try {
            List<BrAPIGermplasm> existingGermplasms = brAPIQueryService.getGermplasmByName(new ArrayList<>(germplasmNames));
            existingGermplasms.forEach(existingGermplasm ->
                    existingGermplasmMap.put(existingGermplasm.getGermplasmName(), existingGermplasm));

            List<BrAPIGermplasmAttribute> existingGermplasmAttributes = brAPIQueryService.getGermplasmAttributesByName(new ArrayList<>(germplasmAttributeNames));
            existingGermplasmAttributes.forEach(existingGermplasmAttribute ->
                    existingGermplasmAttributeMap.put(existingGermplasmAttribute.getAttributeName(), existingGermplasmAttribute));

            List<BrAPIObservationUnit> existingOUs = brAPIQueryService.getObservationUnitsByNameAndStudyName(new ArrayList<>(ouNameByStudy));
            existingOUs.forEach(existingOU -> existingOUMap.put(existingOU.getObservationUnitName(), existingOU));
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Create new germplasm
        List<BrAPIGermplasm> newGermplasmList = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);

            if (pedigreeImport.getGermplasm() != null) {

                //TODO: Check for duplicate germplasm within the file
                Germplasm germplasm = pedigreeImport.getGermplasm();
                if (!existingGermplasmMap.containsKey(germplasm.getGermplasmName())) {
                    BrAPIGermplasm newGermplasm = new BrAPIGermplasm();
                    newGermplasm.setGermplasmName(germplasm.getGermplasmName());
                    newGermplasm.setGermplasmPUI(germplasm.getGermplasmPUI());
                    //TODO: Set with program species
                    //brAPIGermplasm.setSpecies();
                    newGermplasm.setAccessionNumber(germplasm.getAccessionNumber());
                    //TODO: Need to check that the acquisition date it in date format
                    //brAPIGermplasm.setAcquisitionDate(pedigreeImport.getGermplasm().getAcquisitionDate());
                    newGermplasm.setCountryOfOriginCode(pedigreeImport.getGermplasm().getCountryOfOrigin());
                    newGermplasmList.add(newGermplasm);
                }
            }
        }

        GermplasmApi germplasmApi = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);
        try {
            germplasmApi.germplasmPost(newGermplasmList);
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }
        Map<String, BrAPIGermplasm> allGermplasm = new HashMap<>(existingGermplasmMap);
        newGermplasmList.forEach(germplasm -> allGermplasm.put(germplasm.getGermplasmName(), germplasm));

        // Create germplasm attributes
        //TODO: Only create if a new germplasm was created
        List<BrAPIGermplasmAttribute> newGermplasmAttributeList = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            if (pedigreeImport.getGermplasm().getGermplasmAttributes() != null) {
                List<BrAPIGermplasmAttributeValue> values = new ArrayList<>();
                for (GermplasmAttribute attribute: pedigreeImport.getGermplasm().getGermplasmAttributes()) {
                    if (!existingGermplasmAttributeMap.containsKey(attribute)) {
                        BrAPIGermplasmAttribute newGermplasmAttribute = new BrAPIGermplasmAttribute();
                        newGermplasmAttribute.setAttributeName(attribute.getAttributeName());
                        newGermplasmAttribute.setAttributeDescription(attribute.getAttributeDescription());
                        newGermplasmAttribute.setAttributeCategory(attribute.getAttributeCategory());
                        newGermplasmAttributeList.add(newGermplasmAttribute);
                    }
                }
            }
        }

        GermplasmAttributesApi germplasmAttributesApi = brAPIProvider.getGermplasmAttributesApi(BrAPIClientType.CORE);
        try {
            germplasmAttributesApi.attributesPost(newGermplasmAttributeList);
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }
        Map<String, BrAPIGermplasmAttribute> allGermplasmAttributes = new HashMap<>(existingGermplasmAttributeMap);
        newGermplasmAttributeList.forEach(attribute -> allGermplasmAttributes.put(attribute.getAttributeName(), attribute));

        // Create new germplasm attribute values
        // TODO: Should this overwrite existing germplasm attribute values?
        List<BrAPIGermplasmAttributeValue> newGermplasmAttributeValueList = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            for (GermplasmAttribute attribute: pedigreeImport.getGermplasm().getGermplasmAttributes()) {
                BrAPIGermplasmAttributeValue newAttributeValue = new BrAPIGermplasmAttributeValue();
                newAttributeValue.setValue(attribute.getAttributeValue());

                BrAPIGermplasm germplasm = allGermplasm.get(pedigreeImport.getGermplasm().getGermplasmName());
                newAttributeValue.setGermplasmDbId(germplasm.getGermplasmDbId());

                BrAPIGermplasmAttribute germplasmAttribute = allGermplasmAttributes.get(attribute.getAttributeName());
                newAttributeValue.setAttributeDbId(germplasmAttribute.getAttributeDbId());
                newGermplasmAttributeValueList.add(newAttributeValue);
            }
        }

        GermplasmAttributeValuesApi germplasmAttributeValuesApi = brAPIProvider.getGermplasmAttributeValuesApi(BrAPIClientType.CORE);
        try {
            germplasmAttributeValuesApi.attributevaluesPost(newGermplasmAttributeValueList);
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Create new OUs
        List<BrAPIObservationUnit> newObservationUnitList = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            ObservationUnit observationUnit = pedigreeImport.getObservationUnit();
            if (!existingOUMap.containsKey(observationUnit.getObservationUnitName())) {

                //TODO: These can probably go in a separate method in the query service
                BrAPIObservationUnit newObservationUnit = new BrAPIObservationUnit();
                newObservationUnit.setObservationUnitName(observationUnit.getObservationUnitName());

                BrAPIObservationUnitHierarchyLevel level = new BrAPIObservationUnitHierarchyLevel();
                level.setLevelName(observationUnit.getObservationLevel());

                BrAPIGermplasm germplasm = allGermplasm.get(pedigreeImport.getGermplasm().getGermplasmName());
                newObservationUnit.setGermplasmDbId(germplasm.getGermplasmDbId());

                List<BrAPIExternalReference> externalReferences = new ArrayList<>();
                //TODO: Should we be checking this back here, or depending on the user?
                BrAPIExternalReference brAPIExternalReference = new BrAPIExternalReference();
                brAPIExternalReference.setReferenceSource(BrAPIQueryService.OU_ID_REFERENCE_SOURCE);
                brAPIExternalReference.setReferenceID(observationUnit.getObservationUnitPermanentID());
                externalReferences.add(brAPIExternalReference);

                for (ExternalReference externalReference: observationUnit.getExternalReferences()) {
                    BrAPIExternalReference newBrAPIExternalReference = new BrAPIExternalReference();
                    newBrAPIExternalReference.setReferenceSource(externalReference.getReferenceSource());
                    newBrAPIExternalReference.setReferenceID(externalReference.getReferenceID());
                    externalReferences.add(newBrAPIExternalReference);
                }
                newObservationUnit.setExternalReferences(externalReferences);
                newObservationUnitList.add(newObservationUnit);
            }
        }

        ObservationUnitsApi observationUnitsApi = brAPIProvider.getObservationUnitApi(BrAPIClientType.CORE);
        try {
            observationUnitsApi.observationunitsPost(newObservationUnitList);
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }
        Map<String, BrAPIObservationUnit> allOUs = new HashMap<>(existingOUMap);
        newObservationUnitList.forEach(observationUnit -> allOUs.put(observationUnit.getObservationUnitName(), observationUnit));

        // Create new crosses
        // TODO: Need to add ability to add crosses to existing germplasm without crosses
        List<BrAPICross> newCrosses = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            Cross cross = pedigreeImport.getCross();
            Germplasm germplasm = pedigreeImport.getGermplasm();
            if (cross != null && germplasm != null) {
                // If germplasm is not created in this round, skip it
                // TODO: Make a better way to check if new germplasm was created
                if (!existingGermplasmMap.containsKey(germplasm.getGermplasmName())) {
                    BrAPICross newCross = new BrAPICross();
                    //TODO: Check proper date format
                    //cross.setCrossDateTime(cross.getCrossDateTime());
                    newCross.setCrossName(cross.getCrossName());
                    //TODO: Check that value is legit
                    BrAPICrossType brAPICrossType = BrAPICrossType.valueOf(cross.getCrossType().toUpperCase());
                    newCross.setCrossType(brAPICrossType);

                    List<BrAPIExternalReference> externalReferences = new ArrayList<>();
                    for (ExternalReference externalReference: cross.getExternalReferences()) {
                        BrAPIExternalReference newBrAPIExternalReference = new BrAPIExternalReference();
                        newBrAPIExternalReference.setReferenceSource(externalReference.getReferenceSource());
                        newBrAPIExternalReference.setReferenceID(externalReference.getReferenceID());
                        externalReferences.add(newBrAPIExternalReference);
                    }
                    newCross.setExternalReferences(externalReferences);

                    List<BrAPICrossCrossAttributes> crossAttributes = new ArrayList<>();
                    for (AdditionalInfo crossAttribute: cross.getCrossAttributes()) {
                        BrAPICrossCrossAttributes newCrossAttribute = new BrAPICrossCrossAttributes();
                        newCrossAttribute.setCrossAttributeName(crossAttribute.getAdditionalInfoName());
                        newCrossAttribute.setCrossAttributeValue(crossAttribute.getAdditionalInfoValue());
                        crossAttributes.add(newCrossAttribute);
                    }
                    newCross.crossAttributes(crossAttributes);
                }
            }
        }

        CrossesApi crossesApi = brAPIProvider.getCrossesApi(BrAPIClientType.CORE);
        try {
            crossesApi.crossesPost(newCrosses);
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        //DONE!
    }
}
