package com.example.vex360.features.exhibition.events;

import org.springframework.context.ApplicationEvent;

import com.example.vex360.features.exhibition.entities.Exhibition;

import lombok.Getter;

@Getter
public class ExhibitionActivatedEvent extends ApplicationEvent {
    private final Exhibition exhibition;

    public ExhibitionActivatedEvent(Object source, Exhibition exhibition) {
        super(source);
        this.exhibition = exhibition;
    }
}
