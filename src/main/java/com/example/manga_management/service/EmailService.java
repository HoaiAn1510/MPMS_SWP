package com.example.manga_management.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an OTP verification email to the specified address.
     *
     * @param toEmail the recipient's email address
     * @param otp the one-time password to include in the email body
     */
    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("SANKYUU - Mã xác thực tài khoản");
        message.setText("Mã OTP của bạn là: " + otp
                + ". Mã có hiệu lực trong 5 phút. Không chia sẻ mã này cho ai.");
        mailSender.send(message);
    }
}
