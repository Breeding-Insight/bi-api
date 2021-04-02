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

package org.breedinginsight.brapps.importer.model.mapping;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.model.BrAPIImportConfigManager;
import org.breedinginsight.brapps.importer.model.base.BrAPIObject;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class BrAPIMappingManager {

    private BrAPIImportConfigManager configManager;

    @Inject
    BrAPIMappingManager(BrAPIImportConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<BrAPIImport> map(BrAPIImportMapping importMapping, Table data) throws UnprocessableEntityException {

        if (importMapping.getMapping() == null) {
            throw new NullPointerException("Mapping must be supplied");
        }

        // Go through the file and do the mapping
        // Validity of mapping is checked during the mapping
        List<BrAPIImport> brAPIImports = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < data.rowCount(); rowIndex++) {

            Optional<BrAPIImportService> optionalImportService = configManager.getImportServiceById(importMapping.getImportTypeId());
            if (optionalImportService.isEmpty()){
                throw new UnprocessableEntityException("Import type with that id does not exist.");
            }
            BrAPIImportService importService = optionalImportService.get();
            BrAPIImport brAPIImport = importService.getImportClass();

            // Run through the brapi fields and look for a match
            Field[] fields = brAPIImport.getClass().getDeclaredFields();
            for (Field field: fields) {
                mapField(brAPIImport, field, importMapping.getMapping(), data, rowIndex);
            }

            brAPIImports.add(brAPIImport);
        }

        return brAPIImports;
    }

    private void mapField(Object parent, Field field, List<BrAPIMappingField> mappings, Table importFile, Integer rowIndex)
            throws UnprocessableEntityException {

        Row focusRow = importFile.row(rowIndex);
        // Process this field
        ImportType type = field.getAnnotation(ImportType.class);
        ImportFieldMetadata metadata;
        if (type == null) {
            return;
        } else if (type.type() != ImportFieldType.LIST) {
            metadata = field.getAnnotation(ImportFieldMetadata.class) != null ?
                    field.getAnnotation(ImportFieldMetadata.class) : field.getType().getAnnotation(ImportFieldMetadata.class);
        } else {
            metadata = field.getAnnotation(ImportFieldMetadata.class) != null ?
                    field.getAnnotation(ImportFieldMetadata.class) : (ImportFieldMetadata) type.clazz().getAnnotation(ImportFieldMetadata.class);
        }

        ImportFieldRequired required = field.getAnnotation(ImportFieldRequired.class);

        List<BrAPIMappingField> foundMappings = new ArrayList<>();
        if (mappings != null) {
             foundMappings = mappings.stream()
                    .filter(mappingField -> mappingField.getObjectId().equals(metadata.id())).collect(Collectors.toList());
        }

        // Check required field is present
        // TODO: Separate into validation method to add other validations later on
        if (required != null && foundMappings.size() == 0) {
            throw new UnprocessableEntityException(String.format(
                    "Required field, %s, not provided", metadata.id()));
        } else if (required == null && foundMappings.size() == 0) {
            return;
        }

        BrAPIMappingField matchedMapping = foundMappings.get(0);
        if (type == null) {
            throw new InternalServerException("BrAPIObject is missing import type annotation");
        } else if (type.type() == ImportFieldType.OBJECT) {

            Boolean objectIsEmpty = fieldObjectIsEmpty(matchedMapping);
            if (required != null && objectIsEmpty) {
                throw new UnprocessableEntityException(String.format(
                        "Required object, %s, was not mapped", metadata.id()));
            } else if (required == null && objectIsEmpty) {
                return;
            }

            BrAPIObject brAPIObject;
            try {
                brAPIObject = (BrAPIObject) field.getType().getDeclaredConstructor().newInstance();
                field.setAccessible(true);
                field.set(parent, brAPIObject);
                field.setAccessible(false);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new InternalServerException(e.toString(), e);
            }

            List<Field> fields = Arrays.asList(brAPIObject.getClass().getDeclaredFields());
            for (Field lowerField: fields) {
                mapField(brAPIObject, lowerField, matchedMapping.getMapping(), importFile, rowIndex);
            }
        } else if (type.type() == ImportFieldType.LIST) {
            //TODO: Current can't handle primitive types, only BrAPIObject types

            if (matchedMapping.getMapping() == null && required != null) {
                throw new UnprocessableEntityException(String.format(
                        "List field, %s, contains no entries", metadata.id()));
            } else if (matchedMapping.getMapping() == null) {
                return;
            }

            List<BrAPIObject> updatedList = new ArrayList<>();
            for (BrAPIMappingField listField: matchedMapping.getMapping()){

                BrAPIObject newObject;
                try {
                    newObject = (BrAPIObject) type.clazz().getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new InternalServerException(e.toString(), e);
                }

                // Populate new object
                List<Field> fields = Arrays.asList(newObject.getClass().getDeclaredFields());
                for (Field lowerField: fields) {
                    mapField(newObject, lowerField, listField.getMapping(), importFile, rowIndex);
                }

                field.setAccessible(true);
                List<BrAPIObject> currentList;
                try {
                    currentList = (List<BrAPIObject>) field.get(parent);
                } catch (IllegalAccessException e) {
                    throw new InternalServerException(e.toString(), e);
                }
                if (currentList != null) updatedList.addAll(currentList);
                else updatedList.add(newObject);
            }

            try {
                field.setAccessible(true);
                field.set(parent, updatedList);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }
        } else if (type.type() == ImportFieldType.RELATIONSHIP) {
            if (required != null && (matchedMapping.getValue() == null ||
                    matchedMapping.getValue().getRelationMap() == null)) {
                throw new UnprocessableEntityException(String.format("Relationship field %s is required", metadata.name()));

            } else if (matchedMapping.getValue() == null || matchedMapping.getValue().getRelationMap() == null) {
                return;

            } else if (matchedMapping.getValue().getRelationMap().getReference() == null ||
                    matchedMapping.getValue().getRelationMap().getReference() == null)
            {
                throw new BadRequestException("Relationship field is not properly formatted");
            }

            BrAPIMappingValue value = matchedMapping.getValue();
            ImportRelation relationship = new ImportRelation();
            relationship.setType(value.getRelationValue());
            relationship.setTarget(value.getRelationMap().getTarget());
            relationship.setReference(value.getRelationMap().getReference());
            try {
                field.setAccessible(true);
                field.set(parent, relationship);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }
        } else {
            // It is a simple type
            //TODO: Do some type checks here

            // Check that request field is properly formatted
            if (required != null && matchedMapping.getValue() == null) {
                throw new UnprocessableEntityException(String.format("Field %s is required", metadata.name()));
            } else if (required == null && matchedMapping.getValue() == null) {
                return;
            } else if (matchedMapping.getValue() != null &&
                    matchedMapping.getValue().getFileFieldName() == null && matchedMapping.getValue().getConstantValue() == null){
                throw new BadRequestException("Basic mapping field must have file field or constant value specified.");
            }

            // Check if the mapping passed a constant value or a mapped value
            if (matchedMapping.getValue().getFileFieldName() != null) {
                // Check that the file as this name
                if (!focusRow.columnNames().contains(matchedMapping.getValue().getFileFieldName())) {
                    throw new UnprocessableEntityException(String.format("Column name %s does not exist in file", matchedMapping.getValue().getFileFieldName()));
                }
                String fileValue = focusRow.getString(matchedMapping.getValue().getFileFieldName());
                if (StringUtils.isBlank(fileValue)) fileValue = null;
                try {
                    field.setAccessible(true);
                    field.set(parent, fileValue);
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            } else if (matchedMapping.getValue().getConstantValue() != null){
                String value = matchedMapping.getValue().getConstantValue();
                if (StringUtils.isBlank(value)) value = null;
                try {
                    field.setAccessible(true);
                    field.set(parent, value);
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            }

        }

    }

    /*
        A mapped object is considered null if no fields in it have been mapped
     */
    private Boolean fieldObjectIsEmpty(BrAPIMappingField mappedObject) {
        if (mappedObject.getMapping() != null){
            for (BrAPIMappingField mappedField: mappedObject.getMapping()) {
                if (mappedField != null) {
                    return false;
                }
            }
        }

        return true;
    }
}
