package com.example.vex360.features.booth.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotspotCornersDTO {
    private List<Double> tl;
    private List<Double> tr;
    private List<Double> bl;
    private List<Double> br;
}
