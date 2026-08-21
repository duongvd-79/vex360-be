package com.example.vex360.features.analytics.dtos.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizerBoothRankingPageDTO {
    private List<OrganizerBoothRankingItemDTO> items;
    private long total;
    private int page;
    private int size;
}
