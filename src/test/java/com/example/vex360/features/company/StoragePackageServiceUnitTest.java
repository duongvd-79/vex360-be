package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.vex360.features.company.dtos.request.CreateStoragePackageOrderRequest;
import com.example.vex360.features.company.dtos.request.CreateStoragePackageRequest;
import com.example.vex360.features.company.dtos.response.AdminStoragePackageOrderResponseDTO;
import com.example.vex360.features.company.dtos.response.StoragePackageOrderResponseDTO;
import com.example.vex360.features.company.dtos.response.StoragePackageResponseDTO;
import com.example.vex360.features.company.dtos.response.StorageUsageResponseDTO;
import com.example.vex360.features.company.entities.Company;
import com.example.vex360.features.company.entities.StoragePackage;
import com.example.vex360.features.company.entities.StoragePackageOrder;
import com.example.vex360.features.company.repositories.CompanyRepository;
import com.example.vex360.features.company.repositories.StoragePackageOrderRepository;
import com.example.vex360.features.company.repositories.StoragePackageRepository;
import com.example.vex360.features.exhibition.events.StoragePackagePaymentCompletedEvent;
import com.example.vex360.features.exhibition.services.StoragePaymentService;
import com.example.vex360.features.user.entities.User;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.company.services.StoragePackageService;
import com.example.vex360.shared.enums.StoragePackageOrderStatus;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

@ExtendWith(MockitoExtension.class)
class StoragePackageServiceUnitTest {

    @Mock
    private StoragePackageRepository storagePackageRepository;

    @Mock
    private StoragePackageOrderRepository storagePackageOrderRepository;

    @Mock
    private StoragePaymentService storagePaymentService;

    @Mock
    private CompanyService companyService;

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private StoragePackageService storagePackageService;

