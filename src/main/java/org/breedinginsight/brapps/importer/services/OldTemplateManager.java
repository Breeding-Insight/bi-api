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

package org.breedinginsight.brapps.importer.services;

import io.micronaut.context.annotation.Context;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.config.ImportConfigMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportConfigResponse;
import org.breedinginsight.brapps.importer.model.config.ImportFieldConfig;
import org.breedinginsight.brapps.importer.model.config.ImportRelationOptionConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Context
@Singleton
public class OldTemplateManager {

    private Map<String, BrAPIImportService> brAPIImportsMap;

    // brapiimport instead of brapiimportservice
    @Inject
    OldTemplateManager(BrAPIImportService[] importServices) {
        // Get all imports
        brAPIImportsMap = new HashMap<>();
        for (BrAPIImportService importService: importServices) {
            brAPIImportsMap.put(importService.getImportTypeId(), importService);
        }
    }

    public List<BrAPIImportService> getAllImportServices() {
        return new ArrayList<>(brAPIImportsMap.values());
    }

    public List<ImportConfigResponse> getAllImportTypeConfigs() {
        return getAllImportServices().stream()
                .map(importService -> getTypeConfig(importService.getImportClass().getClass()))
                .collect(Collectors.toList());
    }

    public ImportConfigResponse getTypeConfig(Class c) {

        Field[] objectFields = c.getDeclaredFields();
        ImportConfigMetadata metadata = (ImportConfigMetadata) c.getAnnotation(ImportConfigMetadata.class);
        ImportConfigResponse importConfigResponse = new ImportConfigResponse();
        importConfigResponse.setId(String.valueOf(metadata.dbId()));
        importConfigResponse.setName(metadata.name());
        importConfigResponse.setDescription(metadata.description());

        // Construct the objects in brapi
        List<ImportFieldConfig> importObjectConfigs = new ArrayList<>();
        for (Field objectField: objectFields) {
            // Recurses down to population config
            ImportFieldConfig objectConfig = getObjectField(objectField);
            importObjectConfigs.add(objectConfig);
        }

        importConfigResponse.setFields(importObjectConfigs);
        return importConfigResponse;
    }

    public Optional<BrAPIImportService> getImportServiceById(String importTypeId) {

        if (brAPIImportsMap.containsKey(importTypeId)) {
            return Optional.of(brAPIImportsMap.get(importTypeId));
        } else {
            return Optional.empty();
        }
    }

    private ImportFieldConfig getObjectField(Field field) {

        ImportFieldType fieldType = field.getAnnotation(ImportFieldType.class);
        if (fieldType == null) return null;

        ImportFieldConfig fieldConfig = constructFieldConfig(field);

        // Dive deeper if necessary
        if (fieldType.type() == ImportFieldTypeEnum.OBJECT) {
            List<Field> fields = Arrays.asList(field.getType().getDeclaredFields());
            List<ImportFieldConfig> subFieldConfigs = fields.stream()
                    .map(subField -> getObjectField(subField))
                    .filter(subField -> subField != null)
                    .collect(Collectors.toList());
            fieldConfig.setFields(subFieldConfigs);
        } else if (fieldType.type() == ImportFieldTypeEnum.LIST) {
            List<Field> fields = Arrays.asList(fieldType.clazz().getDeclaredFields());
            List<ImportFieldConfig> subFieldConfigs = fields.stream()
                    .map(subField -> getObjectField(subField))
                    .filter(subField -> subField != null)
                    .collect(Collectors.toList());
            fieldConfig.setFields(subFieldConfigs);
        }
        else if (fieldType.type() == ImportFieldTypeEnum.RELATIONSHIP) {
            fieldConfig = constructRelationField(field);
        }

        return fieldConfig;
    }

    private ImportFieldConfig constructFieldConfig(Field field) {

        ImportFieldMetadata fieldMetadata = field.getAnnotation(ImportFieldMetadata.class);
        ImportFieldType type = field.getAnnotation(ImportFieldType.class);
        Class c = type != null && type.type() == ImportFieldTypeEnum.LIST ? type.clazz() : field.getType();
        ImportFieldMetadata classMetadata = (ImportFieldMetadata) c.getAnnotation(ImportFieldMetadata.class);
        ImportMappingRequired required = field.getAnnotation(ImportMappingRequired.class);
        ImportFieldMetadata metadata = fieldMetadata != null ? fieldMetadata : classMetadata;
        // This is in the case of metadata missing on a java type, e.g. String
        //TODO: Give more detail, such as which import this errored out on
        if (metadata == null) throw new InternalServerException("Metadata missing on java primitive");

        ImportFieldConfig fieldConfig = new ImportFieldConfig();
        fieldConfig.setId(metadata.id());
        fieldConfig.setName(metadata.name());
        fieldConfig.setDescription(metadata.description());
        fieldConfig.setType(type.type());
        fieldConfig.setRequired(required != null);
        fieldConfig.setCollectTime(type.collectTime());
        return fieldConfig;
    }

    private ImportFieldConfig constructRelationField(Field field) {
        ImportFieldConfig fieldConfig = constructFieldConfig(field);

        ImportFieldRelations relations = field.getAnnotation(ImportFieldRelations.class);
        List<ImportRelationOptionConfig> relationConfigs = new ArrayList<>();
        for (ImportFieldRelation relation: relations.relations()) {
            ImportRelationOptionConfig relationConfig = new ImportRelationOptionConfig();
            relationConfig.setId(relation.type().getId());
            relationConfig.setName(relation.type().getName());
            relationConfig.setDescription(!relation.description().isEmpty() ? relation.description() : relation.type().getDefaultDescription());

            // Relation type specific logic
            if (relation.type() == ImportRelationType.DB_LOOKUP || relation.type() == ImportRelationType.DB_LOOKUP_CONSTANT_VALUE) {
                relationConfig.setImportFields(relation.importFields());
            }

            relationConfigs.add(relationConfig);
        }

        fieldConfig.setRelationOptions(relationConfigs);
        return fieldConfig;
    }
}
