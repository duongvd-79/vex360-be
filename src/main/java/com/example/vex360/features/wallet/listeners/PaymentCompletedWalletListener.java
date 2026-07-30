package com.example.vex360.features.wallet.listeners;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.events.ExhibitionPaymentCompletedEvent;
import com.example.vex360.features.wallet.services.PaymentRevenueRecognitionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedWalletListener {

    private final PaymentRevenueRecognitionService paymentRevenueRecognitionService;

    @EventListener
    public void handlePaymentCompleted(ExhibitionPaymentCompletedEvent event) {
        if (event != null && event.getPayment() != null) {
            log.info("[Wallet Listener] Handling ExhibitionPaymentCompletedEvent for payment ID {}", event.getPayment().getId());
            paymentRevenueRecognitionService.recognizeRevenueForPayment(event.getPayment());
        }
    }
}
