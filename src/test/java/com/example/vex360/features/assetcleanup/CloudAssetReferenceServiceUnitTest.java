package com.example.vex360.features.assetcleanup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.vex360.features.assetcleanup.services.CloudAssetReferenceService;
import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;

@ExtendWith(MockitoExtension.class)
class CloudAssetReferenceServiceUnitTest {

    @Mock
    private DesignAssetReferenceService designReferenceService;
    @Mock
    private DesignDraftAssetService designDraftAssetService;
    @Mock
    private ExhibitionService exhibitionService;
    @Mock
    private ProductService productService;
    @Mock
    private UserService userService;
    @Mock
    private CompanyService companyService;
    @Mock
    private WithdrawalRequestService withdrawalRequestService;

    private CloudAssetReferenceService referenceService;

    @BeforeEach
    void setUp() {
        referenceService = new CloudAssetReferenceService(
                designReferenceService,
                designDraftAssetService,
                exhibitionService,
                productService,
                userService,
                companyService,
                withdrawalRequestService);
    }

    @Test
    void keepsProductContentAsset() {
        when(productService.isAssetReferenced("video/product-demo")).thenReturn(true);

        assertTrue(referenceService.isReferenced("video/product-demo"));
    }

    @Test
    void keepsUrlOnlyAvatarAsset() {
        when(userService.isAvatarAssetReferenced("avatar/user-1")).thenReturn(true);

        assertTrue(referenceService.isReferenced("avatar/user-1"));
    }

    @Test
    void blankPublicIdIsNotReferencedAndDoesNotQueryDatabase() {
        assertFalse(referenceService.isReferenced(" "));

        verify(designReferenceService, never()).isReferenced(" ");
    }
}
