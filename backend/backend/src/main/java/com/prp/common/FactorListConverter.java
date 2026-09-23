package com.prp.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class FactorListConverter implements AttributeConverter<List<Factor>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Factor> list) {
        try {
            return MAPPER.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Factor> convertToEntityAttribute(String json) {
        try {
            return json == null ? new ArrayList<>() : MAPPER.readValue(json, new TypeReference<ArrayList<Factor>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
