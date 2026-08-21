package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.QueryUtils;

import com.example.vex360.features.booth.enums.BoothStatus;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.product.enums.ProductStatus;

class HotspotRepositoryQueryTest {

    @Test
    void displayedProductQuerySortsBySelectedProductName() throws NoSuchMethodException {
        Query query = HotspotRepository.class
                .getMethod("searchDisplayedProductsForVisitor", UUID.class, String.class,
                        ProductStatus.class, BoothStatus.class, Pageable.class)
                .getAnnotation(Query.class);

        String sortedQuery = QueryUtils.applySorting(query.value(), Sort.by("name"));

        assertTrue(sortedQuery.contains("order by product.name asc"), sortedQuery);
    }
}
