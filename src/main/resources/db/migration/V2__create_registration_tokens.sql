CREATE TABLE registration_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    user_id BINARY(16) NOT NULL,
    CONSTRAINT fk_registration_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
