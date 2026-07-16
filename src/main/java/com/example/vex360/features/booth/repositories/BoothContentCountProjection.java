package com.example.vex360.features.booth.repositories;

import java.util.UUID;

public interface BoothContentCountProjection {
    UUID getBoothId();

    Long getContentCount();
}
