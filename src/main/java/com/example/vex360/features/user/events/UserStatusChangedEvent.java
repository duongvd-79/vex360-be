package com.example.vex360.features.user.events;

import com.example.vex360.features.user.entities.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserStatusChangedEvent extends ApplicationEvent {
    private final User user;

    public UserStatusChangedEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
