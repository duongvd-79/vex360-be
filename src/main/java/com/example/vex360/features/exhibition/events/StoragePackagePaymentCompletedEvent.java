package com.example.vex360.features.exhibition.events;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

@Getter
public class StoragePackagePaymentCompletedEvent extends ApplicationEvent {

    private final Integer storagePackageOrderId;

    public StoragePackagePaymentCompletedEvent(Object source, Integer storagePackageOrderId) {
        super(source);
        this.storagePackageOrderId = storagePackageOrderId;
    }
}
