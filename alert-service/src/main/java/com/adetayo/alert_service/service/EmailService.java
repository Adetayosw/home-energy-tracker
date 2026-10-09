package com.adetayo.alert_service.service;

import com.adetayo.alert_service.entity.Alert;
import com.adetayo.alert_service.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    private final AlertRepository  alertRepository;

    public EmailService(JavaMailSender mailSender,  AlertRepository alertRepository) {
        this.mailSender = mailSender;
        this.alertRepository = alertRepository;
    }

    public void sendEmail(String to, String subject, String content, Long userId) {
        log.info("Sending email to {}, subject {}, content {}", to, subject, content);


        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setFrom("noreply@adetayo.com");
        message.setSubject(subject);
        message.setText(content);

        try{
            mailSender.send(message);

            final Alert alert = Alert.builder()
                    .userId(userId)
                    .sent(true)
                    .createdAt(LocalDateTime.now())
                    .build();

    alertRepository.saveAndFlush(alert);

        }catch(MailException e){
            log.error("Failed to send email to {}:", to, e);

            final Alert alert = Alert.builder()
                    .userId(userId)
                    .sent(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            alertRepository.saveAndFlush(alert);

        }
    }
}
