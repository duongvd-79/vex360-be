package com.example.vex360.features.mail;

interface EmailTransport {

    void send(String toEmail, String subject, String htmlContent);
}
