CREATE TABLE chat_rooms (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    exhibition_id        INT          NOT NULL,
    exhibitor_user_id    BINARY(16)   NOT NULL,
    visitor_user_id      BINARY(16)   NOT NULL,
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_message_at      TIMESTAMP    NULL,
    last_message_preview VARCHAR(255) NULL,

    CONSTRAINT fk_chatroom_exhibition  FOREIGN KEY (exhibition_id)     REFERENCES exhibitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_chatroom_exhibitor   FOREIGN KEY (exhibitor_user_id) REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT fk_chatroom_visitor     FOREIGN KEY (visitor_user_id)   REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT uq_chatroom             UNIQUE (exhibition_id, exhibitor_user_id, visitor_user_id)
);

CREATE TABLE chat_messages (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    room_id     BINARY(16)   NOT NULL,
    sender_id   BINARY(16)   NOT NULL,
    sender_role VARCHAR(20)  NOT NULL,
    content     TEXT         NOT NULL,
    sent_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    read_at     TIMESTAMP    NULL,

    CONSTRAINT fk_message_room   FOREIGN KEY (room_id)   REFERENCES chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id)      ON DELETE CASCADE
);
