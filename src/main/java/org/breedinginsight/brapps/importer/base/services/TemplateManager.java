package org.breedinginsight.brapps.importer.base.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.base.model.config.*;
import org.breedinginsight.brapps.importer.base.model.BrAPIImport;
import org.breedinginsight.brapps.importer.base.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.base.model.mapping.MappingField;
import org.breedinginsight.brapps.importer.base.model.mapping.MappingValue;
import org.breedinginsight.brapps.importer.imports.experiment.ExperimentProcessor;
import org.breedinginsight.brapps.importer.imports.germplasm.GermplasmProcessor;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides processors for the imports. Accesses BrAPIImport classes through the processors getSupportedImport object
 */
@Slf4j
@Singleton
public class TemplateManager {
    private List<Processor> processors;

    TemplateManager(Provider<GermplasmProcessor> germplasmProcessorProvider, Provider<ExperimentProcessor> experimentProcessorProvider) {
        this.processors = List.of(
                experimentProcessorProvider.get(),
                germplasmProcessorProvider.get()
        );

        // TODO: Check that all of the imports and processors are configured correctly
        // Check ids exist
        // Check that supported import is not null
    }

    public Optional<Processor> getProcessor(Class<? extends BrAPIImport> importClass) {
        for (Processor processor: processors) {
            if (processor.getSupportedImport().getClass().equals(importClass)) {
                return Optional.of(processor);
            }
        }
        return Optional.empty();
    }

    public Optional<BrAPIImport> getTemplate(int importId) {
        // Iterate through all of the brapi imports
        for (Processor processor: processors) {
            Class<? extends BrAPIImport> importClass = processor.getSupportedImport().getClass();
            ImportConfigMetadata metadata = importClass.getAnnotation(ImportConfigMetadata.class);
            if (importId == metadata.dbId()) {
                return Optional.of(processor.getSupportedImport());
            }
        }
        return Optional.empty();
    }

    public ImportMapping generateMappingForTemplate(Class<? extends BrAPIImport> importClass) {

        ImportMapping importMapping = new ImportMapping();
        // Set mapping
        Field[] fields = importClass.getDeclaredFields();
        importMapping.setMappingConfig(processFields(fields));
        // Set fields
        importMapping.setSaved(false);
        // Set template id
        ImportConfigMetadata metadata = importClass.getAnnotation(ImportConfigMetadata.class);
        importMapping.setImporterTemplateId(metadata.dbId());
        return importMapping;
    }

    private List<MappingField> processFields(Field[] fields) {

        List<MappingField> mappingFields = new ArrayList<>();
        for (Field field: fields) {

            // If object recursively generate
            ImportFieldType type = field.getAnnotation(ImportFieldType.class);
            ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class);

            // If not a brapi import field, skip it
            if (type == null) continue;

            // Create new mapping field
            MappingField mappingField = new MappingField();
            mappingField.setObjectId(metadata.id());

            if (type.type() == ImportFieldTypeEnum.OBJECT) {
                Field[] subFields = field.getType().getDeclaredFields();
                mappingField.setMapping(processFields(subFields));
            } else {
                // If primitive field, add a mapping for field to field
                MappingValue value = new MappingValue();
                value.setFileFieldName(metadata.name());
                mappingField.setValue(value);
            }
            mappingFields.add(mappingField);
        }
        return mappingFields;
    }

    public List<ImportConfigResponse> getAllImportTemplates() {

        List<ImportConfigResponse> templates = new ArrayList<>();
        for (Processor processor: processors) {
            BrAPIImport brAPIImport = processor.getSupportedImport();
            ImportConfigMetadata metadata = brAPIImport.getClass().getAnnotation(ImportConfigMetadata.class);
            ImportConfigResponse importConfigResponse = new ImportConfigResponse();
            importConfigResponse.setId(metadata.dbId());
            importConfigResponse.setName(metadata.name());
            importConfigResponse.setDescription(metadata.description());

            // Construct the template configuration
            Field[] objectFields = brAPIImport.getClass().getDeclaredFields();
            List<ImportFieldConfig> importObjectConfigs = new ArrayList<>();
            for (Field objectField: objectFields) {
                // Recurses down to population config
                ImportFieldConfig objectConfig = getObjectField(objectField);
                importObjectConfigs.add(objectConfig);
            }

            importConfigResponse.setFields(importObjectConfigs);
            templates.add(importConfigResponse);
        }

        return templates;
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

}
