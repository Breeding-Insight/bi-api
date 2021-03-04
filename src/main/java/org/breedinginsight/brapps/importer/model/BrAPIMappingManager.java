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

package org.breedinginsight.brapps.importer.model;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.base.BrAPIObject;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldRequired;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.ImportMetadata;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    public BrAPIImport map(BrAPIImportMapping importMapping) throws UnprocessableEntityException {

        if (importMapping.getObjects() == null) {
            throw new NullPointerException("Mapping must be supplied");
        }

        // Get the import type
        Optional<BrAPIImport> brAPIImportOptional = configManager.getTypeConfigById(importMapping.getImportTypeId());

        if (brAPIImportOptional.isEmpty()) {
            throw new UnprocessableEntityException("Import type with that id does not exist.");
        }

        BrAPIImport brAPIImport = brAPIImportOptional.get();

        // Go through the file and do the mapping
        // Validity of mapping is checked during the mapping
        for (int rowIndex = 0; rowIndex < importMapping.getFile().rowCount(); rowIndex++) {

            // Run through the brapi fields and look for a match
            Field[] fields = brAPIImport.getClass().getDeclaredFields();
            for (Field field: fields) {
                // e.g germplasm, cross
                //TODO: This can probably be consolidated. Its also used in the import config generation
                //TODO: Should this be in the import class map method?
                ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class) != null ?
                        field.getAnnotation(ImportFieldMetadata.class) : field.getType().getAnnotation(ImportFieldMetadata.class);
                ImportFieldRequired required = field.getAnnotation(ImportFieldRequired.class);

                List<BrAPIMappingObject> matchedObjects = importMapping.getObjects().stream()
                        .filter(objectMapping -> objectMapping.getObjectId().equals(metadata.id()))
                        .collect(Collectors.toList());

                if (matchedObjects.size() == 0 & required != null) {
                    //TODO: Add to mapping validations for multiple error return
                    throw new BadRequestException(String.format("Required field, %s was not passed", metadata.id()));
                }

                //TODO: Handle top level list types
                if (matchedObjects.size() >= 1) {
                    BrAPIMappingObject matchedObject = matchedObjects.get(0);

                    // Map through the fields
                    BrAPIObject brAPIObject;
                    try {
                        brAPIObject = (BrAPIObject) field.getType().getDeclaredConstructor(null).newInstance();
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        throw new InternalServerException(e.toString());
                    }

                    //TODO: Catch all the errors from this
                    mapObject(brAPIObject, matchedObject, metadata, importMapping.getFile(), rowIndex);

                    // Assign to our import class
                    try {
                        field.setAccessible(true);
                        field.set(brAPIImport, brAPIObject);
                        field.setAccessible(false);
                    } catch (IllegalAccessException e) {
                        throw new InternalServerException(e.toString());
                    }
                }
            }

        }

        return brAPIImport;
    }

    /* Maps the file to the object. Recurses down the object tree. */
    private void mapObject(BrAPIObject brAPIObject, BrAPIMappingObject mapping,
                                         ImportFieldMetadata objectMetadata, Table importFile, Integer rowIndex) throws UnprocessableEntityException {

        Row focusRow = importFile.row(rowIndex);
        Field[] fields = brAPIObject.getClass().getDeclaredFields();
        for (Field field: fields) {
            // eg. externalReferences, germplasmName
            ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class) != null ?
                    field.getAnnotation(ImportFieldMetadata.class) : field.getType().getAnnotation(ImportFieldMetadata.class);
            ImportType type = field.getAnnotation(ImportType.class);
            ImportFieldRequired required = field.getAnnotation(ImportFieldRequired.class);

            // Check required field is present
            // TODO: Separate into validation method to add other validations later on
            if (required != null && !mapping.getFields().containsKey(metadata.id())) {
                throw new UnprocessableEntityException(String.format(
                        "Required field, %s.%s, not provided", objectMetadata.id(), metadata.id()));
            } else if (required == null && !mapping.getFields().containsKey(metadata.id())) {
                continue;
            }

            // Map to the specific types of objects
            if (type == null) {
                throw new InternalServerException("BrAPIObject is missing import type annotation");
            }
            if (type.type() == ImportFieldType.LIST) {
                // TODO: Recurse down
                continue;
            } else if (type.type() == ImportFieldType.RELATIONSHIP) {
                //TODO: Do the lookups
                continue;
            } else {
                // It is a simple type
                //TODO: Do some type checks here

                // Check if the mapping passed a constant value or a mapped value
                BrAPIMappingField mappingField = mapping.getFields().get(metadata.id());
                if (mappingField.getFileFieldName() != null) {
                    // Check that the file as this name
                    if (!focusRow.columnNames().contains(mappingField.getFileFieldName())) {
                        throw new UnprocessableEntityException(String.format("Column name %s does not exist in file", mappingField.getFileFieldName()));
                    }
                    String fileValue = focusRow.getString(mappingField.getFileFieldName());
                    try {
                        field.setAccessible(true);
                        field.set(brAPIObject, fileValue);
                        field.setAccessible(false);
                    } catch (IllegalAccessException e) {
                        throw new InternalServerException(e.toString());
                    }
                }

            }
        }
    }
}
