package com.example.vex360.features.chat.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class GetOrCreateRoomRequest {

    @NotNull
    private UUID exhibitionId;

    @NotNull
    private UUID exhibitorUserId;
}
