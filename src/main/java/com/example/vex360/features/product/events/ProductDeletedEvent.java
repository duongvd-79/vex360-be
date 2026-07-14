package com.example.vex360.features.product.events;

import org.springframework.context.ApplicationEvent;

import com.example.vex360.features.product.entities.Product;

import lombok.Getter;

@Getter
public class ProductDeletedEvent extends ApplicationEvent {

    private final Product product;

    public ProductDeletedEvent(Object source, Product product) {
        super(source);
        this.product = product;
    }
}
