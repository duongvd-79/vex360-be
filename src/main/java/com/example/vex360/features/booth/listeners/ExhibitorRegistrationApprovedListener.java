package com.example.vex360.features.booth.listeners;

import com.example.vex360.features.booth.services.BoothProvisioningService;
import com.example.vex360.features.exhibition.events.ExhibitorRegistrationApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExhibitorRegistrationApprovedListener {

    private final BoothProvisioningService boothProvisioningService;

    @EventListener
    @Transactional
    public void handleExhibitorRegistrationApproved(ExhibitorRegistrationApprovedEvent event) {
        log.info("Received ExhibitorRegistrationApprovedEvent for registration ID: {}",
                event.getRegistration().getId());
        boothProvisioningService.ensureBoothForApprovedRegistration(event.getRegistration());
    }
}
