package com.example.vex360.shared.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.example.vex360.shared.enums.ExhibitionStatus;

@Component
public class StringToExhibitionStatusConverter implements Converter<String, ExhibitionStatus> {

    @Override
    public ExhibitionStatus convert(@NonNull String source) {
        if (source.isBlank() || "ALL".equalsIgnoreCase(source.trim())) {
            return null;
        }
        try {
            return ExhibitionStatus.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
