package com.example.vex360.features.exhibition.repositories;

import com.example.vex360.features.exhibition.entities.Exhibition;

public interface AdminExhibitionProjection {
    Exhibition getExhibition();

    String getCompanyName();
}
