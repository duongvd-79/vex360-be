package com.example.vex360.features.designrequest.services;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.designrequest.entities.DesignRequest;
import com.example.vex360.features.designrequest.entities.DesignRequestProduct;
import com.example.vex360.features.designrequest.enums.DesignRequestMode;
import com.example.vex360.features.designrequest.repositories.DesignRequestProductRepository;
import com.example.vex360.features.product.entities.Product;
import com.example.vex360.features.product.enums.ProductStatus;
import com.example.vex360.features.product.repositories.ProductRepository;
import com.example.vex360.shared.exceptions.AppException;
import com.example.vex360.shared.exceptions.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Service that manages the allowlist of products associated with a design
 * request.
 * Controls which products are visible/allowed to be linked to hotspots during
 * the design phase.
 */
@Service
@RequiredArgsConstructor
public class DesignRequestProductService {
    private final DesignRequestProductRepository requestProductRepository;
    private final ProductRepository productRepository;
    private final HotspotRepository hotspotRepository;

    /**
     * Initializes the product allowlist for a design request.
     * For redesign, it also automatically includes active baseline products from
     * existing hotspots.
     *
     * @param request            the design request to initialize allowlist for
     * @param selectedProductIds the product identifiers chosen by the exhibitor
     * @throws AppException if product ownership or status validation fails
     */
    @Transactional
    public void initializeAllowlist(DesignRequest request, List<UUID> selectedProductIds) {
        List<UUID> selectedIds = distinctIds(selectedProductIds);
        List<Product> selected = loadActiveCompanyProducts(request, selectedIds);
        Map<UUID, Product> products = selected.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<UUID> requiredIds = new HashSet<>();
        if (request.getMode() == DesignRequestMode.REDESIGN) {
            for (Product product : hotspotRepository.findDistinctProductsByBoothId(request.getBooth().getId())) {
                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw new AppException(ErrorCode.DESIGN_REQUEST_NOT_ELIGIBLE);
                }
                products.put(product.getId(), product);
                requiredIds.add(product.getId());
            }
        }
        products.values().forEach(product -> request.getProducts().add(DesignRequestProduct.builder()
                .designRequest(request)
                .product(product)
                .requiredFromBaseline(requiredIds.contains(product.getId()))
                .build()));
    }

    /**
     * Replaces the optional/non-baseline products in the allowlist.
     * Retains any baseline products.
     *
     * @param request            the design request to update
     * @param selectedProductIds the new list of product identifiers
     * @throws AppException if product ownership or status validation fails
     */
    @Transactional
    public void replaceOptionalProducts(DesignRequest request, List<UUID> selectedProductIds) {
        List<Product> selected = loadActiveCompanyProducts(request, distinctIds(selectedProductIds));
        Set<UUID> requiredIds = request.getProducts().stream()
                .filter(product -> Boolean.TRUE.equals(product.getRequiredFromBaseline()))
                .map(product -> product.getProduct().getId())
                .collect(Collectors.toSet());
        request.getProducts().removeIf(product -> !Boolean.TRUE.equals(product.getRequiredFromBaseline()));
        selected.stream()
                .filter(product -> !requiredIds.contains(product.getId()))
                .forEach(product -> request.getProducts().add(DesignRequestProduct.builder()
                        .designRequest(request)
                        .product(product)
                        .requiredFromBaseline(false)
                        .build()));
    }

    /**
     * Asserts that a product is allowed in the design request allowlist.
     *
     * @param request   the design request to check
     * @param productId the identifier of the product
     * @throws AppException if the product is not allowed
     */
    @Transactional(readOnly = true)
    public void assertProductAllowed(DesignRequest request, UUID productId) {
        if (!requestProductRepository.existsByDesignRequestIdAndProductId(request.getId(), productId)) {
            throw new AppException(ErrorCode.DESIGN_PRODUCT_NOT_ALLOWED);
        }
    }

    private List<Product> loadActiveCompanyProducts(DesignRequest request, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findByIdInAndCompanyId(ids, request.getCompany().getId());
        if (products.size() != ids.size() || products.stream().anyMatch(p -> p.getStatus() != ProductStatus.ACTIVE)) {
            throw new AppException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        return products;
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new HashSet<>();
        for (UUID id : ids) {
            if (id == null || !distinct.add(id)) {
                throw new AppException(ErrorCode.INVALID_DESIGN_DRAFT);
            }
        }
        return List.copyOf(distinct);
    }
}
