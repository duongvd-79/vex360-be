package com.example.vex360.features.exhibition.events;

import org.springframework.context.ApplicationEvent;
import com.example.vex360.features.exhibition.entities.Payment;
import lombok.Getter;

@Getter
public class ExhibitionPaymentCompletedEvent extends ApplicationEvent {

    private final Payment payment;

    public ExhibitionPaymentCompletedEvent(Object source, Payment payment) {
        super(source);
        this.payment = payment;
    }
}
