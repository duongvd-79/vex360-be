CREATE TABLE exhibitions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,
    organizer_user_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    estimated_booths INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exhibition_organizer FOREIGN KEY (organizer_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE exhibition_packages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    template_id BINARY(16) NOT NULL,
    exhibition_id INT NOT NULL,
    final_price DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exhibition_package_template FOREIGN KEY (template_id) REFERENCES package_templates(id) ON DELETE CASCADE,
    CONSTRAINT fk_exhibition_package_exhibition FOREIGN KEY (exhibition_id) REFERENCES exhibitions(id) ON DELETE CASCADE,
    CONSTRAINT chk_final_price CHECK (final_price >= 0)
);

CREATE TABLE exhibitor_registrations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    exhibition_package_id INT NOT NULL,
    company_user_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_by_user_id BINARY(16) NULL,
    rejected_reason TEXT NULL,
    CONSTRAINT fk_registration_package FOREIGN KEY (exhibition_package_id) REFERENCES exhibition_packages(id) ON DELETE CASCADE,
    CONSTRAINT fk_registration_company FOREIGN KEY (company_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_registration_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    exhibitor_registration_id INT NOT NULL,
    order_code BIGINT NOT NULL UNIQUE,
    amount DECIMAL(15, 2) NOT NULL,
    system_fee DECIMAL(15, 2) NOT NULL,
    organizer_payout DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'VND',
    payment_provider VARCHAR(50) DEFAULT 'PAYOS',
    payment_reference VARCHAR(255) NULL,
    checkout_url TEXT NULL,
    status VARCHAR(50) NOT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_registration FOREIGN KEY (exhibitor_registration_id) REFERENCES exhibitor_registrations(id) ON DELETE CASCADE
);
