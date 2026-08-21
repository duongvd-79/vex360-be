package com.example.vex360.shared.utils;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableUtils {
    private PageableUtils() {
    }

    public static Pageable remapSort(Pageable pageable, Map<String, String> aliases) {
        Sort mappedSort = Sort.by(pageable.getSort().stream()
                .map(order -> new Sort.Order(
                        order.getDirection(),
                        aliases.getOrDefault(order.getProperty(), order.getProperty()),
                        order.getNullHandling()))
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mappedSort);
    }
}
