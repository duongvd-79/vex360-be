package com.example.vex360.features.designrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.vex360.features.designrequest.controllers.AdminDesignRequestController;
import com.example.vex360.features.designrequest.controllers.DesignerDesignRequestController;
import com.example.vex360.features.designrequest.controllers.ExhibitorDesignRequestController;

class DesignRequestControllerSecurityTest {
    @Test
    void exhibitorControllerRequiresExhibitorRole() {
        assertControllerSecurity(
                ExhibitorDesignRequestController.class,
                "/api/v1/exhibitor/design-requests",
                "hasAuthority('EXHIBITOR')");
    }

    @Test
    void adminControllerRequiresAdminRole() {
        assertControllerSecurity(
                AdminDesignRequestController.class,
                "/api/v1/admin/design-requests",
                "hasAuthority('ADMIN')");
    }

    @Test
    void designerControllerRequiresDesignerRole() {
        assertControllerSecurity(
                DesignerDesignRequestController.class,
                "/api/v1/designer/design-requests",
                "hasAuthority('DESIGNER')");
    }

    private void assertControllerSecurity(
            Class<?> controllerClass,
            String expectedPath,
            String expectedAuthority) {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        PreAuthorize preAuthorize = controllerClass.getAnnotation(PreAuthorize.class);

        assertEquals(expectedPath, requestMapping.value()[0]);
        assertEquals(expectedAuthority, preAuthorize.value());
    }
}
