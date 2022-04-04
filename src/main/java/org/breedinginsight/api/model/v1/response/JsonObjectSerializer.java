package org.breedinginsight.api.model.v1.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class JsonObjectSerializer extends JsonSerializer<JsonObject> {

    private Gson gson = new Gson();
    ObjectMapper mapper = new ObjectMapper();

    @Override
    public void serialize(JsonObject value, JsonGenerator jgen,
                          SerializerProvider provider) throws IOException,
            JsonProcessingException {
        if (value != null) {
            JsonNode jacksonValue = mapper.readTree(value.toString());
            jacksonValue.serialize(jgen, provider);
        }
    }
}
