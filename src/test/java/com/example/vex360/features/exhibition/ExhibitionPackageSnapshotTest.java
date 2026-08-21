package com.example.vex360.features.exhibition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.exhibition.entities.ExhibitionPackage;
import com.example.vex360.features.packagetemplate.entities.PackageTemplate;
import com.example.vex360.shared.enums.BoothListingPriority;

class ExhibitionPackageSnapshotTest {

    @Test
    void snapshotTemplateTerms_copiesAllBusinessTerms() {
        PackageTemplate template = PackageTemplate.builder()
                .name("Premium")
                .description("Premium benefits")
                .price(new BigDecimal("1000000.00"))
                .currency("VND")
                .maxProductsPerBooth(20)
                .maxEmbeddedVideosPerBooth(4)
                .maxPanoramasPerBooth(3)
                .maxHotspotsPerBooth(12)
                .listingPriority(BoothListingPriority.FEATURED)
                .build();
        ExhibitionPackage exhibitionPackage = new ExhibitionPackage();

        exhibitionPackage.snapshotTemplateTerms(template);
        template.setName("Changed");
        template.setDescription("Changed benefits");
        template.setPrice(new BigDecimal("2000000.00"));
        template.setCurrency("USD");
        template.setMaxProductsPerBooth(1);
        template.setMaxEmbeddedVideosPerBooth(1);
        template.setMaxPanoramasPerBooth(1);
        template.setMaxHotspotsPerBooth(1);
        template.setListingPriority(BoothListingPriority.NORMAL);

        assertEquals("Premium", exhibitionPackage.getPackageNameSnapshot());
        assertEquals("Premium benefits", exhibitionPackage.getPackageDescriptionSnapshot());
        assertEquals(new BigDecimal("1000000.00"), exhibitionPackage.getPriceSnapshot());
        assertEquals("VND", exhibitionPackage.getCurrencySnapshot());
        assertEquals(20, exhibitionPackage.getMaxProductsPerBoothSnapshot());
        assertEquals(4, exhibitionPackage.getMaxEmbeddedVideosPerBoothSnapshot());
        assertEquals(3, exhibitionPackage.getMaxPanoramasPerBoothSnapshot());
        assertEquals(12, exhibitionPackage.getMaxHotspotsPerBoothSnapshot());
        assertEquals(BoothListingPriority.FEATURED, exhibitionPackage.getListingPrioritySnapshot());
    }
}
