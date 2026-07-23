package com.example.vex360.features.analytics.dtos.response;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExhibitionAnalyticsOverviewDTO {
    private String id; // uuid của triển lãm
    private String code; // mã hiển thị, vd "EXH-001"
    private String name;
    private String status;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private long boothCount;
}
