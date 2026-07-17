package com.example.vex360.features.company;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.example.vex360.features.booth.controllers.ExhibitorBoothController;
import com.example.vex360.features.booth.controllers.ExhibitorMediaAssetController;
import com.example.vex360.features.booth.controllers.OrganizerBoothController;
import com.example.vex360.features.booth.controllers.OrganizerBoothReviewController;
import com.example.vex360.features.company.controllers.CompanyController;
import com.example.vex360.features.company.controllers.StoragePackageController;
import com.example.vex360.shared.config.security.RequireActiveCompany;
import com.example.vex360.features.designrequest.controllers.ExhibitorDesignRequestController;
import com.example.vex360.features.exhibition.controllers.ExhibitorExhibitionController;
import com.example.vex360.features.exhibition.controllers.ExhibitorRegistrationController;
import com.example.vex360.features.exhibition.controllers.OrganizerExhibitionController;
import com.example.vex360.features.product.controllers.ProductCategoryController;
import com.example.vex360.features.product.controllers.ProductController;
import com.example.vex360.shared.controllers.UploadController;
import com.example.vex360.shared.enums.Role;

class CompanyProfileAccessControllerTest {
    @Test
    void exhibitorControllersRequireActiveCompany() {
        assertRequiresRole(Role.EXHIBITOR,
                ExhibitorBoothController.class,
                ExhibitorMediaAssetController.class,
                ExhibitorDesignRequestController.class,
                ExhibitorExhibitionController.class,
                ExhibitorRegistrationController.class,
                ProductController.class,
                ProductCategoryController.class);
    }

    @Test
    void organizerControllersRequireActiveCompany() {
        assertRequiresRole(Role.ORGANIZER,
                OrganizerExhibitionController.class,
                OrganizerBoothController.class,
                OrganizerBoothReviewController.class);
    }

    @Test
    void storageOrderAndUsageRequireActiveExhibitorCompany() {
        assertMethodRequiresRole(StoragePackageController.class, "createOrder", Role.EXHIBITOR);
        assertMethodRequiresRole(StoragePackageController.class, "getUsage", Role.EXHIBITOR);
    }

    @Test
    void onboardingControllersRemainAccessibleWithoutActiveCompany() {
        assertFalse(CompanyController.class.isAnnotationPresent(RequireActiveCompany.class));
        assertFalse(UploadController.class.isAnnotationPresent(RequireActiveCompany.class));
    }

    private void assertRequiresRole(Role role, Class<?>... controllerClasses) {
        Arrays.stream(controllerClasses).forEach(controllerClass -> {
            RequireActiveCompany annotation = controllerClass.getAnnotation(RequireActiveCompany.class);
            assertNotNull(annotation, controllerClass.getSimpleName());
            assertArrayEquals(new Role[] {role}, annotation.roles(), controllerClass.getSimpleName());
        });
    }

    private void assertMethodRequiresRole(Class<?> controllerClass, String methodName, Role role) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        RequireActiveCompany annotation = method.getAnnotation(RequireActiveCompany.class);
        assertNotNull(annotation, methodName);
        assertArrayEquals(new Role[] {role}, annotation.roles(), methodName);
    }
}
