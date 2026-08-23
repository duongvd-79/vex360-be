package com.example.vex360.features.hall.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vex360.features.hall.entities.ExhibitionHall;
import com.example.vex360.features.user.entities.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizerHallPreviewService {
    private final ExhibitionHallService hallService;
    private final HallReviewSnapshotFactory snapshotFactory;

    @Transactional(readOnly = true)
    public HallReviewSnapshot getPreview(User organizer, UUID exhibitionUuid) {
        ExhibitionHall hall = hallService.findOwnedHallEntity(organizer, exhibitionUuid);
        return snapshotFactory.create(hall);
    }
}
