package com.example.vex360.features.booth.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.vex360.features.booth.services.BoothLifecycleService;
import com.example.vex360.features.exhibition.events.ExhibitionActivatedEvent;
import com.example.vex360.features.exhibition.events.ExhibitionCompletedEvent;

import lombok.RequiredArgsConstructor;

@Component("boothExhibitionLifecycleListener")
@RequiredArgsConstructor
public class ExhibitionLifecycleListener {

    private final BoothLifecycleService boothLifecycleService;

    @EventListener
    public void onActivated(ExhibitionActivatedEvent event) {
        boothLifecycleService.handleExhibitionActivated(event.getExhibition().getId());
    }

    @EventListener
    public void onCompleted(ExhibitionCompletedEvent event) {
        boothLifecycleService.handleExhibitionCompleted(event.getExhibition().getId());
    }
}
