package online.mytruyen.catalog.support;

import online.mytruyen.catalog.exception.ApiException;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Component
public class Patches {
    private final ObjectMapper mapper;
    private final Validator validator;
    public Patches(ObjectMapper mapper, Validator validator) { this.mapper=mapper; this.validator=validator; }
    public <T> T apply(T current, Map<String,Object> fields, Class<T> type) {
        Map<String,Object> merged = new LinkedHashMap<>(mapper.convertValue(current,
            new tools.jackson.core.type.TypeReference<Map<String,Object>>() {}));
        if (!merged.keySet().containsAll(fields.keySet())) throw new ApiException(400, "Unknown or read-only field");
        merged.putAll(fields);
        try {
            T value = mapper.convertValue(merged, type);
            if (!validator.validate(value).isEmpty()) throw new ApiException(400, "Invalid fields");
            return value;
        } catch (IllegalArgumentException | tools.jackson.core.JacksonException e) {
            throw new ApiException(400, "Invalid fields");
        }
    }
}
