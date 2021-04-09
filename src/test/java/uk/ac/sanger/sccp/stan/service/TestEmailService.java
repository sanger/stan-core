package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import uk.ac.sanger.sccp.stan.config.MailConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link EmailService}
 * @author dr6
 */
public class TestEmailService {
    @Mock
    JavaMailSender mockMailSender;
    @Mock
    MailConfig mockMailConfig;

    EmailService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        service = spy(new EmailService(mockMailSender, mockMailConfig));
    }

    @Test
    public void testSend() {
        String subject = "Subject alpha";
        String text = "Text pattern beta";
        String sender = "no-reply@sanger.ac.uk";
        when(mockMailConfig.getSender()).thenReturn(sender);

        String[] recipients = {"alabama@nowhere.com", "alaska@nowhere.com"};

        service.send(subject, text, recipients);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setText(text);
        message.setSubject(subject);
        message.setTo(recipients);
        message.setFrom(sender);

        verify(mockMailSender).send(message);
    }

    @Test
    public void testGetServiceDescription() {
        String desc = "Stan test";
        when(mockMailConfig.getServiceDescription()).thenReturn(desc);
        assertEquals(desc, service.getServiceDescription());
    }

    @ParameterizedTest
    @CsvSource(value={
            "false, recipients@sanger.ac.uk, true",
            "true, recipients@sanger.ac.uk, false",
            "false, , false",
    })
    public void testTryAndSendAlert(boolean throwError, String recipient, boolean expectedResult) {
        String[] alertRecipients = (recipient==null ? new String[0] : new String[] { recipient});
        when(mockMailConfig.getAlertRecipients()).thenReturn(alertRecipients);
        boolean shouldAttempt = (alertRecipients.length > 0);
        String subject = "Subject alpha";
        String text = "Text pattern beta";
        if (shouldAttempt) {
            if (throwError) {
                doThrow(MailSendException.class).when(service).send(any(), any(), any());
            } else {
                doNothing().when(service).send(any(), any(), any());
            }
        }

        boolean result = service.tryAndSendAlert(subject, text);
        assertEquals(expectedResult, result);
        if (shouldAttempt) {
            verify(service).send(subject, text, alertRecipients);
        }
    }
}
