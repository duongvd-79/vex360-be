package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.features.exhibition.events.StoragePackagePaymentCompletedEvent;
import com.example.vex360.features.exhibition.services.StoragePaymentService;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;

@ExtendWith(MockitoExtension.class)
class StoragePackageServiceUnitTest {

    @Mock
    StoragePackageRepository storagePackageRepository;
    @Mock
    StoragePackageOrderRepository storagePackageOrderRepository;
    @Mock
    CompanyRepository companyRepository;
    @Mock
    StoragePaymentService storagePaymentService;
    @Mock
    CompanyService companyService;
    @InjectMocks
    StoragePackageService service;

    @Test
    void completedPaymentUpdatesOrderAndCompanyQuota() {
        Company company = Company.builder().id(UUID.randomUUID()).storageQuotaBytes(100L).build();
        StoragePackage storagePackage = StoragePackage.builder().quotaBytes(50L).build();
        StoragePackageOrder order = StoragePackageOrder.builder().id(7).company(company)
                .storagePackage(storagePackage).status(StoragePackageOrderStatus.PENDING).build();
        when(storagePackageOrderRepository.findById(7)).thenReturn(Optional.of(order));

        service.handleStoragePackagePaymentCompleted(new StoragePackagePaymentCompletedEvent(this, 7));

        assertEquals(StoragePackageOrderStatus.PAID, order.getStatus());
        assertEquals(150L, company.getStorageQuotaBytes());
        verify(storagePackageOrderRepository).save(order);
        verify(companyRepository).save(company);
    }
}