    private User currentUser;
    private Company company;
    private StoragePackage storagePackage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storagePackageService, "returnUrl", "http://return");
        ReflectionTestUtils.setField(storagePackageService, "cancelUrl", "http://cancel");

        currentUser = User.builder().id(UUID.randomUUID()).email("user@example.com").build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Company X")
                .storageUsedBytes(200L)
                .storageQuotaBytes(1000L)
                .build();
        storagePackage = StoragePackage.builder()
                .id(1)
                .name("Gold package")
                .description("Desc")
                .quotaBytes(2000L)
                .priceVnd(100000L)
                .isActive(true)
                .build();
    }

    @Test
    void testListActivePackages_Success() {
        when(storagePackageRepository.findByIsActiveTrueOrderByPriceVndAsc()).thenReturn(List.of(storagePackage));

        List<StoragePackageResponseDTO> response = storagePackageService.listActivePackages();

        assertEquals(1, response.size());
        assertEquals("Gold package", response.get(0).getName());
        assertEquals(2000L, response.get(0).getQuotaBytes());
        assertEquals(100000L, response.get(0).getPriceVnd());
    }

    @Test
    void testCreateOrder_PackageNotFound_ThrowsException() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        when(storagePackageRepository.findById(1)).thenReturn(Optional.empty());

        CreateStoragePackageOrderRequest request = new CreateStoragePackageOrderRequest();
        request.setPackageId(1);

        AppException exception = assertThrows(AppException.class, () -> {
            storagePackageService.createOrder(currentUser, request);
        });

        assertEquals(ErrorCode.STORAGE_PACKAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testCreateOrder_Success() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);
        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));

        when(storagePackageOrderRepository.save(any(StoragePackageOrder.class))).thenAnswer(invocation -> {
            StoragePackageOrder o = invocation.getArgument(0);
            o.setId(10);
            return o;
        });

        when(storagePaymentService.createPayment(
                eq(10), anyLong(), eq(100000L), anyString(), eq("http://return"), eq("http://cancel")))
                .thenReturn("https://checkout.url");

        CreateStoragePackageOrderRequest request = new CreateStoragePackageOrderRequest();
        request.setPackageId(1);
        StoragePackageOrderResponseDTO dto = storagePackageService.createOrder(currentUser, request);

        assertNotNull(dto);
        assertEquals(Integer.valueOf(10), dto.getOrderId());
        assertEquals("https://checkout.url", dto.getCheckoutUrl());
        assertEquals("Gold package", dto.getPackageName());
        assertEquals(2000L, dto.getQuotaBytes());
        assertEquals(100000L, dto.getAmountVnd());
        assertEquals("PENDING", dto.getStatus());

        verify(storagePackageOrderRepository, Mockito.times(2)).save(any(StoragePackageOrder.class));
        verify(storagePaymentService).createPayment(
                eq(10), anyLong(), eq(100000L), anyString(), eq("http://return"), eq("http://cancel"));
    }

    @Test
    void testGetUsage_Success_QuotaGreaterThanZero() {
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);

        StorageUsageResponseDTO dto = storagePackageService.getUsage(currentUser);

        assertEquals(200L, dto.getUsedBytes());
        assertEquals(1000L, dto.getQuotaBytes());
        assertEquals(20.0, dto.getUsedPercentage());
    }

    @Test
    void testGetUsage_Success_QuotaIsZero() {
        company.setStorageQuotaBytes(0L);
        when(companyService.getCompanyEntityForCurrentUser(currentUser)).thenReturn(company);

        StorageUsageResponseDTO dto = storagePackageService.getUsage(currentUser);

        assertEquals(200L, dto.getUsedBytes());
        assertEquals(0L, dto.getQuotaBytes());
        assertEquals(0.0, dto.getUsedPercentage());
    }

    // ================= listAllPackages =================

    @Test
    void listAllPackages_Success_ReturnsAllPackagesRegardlessOfStatus() {
        StoragePackage inactivePackage = StoragePackage.builder()
                .id(2).name("Inactive package").quotaBytes(500L).priceVnd(0L).isActive(false).build();
        when(storagePackageRepository.findAllByOrderByPriceVndAsc())
                .thenReturn(List.of(storagePackage, inactivePackage));

        List<StoragePackageResponseDTO> response = storagePackageService.listAllPackages();

        assertEquals(2, response.size());
        assertEquals("Gold package", response.get(0).getName());
        assertEquals("Inactive package", response.get(1).getName());
    }

    // ================= createPackage =================

    @Test
    void createPackage_NameNotDuplicated_SavesAndReturnsActivePackage() {
        CreateStoragePackageRequest request = new CreateStoragePackageRequest();
        request.setName("Platinum package");
        request.setDescription("Best plan");
        request.setQuotaBytes(5000L);
        request.setPriceVnd(200000L);

        when(storagePackageRepository.existsByNameIgnoreCase("Platinum package")).thenReturn(false);
        when(storagePackageRepository.save(any(StoragePackage.class))).thenAnswer(invocation -> {
            StoragePackage saved = invocation.getArgument(0);
            saved.setId(3);
            return saved;
        });

        StoragePackageResponseDTO response = storagePackageService.createPackage(request);

        assertEquals("Platinum package", response.getName());
        assertEquals(5000L, response.getQuotaBytes());
        assertTrue(response.getIsActive());
        verify(storagePackageRepository).save(any(StoragePackage.class));
    }

    @Test
    void createPackage_NameDuplicated_ThrowsStoragePackageNameDuplicated() {
        CreateStoragePackageRequest request = new CreateStoragePackageRequest();
        request.setName("Gold package");
        request.setQuotaBytes(2000L);
        request.setPriceVnd(100000L);

        when(storagePackageRepository.existsByNameIgnoreCase("Gold package")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> storagePackageService.createPackage(request));

        assertEquals(ErrorCode.STORAGE_PACKAGE_NAME_DUPLICATED, exception.getErrorCode());
        verify(storagePackageRepository, Mockito.never()).save(any(StoragePackage.class));
    }

    // ================= togglePackageStatus =================

    @Test
    void togglePackageStatus_CurrentlyActive_TogglesToInactive() {
        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));
        when(storagePackageRepository.save(any(StoragePackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoragePackageResponseDTO response = storagePackageService.togglePackageStatus(1);

        assertFalse(response.getIsActive());
        verify(storagePackageRepository).save(storagePackage);
    }

    @Test
    void togglePackageStatus_CurrentlyInactive_TogglesToActive() {
        storagePackage.setIsActive(false);
        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));
        when(storagePackageRepository.save(any(StoragePackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoragePackageResponseDTO response = storagePackageService.togglePackageStatus(1);

        assertTrue(response.getIsActive());
    }

    @Test
    void togglePackageStatus_PackageNotFound_ThrowsStoragePackageNotFound() {
        when(storagePackageRepository.findById(99)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> storagePackageService.togglePackageStatus(99));

        assertEquals(ErrorCode.STORAGE_PACKAGE_NOT_FOUND, exception.getErrorCode());
    }

    // ================= handleStoragePackagePaymentCompleted =================

    @Test
    void handleStoragePackagePaymentCompleted_OrderFound_MarksPaidAndIncreasesQuota() {
        StoragePackageOrder order = StoragePackageOrder.builder()
                .id(10)
                .company(company)
                .storagePackage(storagePackage)
                .orderCode(123456L)
                .amountVnd(100000L)
                .status(StoragePackageOrderStatus.PENDING)
                .build();
        when(storagePackageOrderRepository.findById(10)).thenReturn(Optional.of(order));

        StoragePackagePaymentCompletedEvent event = new StoragePackagePaymentCompletedEvent(this, 10);
        storagePackageService.handleStoragePackagePaymentCompleted(event);

        assertEquals(StoragePackageOrderStatus.PAID, order.getStatus());
        assertNotNull(order.getPaidAt());
        assertEquals(3000L, company.getStorageQuotaBytes()); // 1000 (initial) + 2000 (package quota)
        verify(storagePackageOrderRepository).save(order);
        verify(companyRepository).save(company);
    }

    @Test
    void handleStoragePackagePaymentCompleted_OrderNotFound_ThrowsStoragePackageOrderNotFound() {
        when(storagePackageOrderRepository.findById(99)).thenReturn(Optional.empty());

        StoragePackagePaymentCompletedEvent event = new StoragePackagePaymentCompletedEvent(this, 99);

        AppException exception = assertThrows(AppException.class,
                () -> storagePackageService.handleStoragePackagePaymentCompleted(event));

        assertEquals(ErrorCode.STORAGE_PACKAGE_ORDER_NOT_FOUND, exception.getErrorCode());
        verify(companyRepository, Mockito.never()).save(any(Company.class));
    }

    // ================= updatePackage =================

    @Test
    void updatePackage_SameNameDifferentCase_SkipsDuplicateCheckAndUpdates() {
        CreateStoragePackageRequest request = new CreateStoragePackageRequest();
        request.setName("GOLD PACKAGE"); // same name, different case as existing "Gold package"
        request.setDescription("Updated desc");
        request.setQuotaBytes(2500L);
        request.setPriceVnd(120000L);

        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));
        when(storagePackageRepository.save(any(StoragePackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoragePackageResponseDTO response = storagePackageService.updatePackage(1, request);

        assertEquals("GOLD PACKAGE", response.getName());
        assertEquals(2500L, response.getQuotaBytes());
        verify(storagePackageRepository, Mockito.never()).existsByNameIgnoreCase(anyString());
    }

    @Test
    void updatePackage_NewNameNotDuplicated_UpdatesAndSaves() {
        CreateStoragePackageRequest request = new CreateStoragePackageRequest();
        request.setName("Diamond package");
        request.setDescription("Updated desc");
        request.setQuotaBytes(3000L);
        request.setPriceVnd(150000L);

        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));
        when(storagePackageRepository.existsByNameIgnoreCase("Diamond package")).thenReturn(false);
        when(storagePackageRepository.save(any(StoragePackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoragePackageResponseDTO response = storagePackageService.updatePackage(1, request);

        assertEquals("Diamond package", response.getName());
        assertEquals(3000L, response.getQuotaBytes());
    }

    @Test
    void updatePackage_NewNameDuplicated_ThrowsStoragePackageNameDuplicated() {
        CreateStoragePackageRequest request = new CreateStoragePackageRequest();
        request.setName("Silver package");
        request.setQuotaBytes(1000L);
        request.setPriceVnd(50000L);

        when(storagePackageRepository.findById(1)).thenReturn(Optional.of(storagePackage));
        when(storagePackageRepository.existsByNameIgnoreCase("Silver package")).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> storagePackageService.updatePackage(1, request));

        assertEquals(ErrorCode.STORAGE_PACKAGE_NAME_DUPLICATED, exception.getErrorCode());
        verify(storagePackageRepository, Mockito.never()).save(any(StoragePackage.class));
    }

    @Test
    void updatePackage_PackageNotFound_ThrowsStoragePackageNotFound() {
        CreateStoragePackageRequest request = new CreateStoragePackageRequest();
        request.setName("Any package");
        request.setQuotaBytes(1000L);
        request.setPriceVnd(50000L);

        when(storagePackageRepository.findById(99)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> storagePackageService.updatePackage(99, request));

        assertEquals(ErrorCode.STORAGE_PACKAGE_NOT_FOUND, exception.getErrorCode());
    }

    // ================= listAllOrders =================

    @Test
    void listAllOrders_Success_ReturnsMappedAdminOrderList() {
        StoragePackageOrder order = StoragePackageOrder.builder()
                .id(10)
                .company(company)
                .storagePackage(storagePackage)
                .orderCode(123456L)
                .amountVnd(100000L)
                .status(StoragePackageOrderStatus.PAID)
                .build();
        when(storagePackageOrderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(order));

        List<AdminStoragePackageOrderResponseDTO> response = storagePackageService.listAllOrders();

        assertEquals(1, response.size());
        assertEquals(company.getName(), response.get(0).getCompanyName());
        assertEquals("Gold package", response.get(0).getPackageName());
        assertEquals("PAID", response.get(0).getStatus());
    }
}
