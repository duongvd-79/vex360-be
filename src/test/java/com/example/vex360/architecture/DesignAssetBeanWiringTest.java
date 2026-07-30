package com.example.vex360.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.vex360.features.booth.repositories.BoothRepository;
import com.example.vex360.features.booth.repositories.HotspotRepository;
import com.example.vex360.features.booth.repositories.MediaAssetRepository;
import com.example.vex360.features.booth.repositories.PanoramaRepository;
import com.example.vex360.features.booth.services.BoothDesignService;
import com.example.vex360.features.booth.services.ExhibitorMediaAssetService;
import com.example.vex360.features.booth.services.PanoramaImageCleanupService;
import com.example.vex360.features.designrequest.repositories.DesignDraftPanoramaRepository;
import com.example.vex360.features.designrequest.repositories.DesignDraftRepository;
import com.example.vex360.features.designrequest.repositories.DesignRequestMediaAssetRepository;
import com.example.vex360.features.designrequest.services.DesignAssetReferenceService;
import com.example.vex360.shared.services.CloudService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DesignAssetBeanWiringTest {

    @Test
    void designAssetCleanupBeansStartAndHandleCleanupWithoutCircularDependency() {
        new ApplicationContextRunner()
                .withBean(BoothRepository.class, () -> mock(BoothRepository.class))
                .withBean(PanoramaRepository.class, () -> mock(PanoramaRepository.class))
                .withBean(HotspotRepository.class, () -> mock(HotspotRepository.class))
                .withBean(MediaAssetRepository.class, () -> mock(MediaAssetRepository.class))
                .withBean(DesignDraftPanoramaRepository.class, () -> mock(DesignDraftPanoramaRepository.class))
                .withBean(DesignDraftRepository.class, () -> mock(DesignDraftRepository.class))
                .withBean(CloudService.class, () -> mock(CloudService.class))
                .withBean(ExhibitorMediaAssetService.class, () -> mock(ExhibitorMediaAssetService.class))
                .withBean(DesignRequestMediaAssetRepository.class,
                        () -> mock(DesignRequestMediaAssetRepository.class))
                .withBean(PanoramaImageCleanupService.class)
                .withBean(BoothDesignService.class)
                .withBean(DesignAssetReferenceService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    context.getBean(PanoramaImageCleanupService.class)
                            .scheduleCleanup("unused-image");

                    verify(context.getBean(CloudService.class))
                            .delete("unused-image", "image");
                });
    }
}
