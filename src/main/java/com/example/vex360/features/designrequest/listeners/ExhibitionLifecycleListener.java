package com.example.vex360.features.designrequest.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.vex360.features.designrequest.services.DesignRequestLifecycleService;
import com.example.vex360.features.exhibition.events.ExhibitionActivatedEvent;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;

import lombok.RequiredArgsConstructor;

@Component("designRequestExhibitionLifecycleListener")
@RequiredArgsConstructor
public class ExhibitionLifecycleListener {

    private final DesignRequestLifecycleService lifecycleService;

    @EventListener
    public void onActivated(ExhibitionActivatedEvent event) {
        lifecycleService.handleExhibitionLifecycleTransition(event.getExhibition().getId());
    }

    @EventListener
    public void onCompleted(ExhibitionCompletedEvent event) {
        lifecycleService.handleExhibitionLifecycleTransition(event.getExhibition().getId());
    }
}
