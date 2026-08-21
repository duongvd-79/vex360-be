package com.example.vex360.features.exhibition.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.vex360.features.exhibition.services.ExhibitionLifecycleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExhibitionLifecycleScheduler {

    ExhibitionLifecycleService exhibitionLifecycleService;

    @Scheduled(cron = "${app.exhibition.lifecycle-cron:0 5 0 * * *}", zone = "UTC")
    public void runLifecycleTransitions() {
        log.info("[Lifecycle Job] Running scheduled exhibition lifecycle transitions...");
        try {
            int updated = exhibitionLifecycleService.processLifecycleTransitions();
            log.info("[Lifecycle Job] Finished transitions. {} exhibition(s) updated.", updated);
        } catch (Exception e) {
            log.error("[Lifecycle Job] Error during exhibition lifecycle transitions", e);
        }
    }
}
