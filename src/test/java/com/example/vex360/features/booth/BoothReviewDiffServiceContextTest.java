package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.vex360.features.booth.services.BoothReviewDiffService;

import tools.jackson.databind.ObjectMapper;

class BoothReviewDiffServiceContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(BoothReviewDiffService.class);

    @Test
    void springJacksonObjectMapperWiresBoothReviewDiffService() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(ObjectMapper.class));
            assertNotNull(context.getBean(BoothReviewDiffService.class));
        });
    }
}
