package com.example.vex360.features.exhibition.events;

import com.example.vex360.features.exhibition.entities.ExhibitorRegistration;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ExhibitorRegistrationApprovedEvent extends ApplicationEvent {
    private final ExhibitorRegistration registration;

    public ExhibitorRegistrationApprovedEvent(Object source, ExhibitorRegistration registration) {
        super(source);
        this.registration = registration;
    }
}
