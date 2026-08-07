package com.example.vex360.features.assetcleanup.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.company.services.CompanyService;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.features.designrequest.services.DesignDraftAssetService;
import com.example.vex360.features.exhibition.services.ExhibitionService;
import com.example.vex360.features.product.services.ProductService;
import com.example.vex360.features.user.services.UserService;
import com.example.vex360.features.wallet.services.WithdrawalRequestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CloudAssetReferenceService {

    private final DesignAssetReferenceService designAssetReferenceService;
    private final DesignDraftAssetService designDraftAssetService;
    private final ExhibitionService exhibitionService;
    private final ProductService productService;
    private final UserService userService;
    private final CompanyService companyService;
    private final WithdrawalRequestService withdrawalRequestService;

    @Transactional(readOnly = true)
    public boolean isReferenced(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return false;
        }
        return designAssetReferenceService.isReferenced(publicId)
                || designDraftAssetService.isAssetReferenced(publicId)
                || exhibitionService.isAssetReferenced(publicId)
                || productService.isAssetReferenced(publicId)
                || userService.isAvatarAssetReferenced(publicId)
                || companyService.isLogoAssetReferenced(publicId)
                || withdrawalRequestService.isProofAssetReferenced(publicId);
    }
}
