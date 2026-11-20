package com.commicom.itda.infra.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailServiceTest {

    /** 실제로 보내지 않고 보낼 메일만 잡아 둠 */
    static class CapturingSender extends JavaMailSenderImpl {
        MimeMessage sent;

        @Override
        public void send(MimeMessage message) {
            sent = message;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void 보내는_사람_이름과_한글_제목_본문이_UTF8로_들어간다() throws Exception {
        CapturingSender sender = new CapturingSender();
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sender);
        MailService service = new MailService(provider);
        ReflectionTestUtils.setField(service, "host", "smtp.example.com");
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");
        ReflectionTestUtils.setField(service, "fromName", "잇츠");

        service.send("user@example.com", "[잇츠] 인증번호 123456", "안녕하세요, 잇츠예요\n인증번호  123456");

        MimeMessage m = sender.sent;
        // 실제 발송 때처럼 헤더(Content-Type 의 charset 등)를 채움
        m.saveChanges();
        // 보내는 사람 이름은 메일 헤더에 UTF-8 로 인코딩돼 들어가야 메일함에서 안 깨짐
        assertThat(m.getHeader("From")[0]).containsIgnoringCase("=?UTF-8?");
        InternetAddress from = (InternetAddress) m.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("noreply@example.com");
        assertThat(from.getPersonal()).isEqualTo("잇츠");
        assertThat(m.getSubject()).isEqualTo("[잇츠] 인증번호 123456");
        assertThat((String) m.getContent()).contains("인증번호  123456");
        assertThat(m.getContentType()).containsIgnoringCase("utf-8");
    }

    @Test
    void 메일_서버가_없으면_보내지_않는다() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        MailService service = new MailService(provider);
        ReflectionTestUtils.setField(service, "host", "");

        service.send("user@example.com", "제목", "본문");

        org.mockito.Mockito.verifyNoInteractions(provider);
    }
}
