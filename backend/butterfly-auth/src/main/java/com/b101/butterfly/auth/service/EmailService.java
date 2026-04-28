package com.b101.butterfly.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    @Value("${mail.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationCode(@NonNull String to, @NonNull String code) {
        String subject = "[Butterfly] 이메일 인증 코드";
        String body = "<h2>이메일 인증 코드</h2>"
                + "<p>아래 코드를 입력하여 이메일을 인증하세요.</p>"
                + "<h1 style='letter-spacing:4px'>" + code + "</h1>"
                + "<p>코드는 5분간 유효합니다.</p>";
        send(to, subject, body);
    }

    @Async
    public void sendOwnerInvite(@NonNull String to, @NonNull String token,
            @NonNull String companyAuthNo) {
        String link = baseUrl + "/auth/owner-setup?token=" + token;
        String subject = "[Butterfly] 계정 설정 링크";
        String body = "<h2>Butterfly CRM 계정 설정</h2>"
                + "<p>아래 버튼을 클릭하여 비밀번호를 설정하고 서비스를 시작하세요.</p>"
                + "<a href='" + link + "' style='padding:12px 24px;background:#4F46E5;color:#fff;"
                + "text-decoration:none;border-radius:6px'>계정 설정하기</a>"
                + "<p>링크는 24시간 후 만료됩니다.</p>"
                + "<hr>"
                + "<p><strong>회사 인증번호:</strong> " + companyAuthNo + "</p>"
                + "<p>직원들이 서비스에 가입할 때 이 인증번호를 사용합니다.</p>";
        send(to, subject, body);
    }

    private void send(@NonNull String to, @NonNull String subject, @NonNull String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("메일 발송 실패: " + to, e);
        }
    }
}
