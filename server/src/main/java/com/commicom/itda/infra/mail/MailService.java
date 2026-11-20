package com.commicom.itda.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * 메일 발송 (SMTP). .env 의 MAIL_HOST·MAIL_USERNAME·MAIL_PASSWORD 가 있으면 실제로 보내고,
 * MAIL_HOST 가 비어 있으면(로컬 개발) 보내지 않고 로그로만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${email-verification.from:}")
    private String from;

    /** 받은 메일함에 보이는 보내는 사람 이름 (예: 잇츠). 비우면 주소만 */
    @Value("${email-verification.from-name:}")
    private String fromName;

    /** 메일 서버가 설정돼 있는지 (아니면 개발 모드: 로그만) */
    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }

    public void send(String to, String subject, String text) {
        if (!isConfigured()) {
            log.info("[메일 미설정 — 실제 발송 안 함] to={} subject={}\n{}", to, subject, text);
            return;
        }
        JavaMailSender sender = mailSender.getObject();
        MimeMessage message = sender.createMimeMessage();
        try {
            // UTF-8: 한글 제목·본문·보내는 사람 이름이 깨지지 않게
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (from != null && !from.isBlank()) {
                if (fromName != null && !fromName.isBlank()) {
                    helper.setFrom(from, fromName);
                } else {
                    helper.setFrom(from);
                }
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException(e);
        }
        sender.send(message);
    }
}
